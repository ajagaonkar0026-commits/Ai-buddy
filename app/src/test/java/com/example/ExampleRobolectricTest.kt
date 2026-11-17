package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.JarvisOrchestrator
import com.example.data.ai.OpenAiRepository
import com.example.data.ai.OrchestratedOutcome
import com.example.data.ai.PlaceholderGeminiAiRepository
import com.example.data.local.JarvisDatabase
import com.example.data.local.JarvisLocalRepository
import com.example.data.network.OperatingMode
import com.example.data.preferences.AssistantSettings
import com.example.system.DeviceControlManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name and no floating overlay permission`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Jarvis", context.getString(R.string.app_name))

        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val permissions = pkgInfo.requestedPermissions?.toList().orEmpty()
        assertFalse(permissions.contains(Manifest.permission.SYSTEM_ALERT_WINDOW))
    }

    @Test
    fun `placeholder gemini repository labels sample output`() = runTest {
        val repo = PlaceholderGeminiAiRepository()
        assertFalse(repo.isLiveGeminiAvailable)
        val reply = repo.generateAssistantReply("Summarize my day", AssistantSettings(assistantName = "FRIDAY"), "Battery 90%")
        assertTrue(reply.isPlaceholder)
        assertTrue(reply.text.contains("Firebase Required"))
        assertTrue(reply.text.contains("FRIDAY"))
    }

    @Test
    fun `orchestrator handles custom name greeting, offline math, and call confirmation`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = JarvisDatabase.getInstance(context)
        val orchestrator = JarvisOrchestrator(
            aiRepository = PlaceholderGeminiAiRepository(),
            openAiRepository = OpenAiRepository(),
            localRepository = JarvisLocalRepository(db),
            deviceControl = DeviceControlManager(context)
        )
        val settings = AssistantSettings(assistantName = "NOVA", userTitle = "Commander", confirmCalls = true)

        val greeting = orchestrator.processInput("Good morning, NOVA", settings, OperatingMode.OFFLINE, null) as OrchestratedOutcome.Reply
        assertTrue(greeting.text.contains("NOVA"))
        assertTrue(greeting.text.contains("Commander"))

        val math = orchestrator.processInput("12 * 8", settings, OperatingMode.OFFLINE, null) as OrchestratedOutcome.Reply
        assertTrue(math.text.contains("96"))

        val call = orchestrator.processInput("call 5551234", settings, OperatingMode.OFFLINE, null) as OrchestratedOutcome.Reply
        assertNotNull(call.pendingConfirmation)
    }
}
