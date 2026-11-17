package com.example.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class PhotoFilterPreset(val label: String) {
    ORIGINAL("Original"),
    CINEMATIC("Cinematic Teal"),
    VIVID_HDR("Vivid HDR"),
    PORTRAIT_GLOW("Portrait Glow"),
    NOIR("Noir B&W"),
    CYBER_HUD("Cyber HUD")
}

enum class PhotoAspectRatio(val label: String, val ratio: Float?) {
    FREE("Original", null),
    SQUARE("1:1 Post", 1f),
    PORTRAIT_4_5("4:5 Social", 4f / 5f),
    REEL_9_16("9:16 Story", 9f / 16f),
    LANDSCAPE_16_9("16:9 Thumb", 16f / 9f)
}

data class PhotoEditConfig(
    val aspectRatio: PhotoAspectRatio = PhotoAspectRatio.FREE,
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val brightness: Float = 0f,     // -0.5f .. 0.5f
    val contrast: Float = 1.0f,     // 0.5f .. 1.8f
    val saturation: Float = 1.0f,   // 0.0f .. 2.0f
    val warmth: Float = 0f,         // -0.3f .. 0.3f
    val vignette: Float = 0f,       // 0f .. 1f
    val portraitDepthBlur: Boolean = false,
    val preset: PhotoFilterPreset = PhotoFilterPreset.ORIGINAL,
    val captionText: String = "",
    val bannerTitle: String = ""
)

class PhotoEditorEngine(private val context: Context) {

