package com.example.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class DeviceTelemetry(
    val batteryPercent: Int = 0,
    val isCharging: Boolean = false,
    val batteryTempC: Float = 0f,
    val storageUsedGb: Float = 0f,
    val storageTotalGb: Float = 0f,
    val volumePercent: Int = 50,
    val maxVolume: Int = 15,
    val flashlightAvailable: Boolean = false,
    val flashlightOn: Boolean = false,
    val dndEnabled: Boolean = false,
    val dndAccessGranted: Boolean = false,
    val writeSettingsGranted: Boolean = false,
    val wifiEnabled: Boolean = false,
    val bluetoothEnabled: Boolean = false
)

data class PendingSensitiveAction(
    val id: String,
    val title: String,
    val description: String,
    val actionType: SensitiveActionType,
    val target: String,
    val payload: String = ""
)

enum class SensitiveActionType {
    CALL,
    SMS,
    EMAIL,
    DELETE_ITEM
}

data class DeviceActionResult(
    val success: Boolean,
    val message: String,
    val pendingConfirmation: PendingSensitiveAction? = null
)

class DeviceControlManager(private val context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var torchEnabled = false

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    fun readTelemetry(): DeviceTelemetry {
        val batteryStatus = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val tempTenths = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0

        val stat = StatFs(Environment.getDataDirectory().path)
        val totalBytes = stat.totalBytes.toDouble()
        val freeBytes = stat.availableBytes.toDouble()
        val totalGb = (totalBytes / (1024.0 * 1024.0 * 1024.0)).toFloat()
        val usedGb = ((totalBytes - freeBytes) / (1024.0 * 1024.0 * 1024.0)).toFloat()

        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volPercent = (curVol * 100) / maxVol

        val flashAvailable = findFlashCameraId() != null
        val dndGranted = notificationManager.isNotificationPolicyAccessGranted
        val dndActive = notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL

        val wifiMgr = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiOn = try { wifiMgr?.isWifiEnabled == true } catch (_: Exception) { false }

        val btMgr = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val btOn = try { btMgr?.adapter?.isEnabled == true } catch (_: Exception) { false }

        return DeviceTelemetry(
            batteryPercent = batteryPct,
            isCharging = isCharging,
            batteryTempC = tempTenths / 10f,
            storageUsedGb = usedGb,
            storageTotalGb = totalGb,
            volumePercent = volPercent,
            maxVolume = maxVol,
            flashlightAvailable = flashAvailable,
            flashlightOn = torchEnabled,
            dndEnabled = dndActive,
            dndAccessGranted = dndGranted,
            writeSettingsGranted = Settings.System.canWrite(appContext),
            wifiEnabled = wifiOn,
            bluetoothEnabled = btOn
        )
    }

    private fun findFlashCameraId(): String? {
        return try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val lensFacing = chars.get(CameraCharacteristics.LENS_FACING)
                hasFlash && lensFacing == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        }
    }

    fun setFlashlight(enabled: Boolean): DeviceActionResult {
        val camId = findFlashCameraId()
            ?: return DeviceActionResult(false, "No hardware camera flashlight detected on this device.")
        return try {
            cameraManager.setTorchMode(camId, enabled)
            torchEnabled = enabled
            DeviceActionResult(true, if (enabled) "Flashlight turned on." else "Flashlight turned off.")
        } catch (e: Exception) {
            DeviceActionResult(false, "Unable to control flashlight: ${e.localizedMessage ?: "hardware busy"}")
        }
    }

    fun setVolumePercent(percent: Int): DeviceActionResult {
        val clamped = percent.coerceIn(0, 100)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val target = ((clamped / 100f) * maxVol).toInt().coerceIn(0, maxVol)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        return DeviceActionResult(true, "Media volume set to $clamped%.")
    }

    fun adjustVolume(increase: Boolean): DeviceActionResult {
        val direction = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        val pct = readTelemetry().volumePercent
        return DeviceActionResult(true, "Volume ${if (increase) "increased" else "decreased"} to $pct%.")
    }

    fun setBrightness(activity: Activity?, percent: Int): DeviceActionResult {
        val clamped = percent.coerceIn(5, 100)
        val normalized = clamped / 100f
        if (Settings.System.canWrite(appContext)) {
            return try {
                Settings.System.putInt(
                    appContext.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                Settings.System.putInt(
                    appContext.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    (normalized * 255).toInt().coerceIn(10, 255)
                )
                DeviceActionResult(true, "System brightness set to $clamped%.")
            } catch (e: Exception) {
                DeviceActionResult(false, "Failed to update system brightness: ${e.message}")
            }
        }
        activity?.let { act ->
            val lp = act.window.attributes
            lp.screenBrightness = normalized
            act.window.attributes = lp
            return DeviceActionResult(
                true,
                "App brightness set to $clamped%. Grant 'Modify system settings' in Settings to adjust global device brightness."
            )
        }
        return DeviceActionResult(false, "Modify System Settings permission required for global brightness.")
    }

    fun openWriteSettingsScreen() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${appContext.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }

    fun setDoNotDisturb(enabled: Boolean): DeviceActionResult {
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            return DeviceActionResult(
                false,
                "Do Not Disturb permission required. Opened Notification Policy Access settings."
            )
        }
        val filter = if (enabled) {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        } else {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }
        notificationManager.setInterruptionFilter(filter)
        return DeviceActionResult(true, if (enabled) "Do Not Disturb enabled." else "Do Not Disturb disabled.")
    }

    fun openWifiSettings(): DeviceActionResult {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        appContext.startActivity(intent)
        return DeviceActionResult(true, "Opened Wi-Fi settings panel.")
    }

    fun openBluetoothSettings(): DeviceActionResult {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
        return DeviceActionResult(true, "Opened Bluetooth settings.")
    }

    fun dispatchMediaControl(action: String): DeviceActionResult {
        val keyCode = when (action.lowercase()) {
            "next", "skip" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "prev", "back" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "pause", "stop" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        return DeviceActionResult(true, "Sent media command: $action.")
    }

    fun setAlarm(hour: Int, minute: Int, label: String): DeviceActionResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Jarvis Alarm" })
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            appContext.startActivity(intent)
            val formatted = String.format(java.util.Locale.US, "%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))
            DeviceActionResult(true, "Alarm intent opened for $formatted ($label).")
        } catch (e: Exception) {
            DeviceActionResult(false, "No clock app available to set alarms.")
        }
    }

    fun openApplication(keyword: String): DeviceActionResult {
        val clean = keyword.trim().lowercase()
        val explicitIntent: Intent? = when {
            clean.contains("camera") -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            clean.contains("gallery") || clean.contains("photo") ->
                Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            clean.contains("setting") -> Intent(Settings.ACTION_SETTINGS)
            clean.contains("calendar") -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
            clean.contains("calculator") -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR)
            clean.contains("contact") -> Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
            clean.contains("youtube") -> {
                appContext.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                    ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
            }
            clean.contains("chrome") || clean.contains("browser") ->
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            clean.contains("map") -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))
            clean.contains("clock") || clean.contains("alarm") -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            else -> findInstalledAppLaunchIntent(clean)
        }

        if (explicitIntent == null) {
            return DeviceActionResult(false, "Could not find an installed app matching \"$keyword\".")
        }
        return try {
            explicitIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(explicitIntent)
            DeviceActionResult(true, "Opened $keyword.")
        } catch (e: Exception) {
            DeviceActionResult(false, "Unable to launch $keyword: ${e.localizedMessage}")
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun findInstalledAppLaunchIntent(query: String): Intent? {
        val pm = appContext.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(mainIntent, 0)
        val match = apps.firstOrNull {
            it.loadLabel(pm).toString().lowercase().contains(query)
        } ?: return null
        return pm.getLaunchIntentForPackage(match.activityInfo.packageName)
    }

    fun openWebsite(urlOrQuery: String): DeviceActionResult {
        val trimmed = urlOrQuery.trim()
        val uri = if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.contains(".")) {
            val fullUrl = if (trimmed.startsWith("http")) trimmed else "https://$trimmed"
            Uri.parse(fullUrl)
        } else {
            Uri.parse("https://www.google.com/search?q=${Uri.encode(trimmed)}")
        }
        return try {
            appContext.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            DeviceActionResult(true, "Opened web link: $uri")
        } catch (e: Exception) {
            DeviceActionResult(false, "No browser available to open $uri")
        }
    }

    fun executeConfirmedSensitiveAction(action: PendingSensitiveAction): DeviceActionResult {
        return try {
            when (action.actionType) {
                SensitiveActionType.CALL -> {
                    val hasCallPerm = ContextCompat.checkSelfPermission(
                        appContext,
                        Manifest.permission.CALL_PHONE
                    ) == PackageManager.PERMISSION_GRANTED
                    val intent = Intent(
                        if (hasCallPerm) Intent.ACTION_CALL else Intent.ACTION_DIAL,
                        Uri.parse("tel:${Uri.encode(action.target)}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(intent)
                    DeviceActionResult(true, "Initiated call flow for ${action.target}.")
                }
                SensitiveActionType.SMS -> {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(action.target)}")).apply {
                        putExtra("sms_body", action.payload)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(intent)
                    DeviceActionResult(true, "Opened SMS composer for ${action.target}.")
                }
                SensitiveActionType.EMAIL -> {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(action.target)}")).apply {
                        putExtra(Intent.EXTRA_SUBJECT, "Message from Jarvis")
                        putExtra(Intent.EXTRA_TEXT, action.payload)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(intent)
                    DeviceActionResult(true, "Opened email composer for ${action.target}.")
                }
                SensitiveActionType.DELETE_ITEM -> {
                    DeviceActionResult(true, "Confirmed deletion of ${action.target}.")
                }
            }
        } catch (e: Exception) {
            DeviceActionResult(false, "Action failed: ${e.localizedMessage}")
        }
    }

    fun readUpcomingCalendarEvents(limit: Int = 5): List<String> {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val events = mutableListOf<String>()
        val now = System.currentTimeMillis()
        val selection = "${CalendarContract.Events.DTSTART} >= ?"
        val args = arrayOf(now.toString())
        try {
            appContext.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART),
                selection,
                args,
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                while (cursor.moveToNext() && events.size < limit) {
                    val title = cursor.getString(titleIdx) ?: "Untitled Event"
                    val startMs = cursor.getLong(startIdx)
                    val timeStr = android.text.format.DateFormat.format("MMM d, h:mm a", startMs).toString()
                    events.add("$title ($timeStr)")
                }
            }
        } catch (_: Exception) { }
        return events
    }

    fun findContactNumber(nameQuery: String): Pair<String, String>? {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        try {
            appContext.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$nameQuery%"),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0) ?: nameQuery
                    val number = cursor.getString(1) ?: return null
                    return name to number
                }
            }
        } catch (_: Exception) { }
        return null
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchLiveWeatherSummary(): String = withContext(Dispatchers.IO) {
        var lat = 37.7749
        var lon = -122.4194
        var locationNote = "Default Coordinates (Grant coarse location for local weather)"
        val hasLoc = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasLoc) {
            try {
                val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                if (loc != null) {
                    lat = loc.latitude
                    lon = loc.longitude
                    locationNote = String.format(java.util.Locale.US, "Device Location (%.2f, %.2f)", lat, lon)
                }
            } catch (_: Exception) { }
        }

        return@withContext try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,wind_speed_10m,weather_code"
            val req = Request.Builder().url(url).build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    "Weather service returned HTTP ${resp.code}."
                } else {
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val current = json.optJSONObject("current")
                    val temp = current?.optDouble("temperature_2m")
                    val wind = current?.optDouble("wind_speed_10m")
                    if (temp != null) {
                        "Weather ($locationNote): ${temp}°C, wind ${wind ?: 0.0} km/h."
                    } else {
                        "Weather unavailable."
                    }
                }
            }
        } catch (e: Exception) {
            "Weather unavailable offline (${e.localizedMessage ?: "no connection"})."
        }
    }
}
