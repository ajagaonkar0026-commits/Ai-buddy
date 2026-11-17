package com.example.system

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.MainActivity
import com.example.R
import com.example.data.preferences.JarvisPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class JarvisTileService : TileService() {

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val currentlyRunning = AssistantServiceState.isRunning.value
        if (currentlyRunning) {
            AssistantServiceState.triggerVoiceActivation()
            val intent = Intent(this, MainActivity::class.java).apply {
                action = JarvisAssistantService.ACTION_VOICE_CONVERSATION
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    201,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                legacyStartActivityAndCollapse(intent)
            }
        } else {
            JarvisAssistantService.start(this)
            tileScope.launch {
                JarvisPreferencesRepository(applicationContext).setBackgroundServiceEnabled(true)
                refreshTile()
            }
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun legacyStartActivityAndCollapse(intent: Intent) {
        startActivityAndCollapse(intent)
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        tileScope.launch {
            val name = try {
                JarvisPreferencesRepository(applicationContext).settingsFlow.first().assistantName
            } catch (_: Exception) {
                "JARVIS"
            }
            val running = AssistantServiceState.isRunning.value
            tile.label = name
            tile.icon = Icon.createWithResource(applicationContext, R.drawable.ic_jarvis_tile)
            tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (running) AssistantServiceState.listeningState.value else "Tap to Start"
            }
            tile.contentDescription = "$name Assistant Quick Settings Tile"
            tile.updateTile()
        }
    }

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }
}