    suspend fun loadBitmap(uri: Uri, maxDimension: Int = 1600): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
            var sampleSize = 1
            while (opts.outWidth / sampleSize > maxDimension || opts.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun renderPreview(source: Bitmap, config: PhotoEditConfig): Bitmap = withContext(Dispatchers.Default) {
        // 1. Rotate / Flip
        val matrix = Matrix().apply {
            if (config.rotationDegrees != 0) postRotate(config.rotationDegrees.toFloat())
            if (config.flipHorizontal) postScale(-1f, 1f)
        }
        val transformed = if (config.rotationDegrees != 0 || config.flipHorizontal) {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } else {
            source
        }

        // 2. Crop to target aspect ratio
        val cropped = cropToAspect(transformed, config.aspectRatio.ratio)

        // 3. Apply color grading + preset
        val output = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val cm = buildColorMatrix(config)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(cropped, 0f, 0f, paint)
        paint.colorFilter = null

        // 4. Portrait Depth / Radial Vignette
        val effectiveVignette = if (config.portraitDepthBlur) maxOf(config.vignette, 0.45f) else config.vignette
        if (effectiveVignette > 0.01f) {
            val radius = maxOf(output.width, output.height) * 0.72f
            val radial = RadialGradient(
                output.width / 2f,
                output.height / 2f,
                radius,
                intArrayOf(Color.TRANSPARENT, Color.argb((effectiveVignette * 190).toInt().coerceIn(0, 220), 5, 10, 20)),
                floatArrayOf(0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            val vigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = radial }
            canvas.drawRect(0f, 0f, output.width.toFloat(), output.height.toFloat(), vigPaint)
        }

        // 5. Cyber HUD subtle frame accent
        if (config.preset == PhotoFilterPreset.CYBER_HUD) {
            val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(170, 0, 229, 255)
                style = Paint.Style.STROKE
                strokeWidth = maxOf(4f, output.width * 0.005f)
            }
            val inset = output.width * 0.03f
            canvas.drawRect(inset, inset, output.width - inset, output.height - inset, hudPaint)
        }

        // 6. Thumbnail Banner & Caption Overlay
        if (config.bannerTitle.isNotBlank()) {
            drawThumbnailBanner(canvas, output.width, output.height, config.bannerTitle.trim())
        }
        if (config.captionText.isNotBlank()) {
            drawCaptionBar(canvas, output.width, output.height, config.captionText.trim())
        }

        if (transformed !== source && transformed !== cropped) transformed.recycle()
        if (cropped !== source && cropped !== transformed) cropped.recycle()

        output
    }

    suspend fun saveToGallery(bitmap: Bitmap, displayName: String = "Jarvis_Edit_${System.currentTimeMillis()}.jpg"): Result<Uri> =
        withContext(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/JarvisStudio")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext Result.failure(IllegalStateException("Unable to create MediaStore entry."))

                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                Result.success(uri)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun exportTempShareUri(bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, "jarvis_photo_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) {
            null
        }
    }

    fun launchExternalEditor(uri: Uri): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_EDIT).apply {
                setDataAndType(uri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Edit image with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun shareImage(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share edited photo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun cropToAspect(src: Bitmap, targetRatio: Float?): Bitmap {
        if (targetRatio == null) return src
        val w = src.width
        val h = src.height
        val currentRatio = w.toFloat() / h.toFloat()
        if (kotlin.math.abs(currentRatio - targetRatio) < 0.01f) return src
        return if (currentRatio > targetRatio) {
            val newW = (h * targetRatio).toInt().coerceIn(1, w)
            val x = (w - newW) / 2
            Bitmap.createBitmap(src, x, 0, newW, h)
        } else {
            val newH = (w / targetRatio).toInt().coerceIn(1, h)
            val y = (h - newH) / 2
            Bitmap.createBitmap(src, 0, y, w, newH)
        }
    }

    private fun buildColorMatrix(config: PhotoEditConfig): ColorMatrix {
        val result = ColorMatrix()

        // Preset base
        when (config.preset) {
            PhotoFilterPreset.ORIGINAL -> Unit
            PhotoFilterPreset.NOIR -> result.setSaturation(0f)
            PhotoFilterPreset.VIVID_HDR -> {
                result.setSaturation(1.35f)
                val c = 1.18f
                val t = (-0.5f * c + 0.5f) * 255f
                result.postConcat(ColorMatrix(floatArrayOf(
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            PhotoFilterPreset.CINEMATIC -> {
                result.postConcat(ColorMatrix(floatArrayOf(
                    1.08f, 0f, 0f, 0f, 8f,
                    0f, 1.02f, 0f, 0f, 4f,
                    0f, 0.05f, 1.12f, 0f, 10f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            PhotoFilterPreset.PORTRAIT_GLOW -> {
                result.setSaturation(1.12f)
                result.postConcat(ColorMatrix(floatArrayOf(
                    1.06f, 0f, 0f, 0f, 14f,
                    0f, 1.03f, 0f, 0f, 10f,
                    0f, 0f, 0.98f, 0f, 6f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            PhotoFilterPreset.CYBER_HUD -> {
                result.setSaturation(1.2f)
                result.postConcat(ColorMatrix(floatArrayOf(
                    0.85f, 0f, 0f, 0f, -10f,
                    0f, 1.08f, 0f, 0f, 12f,
                    0f, 0.1f, 1.25f, 0f, 22f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
        }

        // Saturation
        if (kotlin.math.abs(config.saturation - 1f) > 0.01f && config.preset != PhotoFilterPreset.NOIR) {
            val sat = ColorMatrix().apply { setSaturation(config.saturation) }
            result.postConcat(sat)
        }

        // Contrast + Brightness + Warmth
        val c = config.contrast
        val b = config.brightness * 255f
        val w = config.warmth * 45f
        val translate = (-0.5f * c + 0.5f) * 255f + b
        result.postConcat(
            ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, translate + w,
                    0f, c, 0f, 0f, translate,
                    0f, 0f, c, 0f, translate - w,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        return result
    }

    private fun drawThumbnailBanner(canvas: Canvas, width: Int, height: Int, title: String) {
        val textSize = (width * 0.068f).coerceIn(28f, 120f)
        val grad = LinearGradient(
            0f, 0f, 0f, height * 0.35f,
            Color.argb(200, 7, 11, 20),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        val bgPaint = Paint().apply { shader = grad }
        canvas.drawRect(0f, 0f, width.toFloat(), height * 0.35f, bgPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(12f, 0f, 4f, Color.BLACK)
        }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 229, 255)
        }
        val pad = width * 0.05f
        canvas.drawRect(pad, pad, pad + width * 0.015f, pad + textSize * 1.05f, accentPaint)
        canvas.drawText(title, pad + width * 0.03f, pad + textSize * 0.92f, textPaint)
    }

    private fun drawCaptionBar(canvas: Canvas, width: Int, height: Int, caption: String) {
        val textSize = (width * 0.042f).coerceIn(20f, 72f)
        val barHeight = textSize * 2.1f
        val top = height - barHeight - (height * 0.04f)
        val rect = RectF(width * 0.06f, top, width * 0.94f, top + barHeight)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 7, 11, 20)
        }
        canvas.drawRoundRect(rect, 24f, 24f, bgPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bounds = Rect()
        textPaint.getTextBounds(caption, 0, caption.length, bounds)
        canvas.drawText(caption, width / 2f, rect.centerY() + bounds.height() / 2f, textPaint)
    }
}
