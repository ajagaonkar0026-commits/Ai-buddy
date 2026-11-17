package com.example.ui.screens

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.system.JarvisTileService
import com.example.ui.JarvisViewModel
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.CardBorderSlate
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.OnlineEmerald
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceControlScreen(
    viewModel: JarvisViewModel,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val serviceRunning by viewModel.serviceRunning.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = LocalActivity.current

    var volumeSlider by remember(telemetry.volumePercent) { mutableFloatStateOf(telemetry.volumePercent.toFloat()) }
    var brightnessSlider by remember { mutableFloatStateOf(70f) }
    var alarmHour by remember { mutableStateOf("07") }
    var alarmMinute by remember { mutableStateOf("00") }
    var alarmLabel by remember { mutableStateOf("Morning Wakeup") }
    var contactInput by remember { mutableStateOf("") }
    var smsBodyInput by remember { mutableStateOf("") }
    var tileRequestStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshTelemetry()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("DEVICE CONTROL", style = MaterialTheme.typography.headlineMedium, color = ArcCyan)
                Text("Hardware, system controls & background notification bar", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { viewModel.refreshTelemetry() }, modifier = Modifier.testTag("refresh_telemetry_button")) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh telemetry", tint = ArcCyan)
            }
        }

        // Telemetry Row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TelemetryCard(
                icon = Icons.Filled.BatteryChargingFull,
                label = "BATTERY",
                value = "${telemetry.batteryPercent}%",
                sub = if (telemetry.isCharging) "Charging • ${telemetry.batteryTempC}°C" else "${telemetry.batteryTempC}°C",
                modifier = Modifier.weight(1f)
            )
            val freeGb = (telemetry.storageTotalGb - telemetry.storageUsedGb).coerceAtLeast(0f)
            TelemetryCard(
                icon = Icons.Filled.Storage,
                label = "STORAGE",
                value = String.format(Locale.US, "%.1f GB", freeGb),
                sub = String.format(Locale.US, "of %.1f GB free", telemetry.storageTotalGb),
                modifier = Modifier.weight(1f)
            )
        }

        // Notification Status Bar & Quick Settings Tile Card (Replaces floating icon!)
        Card(
            colors = CardDefaults.cardColors(containerColor = MidnightSurface),
            border = BorderStroke(1.dp, ArcCyan.copy(alpha = 0.45f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = ArcCyan)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Notification Status Bar Control", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Floating screen icon is disabled. Control ${settings.assistantName} cleanly from Android notifications & Quick Settings.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = serviceRunning,
                        onCheckedChange = { enabled ->
                            if (enabled && !hasNotificationPermission) {
                                onRequestNotificationPermission()
                            } else {
                                viewModel.setBackgroundNotificationEnabled(enabled)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = ArcCyan, checkedTrackColor = ArcCyan.copy(alpha = 0.35f)),
                        modifier = Modifier.testTag("background_notification_switch")
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    OutlinedButton(
                        onClick = {
                            val sbm = context.getSystemService(StatusBarManager::class.java)
                            sbm?.requestAddTileService(
                                ComponentName(context, JarvisTileService::class.java),
                                settings.assistantName,
                                Icon.createWithResource(context, R.drawable.ic_jarvis_tile),
                                context.mainExecutor
                            ) { resultCode ->
                                tileRequestStatus = when (resultCode) {
                                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "${settings.assistantName} Quick Settings tile added."
                                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "Quick Settings tile is already added."
                                    else -> "Quick Settings tile request status: $resultCode"
                                }
                            }
                        },
                        border = BorderStroke(1.dp, CardBorderSlate),
                        modifier = Modifier.fillMaxWidth().testTag("add_qs_tile_button")
                    ) {
                        Icon(Icons.Filled.ViewModule, contentDescription = null, tint = ArcCyan)
                        Spacer(Modifier.width(8.dp))
                        Text("Add ${settings.assistantName} to Quick Settings")
                    }
                }
                tileRequestStatus?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = OnlineEmerald)
                }
            }
        }

        // Hardware Controls
        Card(
            colors = CardDefaults.cardColors(containerColor = MidnightSurface),
            border = BorderStroke(1.dp, CardBorderSlate)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("HARDWARE & SYSTEM", style = MaterialTheme.typography.labelMedium, color = ArcCyan)

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { viewModel.toggleFlashlight() },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (telemetry.flashlightOn) ArcCyan.copy(alpha = 0.25f) else ElevatedSlate
                        ),
                        modifier = Modifier.testTag("flashlight_button")
                    ) {
                        Icon(Icons.Filled.FlashlightOn, contentDescription = null, tint = if (telemetry.flashlightOn) ArcCyan else MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(6.dp))
                        Text(if (telemetry.flashlightOn) "Flashlight ON" else "Flashlight OFF")
                    }

                    FilledTonalButton(
                        onClick = { viewModel.toggleDnd() },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (telemetry.dndEnabled) AlertAmber.copy(alpha = 0.22f) else ElevatedSlate
                        ),
                        modifier = Modifier.testTag("dnd_button")
                    ) {
                        Icon(Icons.Filled.DoNotDisturbOn, contentDescription = null, tint = if (telemetry.dndEnabled) AlertAmber else MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(6.dp))
                        Text(if (telemetry.dndEnabled) "DND ON" else "DND OFF")
                    }

                    FilledTonalButton(onClick = { viewModel.openWifiPanel() }) {
                        Icon(Icons.Filled.Wifi, contentDescription = null, tint = ArcCyan)
                        Spacer(Modifier.width(6.dp))
                        Text("Wi-Fi Panel")
                    }

                    FilledTonalButton(onClick = { viewModel.openBluetoothPanel() }) {
                        Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = ArcCyan)
                        Spacer(Modifier.width(6.dp))
                        Text("Bluetooth")
                    }
                }

                // Volume Slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = ArcCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("Media Volume: ${volumeSlider.toInt()}%", style = MaterialTheme.typography.bodyMedium)
                }
                Slider(
                    value = volumeSlider,
                    onValueChange = { volumeSlider = it },
                    onValueChangeFinished = { viewModel.setVolumePercent(volumeSlider.toInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = ArcCyan, activeTrackColor = ArcCyan)
                )

                // Brightness Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.BrightnessHigh, contentDescription = null, tint = ArcCyan)
                        Spacer(Modifier.width(8.dp))
                        Text("Brightness: ${brightnessSlider.toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (!telemetry.writeSettingsGranted) {
                        OutlinedButton(onClick = { viewModel.deviceControl.openWriteSettingsScreen() }) {
                            Text("Allow System", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Slider(
                    value = brightnessSlider,
                    onValueChange = { brightnessSlider = it },
                    onValueChangeFinished = { viewModel.setBrightness(activity, brightnessSlider.toInt()) },
                    valueRange = 10f..100f,
                    colors = SliderDefaults.colors(thumbColor = ArcCyan, activeTrackColor = ArcCyan)
                )

                // Media Playback Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.mediaControl("previous") }) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous track")
                    }
                    FilledTonalButton(onClick = { viewModel.mediaControl("play") }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                        Spacer(Modifier.width(4.dp))
                        Text("Play")
                    }
                    FilledTonalButton(onClick = { viewModel.mediaControl("pause") }) {
                        Icon(Icons.Filled.Pause, contentDescription = "Pause")
                        Spacer(Modifier.width(4.dp))
                        Text("Pause")
                    }
                    IconButton(onClick = { viewModel.mediaControl("next") }) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next track")
                    }
                }
            }
        }

        // Quick Apps & Alarm
        Card(
            colors = CardDefaults.cardColors(containerColor = MidnightSurface),
            border = BorderStroke(1.dp, CardBorderSlate)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("LAUNCH APPS & ALARMS", style = MaterialTheme.typography.labelMedium, color = ArcCyan)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Camera", "Gallery", "YouTube", "Calendar", "Browser", "Calculator", "Settings").forEach { appName ->
                        OutlinedButton(
                            onClick = { viewModel.launchApp(appName) },
                            border = BorderStroke(1.dp, CardBorderSlate)
                        ) {
                            if (appName == "Camera") {
                                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp), tint = ArcCyan)
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(appName)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = alarmHour,
                        onValueChange = { if (it.length <= 2) alarmHour = it.filter(Char::isDigit) },
                        label = { Text("Hr") },
                        modifier = Modifier.width(68.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = alarmMinute,
                        onValueChange = { if (it.length <= 2) alarmMinute = it.filter(Char::isDigit) },
                        label = { Text("Min") },
                        modifier = Modifier.width(72.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = alarmLabel,
                        onValueChange = { alarmLabel = it },
                        label = { Text("Label") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Button(
                    onClick = {
                        viewModel.setAlarm(
                            alarmHour.toIntOrNull() ?: 7,
                            alarmMinute.toIntOrNull() ?: 0,
                            alarmLabel
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArcCyan, contentColor = MaterialTheme.colorScheme.onPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Alarm, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Set Android Alarm")
                }
            }
        }

        // Confirmed Communication (Security Gate)
        Card(
            colors = CardDefaults.cardColors(containerColor = MidnightSurface),
            border = BorderStroke(1.dp, CardBorderSlate)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("CONFIRMED CALL & MESSAGE", style = MaterialTheme.typography.labelMedium, color = ArcCyan)
                OutlinedTextField(
                    value = contactInput,
                    onValueChange = { contactInput = it },
                    label = { Text("Contact name or phone number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = smsBodyInput,
                    onValueChange = { smsBodyInput = it },
                    label = { Text("Message draft (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { viewModel.requestCallWithGate(contactInput) },
                        enabled = contactInput.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Call")
                    }
                    FilledTonalButton(
                        onClick = { viewModel.requestSmsWithGate(contactInput, smsBodyInput) },
                        enabled = contactInput.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Sms, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Draft SMS")
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MidnightSurface),
        border = BorderStroke(1.dp, CardBorderSlate)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = label, tint = ArcCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
