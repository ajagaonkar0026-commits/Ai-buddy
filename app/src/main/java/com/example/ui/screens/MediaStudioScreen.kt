package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.media.PhotoAspectRatio
import com.example.media.PhotoFilterPreset
import com.example.media.VideoExportPreset
import com.example.media.VideoVisualStyle
import com.example.ui.JarvisViewModel
import com.example.ui.components.FirebaseAiNoticeBanner
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.CardBorderSlate
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.OnlineEmerald
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaStudioScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Photo, 1 = Video
    val photoState by viewModel.photoState.collectAsStateWithLifecycle()
    val videoState by viewModel.videoState.collectAsStateWithLifecycle()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.loadPhoto(it) }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.addVideoClip(it) }
    }

    var aiPhotoPrompt by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Studio Hero Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(MaterialTheme.shapes.large)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_media_studio_hero_1794904565943),
                contentDescription = "Jarvis Media Studio Banner",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(ObsidianBackground.copy(alpha = 0.25f), ObsidianBackground.copy(alpha = 0.90f))
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            ) {
                Text("AI MEDIA STUDIO", style = MaterialTheme.typography.headlineMedium, color = ArcCyan)
                Text(
                    "Photo grading, thumbnails, Media3 video trimming, Reels & merges",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                modifier = Modifier.testTag("photo_studio_tab")
            ) {
                Text("Photo Editor")
            }
            SegmentedButton(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                modifier = Modifier.testTag("video_studio_tab")
            ) {
                Text("Video Editor")
            }
        }

        if (activeTab == 0) {
            // PHOTO STUDIO
            Button(
                onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                colors = ButtonDefaults.buttonColors(containerColor = ArcCyan, contentColor = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.fillMaxWidth().testTag("pick_photo_button")
            ) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (photoState.sourceBitmap == null) "Select Photo (Photo Picker)" else "Change Photo")
            }

            if (photoState.previewBitmap != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                    border = BorderStroke(1.dp, CardBorderSlate)
                ) {
                    Image(
                        bitmap = photoState.previewBitmap!!.asImageBitmap(),
                        contentDescription = "Edited photo preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                // AI Instruction Bar
                FirebaseAiNoticeBanner()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = aiPhotoPrompt,
                        onValueChange = { aiPhotoPrompt = it },
                        label = { Text("AI edit command (e.g., cinematic, portrait blur, thumbnail)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    FilledTonalButton(
                        onClick = {
                            if (aiPhotoPrompt.isNotBlank()) viewModel.applyAiPhotoInstruction(aiPhotoPrompt)
                        }
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "Apply AI instruction", tint = ArcCyan)
                    }
                }
                photoState.aiStatusMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = AlertAmber)
                }

                // Aspect Ratio & Orientation
                Card(
                    colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                    border = BorderStroke(1.dp, CardBorderSlate)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("CROP & ORIENTATION", style = MaterialTheme.typography.labelMedium, color = ArcCyan)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PhotoAspectRatio.entries.forEach { aspect ->
                                FilterChip(
                                    selected = photoState.config.aspectRatio == aspect,
                                    onClick = { viewModel.updatePhotoConfig { it.copy(aspectRatio = aspect) } },
                                    label = { Text(aspect.label) }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = {
                                viewModel.updatePhotoConfig { it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) }
                            }) {
                                Icon(Icons.Filled.RotateRight, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Rotate 90°")
                            }
                            OutlinedButton(onClick = {
                                viewModel.updatePhotoConfig { it.copy(flipHorizontal = !it.flipHorizontal) }
                            }) {
                                Icon(Icons.Filled.Flip, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Flip")
                            }
                        }
                    }
                }

                // Presets & Color Grading
                Card(
                    colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                    border = BorderStroke(1.dp, CardBorderSlate)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("PRESETS & GRADING", style = MaterialTheme.typography.labelMedium, color = ArcCyan)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PhotoFilterPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = photoState.config.preset == preset,
                                    onClick = { viewModel.updatePhotoConfig { it.copy(preset = preset) } },
                                    label = { Text(preset.label) }
                                )
                            }
                        }
                        Text("Brightness: ${String.format(Locale.US, "%+.2f", photoState.config.brightness)}", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = photoState.config.brightness,
                            onValueChange = { v -> viewModel.updatePhotoConfig { it.copy(brightness = v) } },
                            valueRange = -0.35f..0.35f
                        )
                        Text("Contrast: ${String.format(Locale.US, "%.2fx", photoState.config.contrast)}", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = photoState.config.contrast,
                            onValueChange = { v -> viewModel.updatePhotoConfig { it.copy(contrast = v) } },
                            valueRange = 0.6f..1.6f
                        )
                        Text("Saturation: ${String.format(Locale.US, "%.2fx", photoState.config.saturation)}", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = photoState.config.saturation,
                            onValueChange = { v -> viewModel.updatePhotoConfig { it.copy(saturation = v) } },
                            valueRange = 0f..2.0f
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Portrait Vignette & Depth Focus", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = photoState.config.portraitDepthBlur,
                                onCheckedChange = { checked ->
                                    viewModel.updatePhotoConfig { it.copy(portraitDepthBlur = checked) }
                                }
                            )
                        }
                    }
                }

                // Thumbnail Banner & Caption
                Card(
                    colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                    border = BorderStroke(1.dp, CardBorderSlate)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("THUMBNAIL & CAPTION TEXT", style = MaterialTheme.typography.labelMedium, color = ArcCyan)
                        OutlinedTextField(
                            value = photoState.config.bannerTitle,
                            onValueChange = { txt -> viewModel.updatePhotoConfig { it.copy(bannerTitle = txt) } },
                            label = { Text("Top Thumbnail Headline") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = photoState.config.captionText,
                            onValueChange = { txt -> viewModel.updatePhotoConfig { it.copy(captionText = txt) } },
                            label = { Text("Bottom Social Caption") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                // Export Actions
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.saveEditedPhoto() },
                        colors = ButtonDefaults.buttonColors(containerColor = OnlineEmerald, contentColor = ObsidianBackground),
                        modifier = Modifier.weight(1f).testTag("save_photo_button")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Save")
                    }
                    FilledTonalButton(
                        onClick = { viewModel.shareEditedPhoto() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                    OutlinedButton(onClick = { viewModel.openInExternalPhotoEditor() }) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = "External editor")
                    }
                }
            }
        } else {
            // VIDEO STUDIO (Media3 Transformer)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArcCyan, contentColor = ObsidianBackground),
                    modifier = Modifier.weight(1f).testTag("pick_video_button")
                ) {
                    Icon(Icons.Filled.VideoLibrary, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (videoState.clips.isEmpty()) "Select Video" else "Add Clip to Merge")
                }
                if (videoState.clips.isNotEmpty()) {
                    OutlinedButton(onClick = { viewModel.clearVideoClips() }) {
                        Text("Reset")
                    }
                }
            }

            if (videoState.clips.isNotEmpty()) {
                val primaryClip = videoState.clips.first()
                Card(
                    colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                    border = BorderStroke(1.dp, CardBorderSlate)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("LOADED CLIPS (${videoState.clips.size})", style = MaterialTheme.typography.labelMedium, color = ArcCyan)
                        primaryClip.thumbnail?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Video thumbnail",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(MaterialTheme.shapes.small),
                                contentScale = ContentScale.Crop
                            )
                        }
                        videoState.clips.forEachIndexed { idx, clip ->
                            Text(
                                text = "Clip ${idx + 1}: ${clip.width}x${clip.height} • ${clip.durationMs / 1000}s",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Trim & Speed & Aspect
                Card(
                    colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                    border = BorderStroke(1.dp, CardBorderSlate)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("MEDIA3 TRANSFORMER PIPELINE", style = MaterialTheme.typography.labelMedium, color = ArcCyan)

                        if (primaryClip.durationMs > 1000L && videoState.clips.size == 1) {
                            val totalSec = (primaryClip.durationMs / 1000f).coerceAtLeast(1f)
                            val startSec = videoState.config.startMs / 1000f
                            val endSec = (videoState.config.endMs.takeIf { it > 0L } ?: primaryClip.durationMs) / 1000f
                            Text("Trim Range: ${startSec.toInt()}s – ${endSec.toInt()}s", style = MaterialTheme.typography.bodyMedium)
                            RangeSlider(
                                value = startSec.coerceIn(0f, totalSec)..endSec.coerceIn(0f, totalSec),
                                onValueChange = { range ->
                                    viewModel.updateVideoConfig {
                                        it.copy(
                                            startMs = (range.start * 1000).toLong(),
                                            endMs = (range.endInclusive * 1000).toLong()
                                        )
                                    }
                                },
                                valueRange = 0f..totalSec
                            )
                        }

                        Text("Format Preset (Reels / Shorts / Cinematic)", style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VideoExportPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = videoState.config.preset == preset,
                                    onClick = { viewModel.updateVideoConfig { it.copy(preset = preset) } },
                                    label = { Text(preset.label) }
                                )
                            }
                        }

                        Text("Visual Style", style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VideoVisualStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = videoState.config.visualStyle == style,
                                    onClick = { viewModel.updateVideoConfig { it.copy(visualStyle = style) } },
                                    label = { Text(style.label) }
                                )
                            }
                        }

                        Text("Playback Speed: ${String.format(Locale.US, "%.2fx", videoState.config.speedMultiplier)}", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = videoState.config.speedMultiplier,
                            onValueChange = { s -> viewModel.updateVideoConfig { it.copy(speedMultiplier = s) } },
                            valueRange = 0.5f..2.0f
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Mute Original Audio", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = videoState.config.muteAudio,
                                onCheckedChange = { m -> viewModel.updateVideoConfig { it.copy(muteAudio = m) } }
                            )
                        }

                        OutlinedTextField(
                            value = videoState.config.subtitleText,
                            onValueChange = { s -> viewModel.updateVideoConfig { it.copy(subtitleText = s) } },
                            label = { Text("Burned-in Subtitle / Title Overlay") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                if (videoState.isExporting) {
                    LinearProgressIndicator(
                        progress = { videoState.exportProgress / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = ArcCyan
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Exporting MP4: ${videoState.exportProgress}%", style = MaterialTheme.typography.labelMedium)
                        OutlinedButton(onClick = { viewModel.cancelVideoExport() }) {
                            Text("Cancel")
                        }
                    }
                } else {
                    Button(
                        onClick = { viewModel.exportVideo() },
                        colors = ButtonDefaults.buttonColors(containerColor = ArcCyan, contentColor = ObsidianBackground),
                        modifier = Modifier.fillMaxWidth().testTag("export_video_button")
                    ) {
                        Icon(Icons.Filled.MovieCreation, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export Edited Video (MP4)")
                    }
                }

                videoState.statusMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = OnlineEmerald)
                }

                if (videoState.exportedFile != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { viewModel.shareExportedVideo() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Share Exported Video")
                        }
                        OutlinedButton(onClick = {
                            val uri = videoState.exportedGalleryUri ?: primaryClip.uri
                            viewModel.videoEditor.launchExternalVideoEditor(uri)
                        }) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = "External video editor")
                        }
                    }
                }
            }
        }
    }
}
