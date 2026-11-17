package com.example.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.SpannableString
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Contrast
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class VideoExportPreset(val label: String, val ratio: Float?) {
    ORIGINAL("Original", null),
    REEL_9_16("Instagram Reel (9:16)", 9f / 16f),
    SHORTS_9_16("YouTube Short (9:16)", 9f / 16f),
    CINEMATIC_16_9("Cinematic (16:9)", 16f / 9f),
    SQUARE_1_1("Square (1:1)", 1f)
}

enum class VideoVisualStyle(val label: String) {
    STANDARD("Natural"),
    CINEMATIC_CONTRAST("Cinematic High-Contrast"),
    NOIR_MONO("Monochrome Noir")
}

data class VideoMetadataInfo(
    val uri: Uri,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val thumbnail: Bitmap?
)

data class VideoEditConfig(
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val speedMultiplier: Float = 1.0f,
    val muteAudio: Boolean = false,
    val preset: VideoExportPreset = VideoExportPreset.ORIGINAL,
    val visualStyle: VideoVisualStyle = VideoVisualStyle.STANDARD,
    val subtitleText: String = "",
    val targetResolutionHeight: Int = 720
)

@OptIn(UnstableApi::class)
class VideoEditorEngine(private val context: Context) {

    private var activeTransformer: Transformer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    suspend fun inspectVideo(uri: Uri): VideoMetadataInfo? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
            val thumb = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            VideoMetadataInfo(uri, duration, width, height, thumb)
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    @OptIn(UnstableApi::class)
    fun exportVideo(
        clipUris: List<Uri>,
        config: VideoEditConfig,
        onProgress: (Int) -> Unit,
        onCompleted: (File, Uri?) -> Unit,
        onError: (String) -> Unit
    ) {
        if (clipUris.isEmpty()) {
            onError("Select at least one video clip.")
            return
        }

        cancelExport()

        val outputFile = File(context.cacheDir, "jarvis_video_${System.currentTimeMillis()}.mp4")
        val videoEffects = mutableListOf<Effect>()

        when (config.visualStyle) {
            VideoVisualStyle.STANDARD -> Unit
            VideoVisualStyle.CINEMATIC_CONTRAST -> videoEffects.add(Contrast(0.28f))
            VideoVisualStyle.NOIR_MONO -> videoEffects.add(RgbFilter.createGrayscaleFilter())
        }

        config.preset.ratio?.let { ratio ->
            videoEffects.add(Presentation.createForAspectRatio(ratio, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
        }

        if (config.targetResolutionHeight > 0) {
            videoEffects.add(Presentation.createForHeight(config.targetResolutionHeight))
        }

        if (kotlin.math.abs(config.speedMultiplier - 1.0f) > 0.05f) {
            videoEffects.add(SpeedChangeEffect(config.speedMultiplier.coerceIn(0.5f, 2.0f)))
        }

        if (config.subtitleText.isNotBlank()) {
            val textOverlay = TextOverlay.createStaticTextOverlay(SpannableString(config.subtitleText.trim()))
            videoEffects.add(OverlayEffect(listOf(textOverlay)))
        }

        val effects = Effects(emptyList(), videoEffects)

        val editedItems = clipUris.mapIndexed { index, uri ->
            val builder = MediaItem.Builder().setUri(uri)
            if (index == 0 && config.endMs > config.startMs && config.endMs > 0L) {
                builder.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(config.startMs.coerceAtLeast(0L))
                        .setEndPositionMs(config.endMs)
                        .build()
                )
            }
            EditedMediaItem.Builder(builder.build())
                .setRemoveAudio(config.muteAudio)
                .setEffects(effects)
                .build()
        }

        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    stopProgressPolling()
                    val galleryUri = saveVideoToGallery(outputFile)
                    onCompleted(outputFile, galleryUri)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    stopProgressPolling()
                    onError(exportException.localizedMessage ?: "Video export failed.")
                }
            })
            .build()

        activeTransformer = transformer

        if (editedItems.size == 1) {
            transformer.start(editedItems.first(), outputFile.absolutePath)
        } else {
            val sequence = EditedMediaItemSequence.Builder(editedItems).build()
            val composition = Composition.Builder(listOf(sequence)).build()
            transformer.start(composition, outputFile.absolutePath)
        }

        val progressHolder = ProgressHolder()
        progressRunnable = object : Runnable {
            override fun run() {
                val state = transformer.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(progressHolder.progress)
                }
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    mainHandler.postDelayed(this, 300L)
                }
            }
        }
        mainHandler.post(progressRunnable!!)
    }

    fun cancelExport() {
        stopProgressPolling()
        try {
            activeTransformer?.cancel()
        } catch (_: Exception) { }
        activeTransformer = null
    }

    private fun stopProgressPolling() {
        progressRunnable?.let { mainHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun saveVideoToGallery(sourceFile: File): Uri? {
        return try {
            val name = "Jarvis_Video_${System.currentTimeMillis()}.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/JarvisStudio")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { input -> input.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (_: Exception) {
            null
        }
    }

    fun shareVideo(file: File, galleryUri: Uri?) {
        val shareUri = galleryUri ?: FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share edited video").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun launchExternalVideoEditor(uri: Uri): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_EDIT).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Edit video with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }
}
