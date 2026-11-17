package com.example.system

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.network.ConnectivityObserver
import com.example.data.preferences.JarvisPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object AssistantServiceState {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _listeningState = MutableStateFlow("Standby")
    val listeningState: StateFlow<String> = _listeningState.asStateFlow()

    private val _voiceTriggerCounter = MutableStateFlow(0)
    val voiceTriggerCounter: StateFlow<Int> = _voiceTriggerCounter.asStateFlow()

    fun setRunning(running: Boolean) {
        _isRunning.value = running
    }

    fun setPaused(paused: Boolean) {
        _isPaused.value = paused
    }

    fun setListeningState(state: String) {
        _listeningState.value = state
    }

    fun triggerVoiceActivation() {
        _voiceTriggerCounter.value += 1
    }
}

class JarvisAssistantService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var prefsRepo: JarvisPreferencesRepository
    private lateinit var connectivityObserver: ConnectivityObserver

    override fun onCreate() {
        super.onCreate()
        prefsRepo = JarvisPreferencesRepository(this)
        connectivityObserver = ConnectivityObserver(this)
        createNotificationChannel()
        AssistantServiceState.setRunning(true)

        serviceScope.launch {
            prefsRepo.settingsFlow.collect { updateNotification() }
        }
        serviceScope.launch {
            connectivityObserver.mode.collect { updateNotification() }
        }
        serviceScope.launch {
            AssistantServiceState.isPaused.collect { updateNotification() }
        }
        serviceScope.launch {
            AssistantServiceState.listeningState.collect { updateNotification() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                serviceScope.launch {
                    prefsRepo.setBackgroundServiceEnabled(false)
                }
                AssistantServiceState.setRunning(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PAUSE -> {
                val nextPaused = !AssistantServiceState.isPaused.value
                AssistantServiceState.setPaused(nextPaused)
                AssistantServiceState.setListeningState(if (nextPaused) "Paused" else "Standby")
            }
            ACTION_VOICE_CONVERSATION -> {
                AssistantServiceState.setPaused(false)
                AssistantServiceState.triggerVoiceActivation()
                val launch = Intent(this, MainActivity::class.java).apply {
                    action = ACTION_VOICE_CONVERSATION
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(launch)
            }
        }

        val notification = buildNotificationSync(
            assistantName = "JARVIS",
            onlineStatus = connectivityObserver.mode.value.label,
            listeningStatus = if (AssistantServiceState.isPaused.value) "Paused" else AssistantServiceState.listeningState.value,
            showDetails = true
        )

        val hasMicPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (hasMicPermission) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
        } else {
            0
        }

        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
        serviceScope.launch { updateNotification() }
        return START_STICKY
    }

    override fun onDestroy() {
        AssistantServiceState.setRunning(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun updateNotification() {
        if (!AssistantServiceState.isRunning.value) return
        val settings = prefsRepo.settingsFlow.first()
        val mode = connectivityObserver.mode.value.label
        val listening = if (AssistantServiceState.isPaused.value) {
            "Paused"
        } else {
            AssistantServiceState.listeningState.value
        }
        val notification = buildNotificationSync(
            assistantName = settings.assistantName,
            onlineStatus = mode,
            listeningStatus = listening,
            showDetails = settings.showNotificationStatusDetails
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotificationSync(
        assistantName: String,
        onlineStatus: String,
        listeningStatus: String,
        showDetails: Boolean
    ): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            100,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val activateIntent = PendingIntent.getActivity(
            this,
            101,
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_VOICE_CONVERSATION
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val settingsIntent = PendingIntent.getActivity(
            this,
            102,
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_OPEN_SETTINGS
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = PendingIntent.getService(
            this,
            103,
            Intent(this, JarvisAssistantService::class.java).apply { action = ACTION_TOGGLE_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            104,
            Intent(this, JarvisAssistantService::class.java).apply { action = ACTION_STOP_SERVICE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isPaused = AssistantServiceState.isPaused.value
        val title = "$assistantName • $onlineStatus"
        val contentText = if (showDetails) {
            "Status: $listeningStatus • Background active"
        } else {
            "Personal Assistant active"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_jarvis_tile)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.action_activate), activateIntent)
            .addAction(0, if (isPaused) getString(R.string.action_resume) else getString(R.string.action_pause), pauseIntent)
            .addAction(0, getString(R.string.action_settings), settingsIntent)
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "jarvis_assistant_status"
        const val NOTIFICATION_ID = 7001
        const val ACTION_VOICE_CONVERSATION = "com.example.action.VOICE_CONVERSATION"
        const val ACTION_OPEN_SETTINGS = "com.example.action.OPEN_SETTINGS"
        const val ACTION_TOGGLE_PAUSE = "com.example.action.TOGGLE_PAUSE"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, JarvisAssistantService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, JarvisAssistantService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
