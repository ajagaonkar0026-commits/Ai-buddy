package com.example.data.ai

import android.app.Activity
import com.example.data.local.JarvisLocalRepository
import com.example.data.network.OperatingMode
import com.example.data.preferences.AiRoutingMode
import com.example.data.preferences.AssistantSettings
import com.example.data.preferences.GreetingStyle
import com.example.system.DeviceActionResult
import com.example.system.DeviceControlManager
import com.example.system.PendingSensitiveAction
import com.example.system.SensitiveActionType
import kotlinx.coroutines.flow.first
import java.util.Locale

sealed interface OrchestratedOutcome {
    data class Reply(
        val text: String,
        val providerTag: String,
        val isPlaceholderAi: Boolean = false,
        val navigateToTab: String? = null,
        val pendingConfirmation: PendingSensitiveAction? = null
    ) : OrchestratedOutcome
}

class JarvisOrchestrator(
    private val aiRepository: AiRepository,
    private val openAiRepository: OpenAiRepository,
    private val localRepository: JarvisLocalRepository,
    private val deviceControl: DeviceControlManager
) {

    suspend fun processInput(
        rawInput: String,
        settings: AssistantSettings,
        operatingMode: OperatingMode,
        activity: Activity?
    ): OrchestratedOutcome {
        val cleaned = stripWakeWord(rawInput.trim(), settings.assistantName)
        val lower = cleaned.lowercase(Locale.US)

        // 1. Greeting / Identity check (works 100% online & offline)
        if (lower.isBlank() || lower.matches(Regex("^(good morning|good afternoon|good evening|hello|hi|hey|who are you|introduce yourself).*"))) {
            val reply = buildPersonalizedGreeting(rawInput, settings)
            return OrchestratedOutcome.Reply(reply, "${settings.assistantName} Core")
        }

        // 2. Local Phone Control & System Commands (works offline & online)
        val deviceResult = tryExecuteDeviceCommand(lower, cleaned, settings, operatingMode, activity)
        if (deviceResult != null) {
            return deviceResult
        }

        // 3. Local Notes & Reminders creation
        if (lower.startsWith("note ") || lower.startsWith("save note") || lower.startsWith("remember ")) {
            val content = cleaned.replaceFirst(Regex("^(save note|note|remember)\\s*:?\\s*", RegexOption.IGNORE_CASE), "").trim()
            if (content.isNotBlank()) {
                localRepository.saveNote("Voice Note", content, "Assistant")
                localRepository.logAutomation(
                    workflowName = "Save Local Note",
                    summary = content.take(80),
                    status = "Completed",
                    stepsDetail = "1. Parsed note intent\n2. Saved to Room local storage"
                )
                return OrchestratedOutcome.Reply(
                    "Saved note to local storage, ${settings.userTitle}: \"$content\"",
                    "Local Notes Engine"
                )
            }
        }

        if (lower.startsWith("remind me") || lower.startsWith("add reminder") || lower.startsWith("add task")) {
            val task = cleaned.replaceFirst(Regex("^(remind me to|remind me|add reminder|add task)\\s*:?\\s*", RegexOption.IGNORE_CASE), "").trim()
            if (task.isNotBlank()) {
                localRepository.addReminder(task, "Today", "Normal")
                localRepository.logAutomation(
                    workflowName = "Create Reminder",
                    summary = task.take(80),
                    status = "Completed",
                    stepsDetail = "1. Extracted reminder text\n2. Persisted to local Reminders"
                )
                return OrchestratedOutcome.Reply(
                    "Reminder added, ${settings.userTitle}: \"$task\"",
                    "Local Reminders"
                )
            }
        }

        // 4. Multi-step Workflows
        if (lower.contains("morning briefing") || lower.contains("daily briefing")) {
            return executeMorningBriefing(settings, operatingMode)
        }

        if (lower.contains("resume") || lower.contains("cv")) {
            localRepository.logAutomation(
                workflowName = "Resume Builder",
                summary = "Routed to Document & Resume Workspace",
                status = "Completed",
                stepsDetail = "1. Detected resume creation request\n2. Pre-loaded profile details\n3. Opened PDF Resume Studio"
            )
            return OrchestratedOutcome.Reply(
                "Opening the Resume & Document Studio, ${settings.userTitle}. Your saved profile details are ready to compile into a formatted PDF.",
                "Task Orchestrator",
                navigateToTab = "tasks"
            )
        }

        if (lower.contains("edit") && (lower.contains("photo") || lower.contains("picture") || lower.contains("image") || lower.contains("background") || lower.contains("thumbnail"))) {
            localRepository.logAutomation(
                workflowName = "Photo Editing Workflow",
                summary = cleaned,
                status = "Completed",
                stepsDetail = "1. Analyzed photo edit prompt\n2. Routed to Media Studio Photo Editor"
            )
            return OrchestratedOutcome.Reply(
                "Opening the Photo Studio, ${settings.userTitle}. Select an image via the Android Photo Picker to apply enhancements, color presets, aspect crops, or captions.",
                "Media Orchestrator",
                navigateToTab = "media"
            )
        }

        if (lower.contains("video") || lower.contains("reel") || lower.contains("short") || lower.contains("cinematic") || lower.contains("subtitle")) {
            localRepository.logAutomation(
                workflowName = "Video Editing Workflow",
                summary = cleaned,
                status = "Completed",
                stepsDetail = "1. Analyzed video edit instruction\n2. Routed to Media3 Transformer Studio"
            )
            return OrchestratedOutcome.Reply(
                "Opening the Video Studio, ${settings.userTitle}. Pick one or more clips to trim, merge, resize for Reels/Shorts, add subtitles, or apply cinematic grading.",
                "Media Orchestrator",
                navigateToTab = "media"
            )
        }

        // 5. Offline Basic Calculations & Offline Guardrails
        val mathAnswer = tryEvaluateSimpleMath(lower)
        if (mathAnswer != null) {
            return OrchestratedOutcome.Reply(
                "The result is $mathAnswer, ${settings.userTitle}.",
                "Offline Math Engine"
            )
        }

        if (operatingMode == OperatingMode.OFFLINE || settings.aiRoutingMode == AiRoutingMode.OFFLINE_ONLY) {
            val notesCount = localRepository.notes.first().size
            val pendingTasks = localRepository.reminders.first().count { !it.isCompleted }
            return OrchestratedOutcome.Reply(
                buildString {
                    appendLine("Operating in Offline Mode (${operatingMode.label}). I cannot access live web or cloud AI knowledge while offline.")
                    appendLine()
                    appendLine("Offline capabilities available right now:")
                    appendLine("• Device controls (flashlight, volume, battery, storage, alarms, app launch)")
                    appendLine("• Local notes ($notesCount saved) & pending reminders ($pendingTasks open)")
                    appendLine("• Basic arithmetic & on-device photo/video/PDF tools")
                }.trim(),
                "Offline Local Engine"
            )
        }

        // 6. Online AI Orchestration: Route intelligently between OpenAI Knowledge & Gemini Brain
        val telemetry = deviceControl.readTelemetry()
        val deviceSummary = "Battery ${telemetry.batteryPercent}%, Storage ${String.format(Locale.US, "%.1f", telemetry.storageTotalGb - telemetry.storageUsedGb)}GB free, Volume ${telemetry.volumePercent}%"

        val isKnowledgeQuery = lower.startsWith("research ") ||
            lower.startsWith("explain ") ||
            lower.startsWith("what is ") ||
            lower.startsWith("how does ") ||
            lower.contains("finance") ||
            lower.contains("history") ||
            lower.contains("write an essay") ||
            lower.contains("summarize") ||
            lower.contains("compare ")

        val preferOpenAi = settings.aiRoutingMode == AiRoutingMode.OPENAI_KNOWLEDGE ||
            (settings.aiRoutingMode == AiRoutingMode.AUTO && isKnowledgeQuery && openAiRepository.isConfigured)

        if (preferOpenAi) {
            val openAiResult = openAiRepository.queryKnowledge(
                prompt = cleaned,
                assistantName = settings.assistantName,
                userTitle = settings.userTitle,
                model = settings.openAiModel,
                enableWebSearch = settings.openAiWebSearch
            )
            openAiResult.onSuccess { reply ->
                return OrchestratedOutcome.Reply(reply.text, reply.providerTag, isPlaceholderAi = false)
            }.onFailure { err ->
                if (settings.aiRoutingMode == AiRoutingMode.OPENAI_KNOWLEDGE) {
                    return OrchestratedOutcome.Reply(
                        "OpenAI Responses API unavailable: ${err.localizedMessage}",
                        "OpenAI Error"
                    )
                }
            }
        }

        // Default primary AI brain: AiRepository (currently PlaceholderGeminiAiRepository since Firebase setup was declined)
        val geminiReply = aiRepository.generateAssistantReply(
            userPrompt = cleaned,
            settings = settings,
            deviceContextSummary = deviceSummary
        )
        return OrchestratedOutcome.Reply(
            text = geminiReply.text,
            providerTag = geminiReply.providerTag,
            isPlaceholderAi = geminiReply.isPlaceholder
        )
    }

    private suspend fun executeMorningBriefing(
        settings: AssistantSettings,
        operatingMode: OperatingMode
    ): OrchestratedOutcome {
        val steps = mutableListOf<String>()
        val events = deviceControl.readUpcomingCalendarEvents()
        steps.add("1. Calendar checked (${events.size} upcoming events)")

        val weather = if (operatingMode == OperatingMode.ONLINE) {
            deviceControl.fetchLiveWeatherSummary().also { steps.add("2. Live weather retrieved") }
        } else {
            "Weather unavailable in Offline Mode.".also { steps.add("2. Weather skipped (offline)") }
        }

        val reminders = localRepository.reminders.first().filter { !it.isCompleted }
        steps.add("3. Reminders reviewed (${reminders.size} pending)")

        val telemetry = deviceControl.readTelemetry()
        steps.add("4. Device telemetry read (Battery ${telemetry.batteryPercent}%)")

        val briefing = buildString {
            appendLine("${buildTimeGreeting()}, ${settings.userTitle}. Here is your ${settings.assistantName} briefing:")
            appendLine()
            appendLine("• $weather")
            appendLine("• Device: Battery ${telemetry.batteryPercent}%${if (telemetry.isCharging) " (Charging)" else ""}, ${String.format(Locale.US, "%.1f", telemetry.storageTotalGb - telemetry.storageUsedGb)} GB free")
            appendLine("• Pending Reminders (${reminders.size}): ${if (reminders.isEmpty()) "All clear" else reminders.take(3).joinToString("; ") { it.title }}")
            appendLine("• Upcoming Calendar: ${if (events.isEmpty()) "None accessible (or grant Calendar permission)" else events.joinToString("; ")}")
            if (operatingMode == OperatingMode.OFFLINE) {
                appendLine()
                appendLine("Note: Live news & weather require an internet connection.")
            }
        }.trim()

        localRepository.logAutomation(
            workflowName = "Morning Briefing",
            summary = "Briefing compiled (${reminders.size} reminders, ${events.size} events)",
            status = "Completed",
            stepsDetail = steps.joinToString("\n")
        )

        return OrchestratedOutcome.Reply(briefing, "${settings.assistantName} Briefing")
    }

    private fun tryExecuteDeviceCommand(
        lower: String,
        original: String,
        settings: AssistantSettings,
        operatingMode: OperatingMode,
        activity: Activity?
    ): OrchestratedOutcome.Reply? {
        val result: DeviceActionResult? = when {
            lower.contains("flashlight") || lower.contains("torch") -> {
                val enable = !lower.contains("off") && !lower.contains("disable")
                deviceControl.setFlashlight(enable)
            }
            lower.contains("volume up") || lower.contains("increase volume") || lower.contains("louder") ->
                deviceControl.adjustVolume(increase = true)
            lower.contains("volume down") || lower.contains("decrease volume") || lower.contains("quieter") ->
                deviceControl.adjustVolume(increase = false)
            lower.contains("mute") ->
                deviceControl.setVolumePercent(0)
            lower.contains("brightness") -> {
                val pct = Regex("\\d+").find(lower)?.value?.toIntOrNull() ?: if (lower.contains("low") || lower.contains("dim")) 25 else 80
                deviceControl.setBrightness(activity, pct)
            }
            lower.contains("do not disturb") || lower.contains("dnd") -> {
                val enable = !lower.contains("off") && !lower.contains("disable")
                deviceControl.setDoNotDisturb(enable)
            }
            lower.contains("wi-fi") || lower.contains("wifi") ->
                deviceControl.openWifiSettings()
            lower.contains("bluetooth") ->
                deviceControl.openBluetoothSettings()
            lower.contains("battery") -> {
                val t = deviceControl.readTelemetry()
                DeviceActionResult(true, "Battery is at ${t.batteryPercent}%${if (t.isCharging) " and charging" else ""} (${t.batteryTempC}°C).")
            }
            lower.contains("storage") || lower.contains("space left") -> {
                val t = deviceControl.readTelemetry()
                val free = (t.storageTotalGb - t.storageUsedGb).coerceAtLeast(0f)
                DeviceActionResult(true, String.format(Locale.US, "Internal storage: %.1f GB free out of %.1f GB total.", free, t.storageTotalGb))
            }
            lower.contains("pause music") || lower.contains("stop music") ->
                deviceControl.dispatchMediaControl("pause")
            lower.contains("play music") || lower.contains("resume music") ->
                deviceControl.dispatchMediaControl("play")
            lower.contains("next track") || lower.contains("next song") ->
                deviceControl.dispatchMediaControl("next")
            lower.contains("previous track") || lower.contains("previous song") ->
                deviceControl.dispatchMediaControl("previous")
            lower.contains("alarm") -> {
                val numbers = Regex("\\d+").findAll(lower).map { it.value.toInt() }.toList()
                var hour = numbers.getOrNull(0) ?: 7
                val minute = numbers.getOrNull(1) ?: 0
                if (lower.contains("pm") && hour in 1..11) hour += 12
                if (lower.contains("am") && hour == 12) hour = 0
                deviceControl.setAlarm(hour, minute, "${settings.assistantName} Alarm")
            }
            lower.startsWith("open ") -> {
                val target = original.substringAfter(" ", "").trim()
                if (target.contains("http") || target.contains(".com") || target.contains(".org")) {
                    if (operatingMode == OperatingMode.OFFLINE) {
                        DeviceActionResult(false, "Cannot open external websites while Offline.")
                    } else {
                        deviceControl.openWebsite(target)
                    }
                } else {
                    deviceControl.openApplication(target)
                }
            }
            lower.startsWith("search ") || lower.startsWith("google ") -> {
                if (operatingMode == OperatingMode.OFFLINE) {
                    DeviceActionResult(false, "Web search requires an active internet connection.")
                } else {
                    val q = original.substringAfter(" ", "").trim()
                    deviceControl.openWebsite(q)
                }
            }
            lower.startsWith("call ") -> {
                val targetRaw = original.substringAfter(" ", "").trim()
                val resolved = deviceControl.findContactNumber(targetRaw)
                val displayName = resolved?.first ?: targetRaw
                val number = resolved?.second ?: targetRaw
                if (settings.confirmCalls) {
                    DeviceActionResult(
                        success = true,
                        message = "Confirmation required before calling $displayName ($number).",
                        pendingConfirmation = PendingSensitiveAction(
                            id = "call_${System.currentTimeMillis()}",
                            title = "Confirm Phone Call",
                            description = "Call $displayName at $number?",
                            actionType = SensitiveActionType.CALL,
                            target = number
                        )
                    )
                } else {
                    deviceControl.executeConfirmedSensitiveAction(
                        PendingSensitiveAction("call", "Call", "", SensitiveActionType.CALL, number)
                    )
                }
            }
            lower.startsWith("text ") || lower.startsWith("message ") || lower.startsWith("sms ") -> {
                val targetRaw = original.substringAfter(" ", "").trim()
                val resolved = deviceControl.findContactNumber(targetRaw)
                val number = resolved?.second ?: targetRaw
                val displayName = resolved?.first ?: targetRaw
                if (settings.confirmMessages) {
                    DeviceActionResult(
                        success = true,
                        message = "Confirmation required before drafting a message to $displayName.",
                        pendingConfirmation = PendingSensitiveAction(
                            id = "sms_${System.currentTimeMillis()}",
                            title = "Confirm SMS Message",
                            description = "Open SMS composer for $displayName ($number)?",
                            actionType = SensitiveActionType.SMS,
                            target = number,
                            payload = ""
                        )
                    )
                } else {
                    deviceControl.executeConfirmedSensitiveAction(
                        PendingSensitiveAction("sms", "SMS", "", SensitiveActionType.SMS, number)
                    )
                }
            }
            else -> null
        }

        return result?.let {
            OrchestratedOutcome.Reply(
                text = it.message,
                providerTag = "Device Control",
                pendingConfirmation = it.pendingConfirmation
            )
        }
    }

    private fun stripWakeWord(input: String, assistantName: String): String {
        val pattern = Regex("^(hey\\s+|ok\\s+|good\\s+morning\\s*,?\\s*)?${Regex.escape(assistantName)}[,:\\s]*", RegexOption.IGNORE_CASE)
        return input.replaceFirst(pattern, "").trim()
    }

    private fun buildPersonalizedGreeting(rawInput: String, settings: AssistantSettings): String {
        val name = settings.assistantName
        val timeGreeting = buildTimeGreeting()
        return when (settings.greetingStyle) {
            GreetingStyle.FORMAL ->
                "$timeGreeting, ${settings.userTitle}. I am $name, online and at your service. How may I assist you today?"
            GreetingStyle.FRIENDLY ->
                "Hey there! $name here — ready whenever you need help with tasks, device controls, research, or media editing."
            GreetingStyle.TACTICAL ->
                "$name Core active. Telemetry nominal. Awaiting your command, ${settings.userTitle}."
        }
    }

    private fun buildTimeGreeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    private fun tryEvaluateSimpleMath(lower: String): String? {
        val sanitized = lower.replace("what is", "").replace("calculate", "").trim()
        val match = Regex("^(-?\\d+(?:\\.\\d+)?)\\s*([+\\-*/x])\\s*(-?\\d+(?:\\.\\d+)?)$").find(sanitized)
            ?: return null
        val a = match.groupValues[1].toDoubleOrNull() ?: return null
        val op = match.groupValues[2]
        val b = match.groupValues[3].toDoubleOrNull() ?: return null
        val res = when (op) {
            "+" -> a + b
            "-" -> a - b
            "*", "x" -> a * b
            "/" -> if (b != 0.0) a / b else return "undefined (division by zero)"
            else -> return null
        }
        return if (res % 1.0 == 0.0) res.toLong().toString() else String.format(Locale.US, "%.4f", res).trimEnd('0').trimEnd('.')
    }
}
