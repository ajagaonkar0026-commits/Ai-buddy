package com.example.data.ai

import com.example.data.preferences.AssistantSettings
import kotlinx.coroutines.delay

data class AiReply(
    val text: String,
    val providerTag: String,
    val isPlaceholder: Boolean
)

/**
 * Single AI repository abstraction for the primary Gemini brain.
 * When Firebase AI Logic is enabled, a FirebaseAiRepository implementation can be swapped in here.
 */
interface AiRepository {
    val isLiveGeminiAvailable: Boolean

    suspend fun generateAssistantReply(
        userPrompt: String,
        settings: AssistantSettings,
        deviceContextSummary: String
    ): AiReply

    suspend fun summarizeDocumentText(
        documentTitle: String,
        extractedText: String,
        assistantName: String
    ): AiReply

    suspend fun enhanceMediaBrief(
        instruction: String,
        mediaType: String,
        assistantName: String
    ): AiReply
}

/**
 * Placeholder Gemini implementation used because Firebase setup was declined.
 * Never calls a direct Gemini REST endpoint or embeds an API key.
 */
class PlaceholderGeminiAiRepository : AiRepository {
    override val isLiveGeminiAvailable: Boolean = false

    override suspend fun generateAssistantReply(
        userPrompt: String,
        settings: AssistantSettings,
        deviceContextSummary: String
    ): AiReply {
        delay(250)
        val name = settings.assistantName
        val honorific = when (settings.greetingStyle) {
            com.example.data.preferences.GreetingStyle.FORMAL -> settings.userTitle
            com.example.data.preferences.GreetingStyle.FRIENDLY -> "friend"
            com.example.data.preferences.GreetingStyle.TACTICAL -> "Operator"
        }
        val response = buildString {
            appendLine("[Sample Response — Firebase Required for Live Gemini]")
            appendLine()
            appendLine("$name acknowledging your request, $honorific: \"$userPrompt\"")
            appendLine()
            appendLine("Live Gemini reasoning via Firebase AI Logic is currently inactive because Firebase setup was declined. Local device telemetry is still active ($deviceContextSummary), and you can also configure OPENAI_API_KEY in AI Studio Secrets to route knowledge queries through the OpenAI Responses API.")
        }.trim()
        return AiReply(
            text = response,
            providerTag = "Gemini Brain (Placeholder)",
            isPlaceholder = true
        )
    }

    override suspend fun summarizeDocumentText(
        documentTitle: String,
        extractedText: String,
        assistantName: String
    ): AiReply {
        delay(200)
        val preview = extractedText.trim().take(280).ifBlank { "No selectable text extracted from file." }
        return AiReply(
            text = "[Sample Summary — Firebase Required for Live Gemini]\n\n$assistantName inspected \"$documentTitle\" (${extractedText.length} characters).\n\nPreview:\n$preview",
            providerTag = "Gemini Doc Engine (Placeholder)",
            isPlaceholder = true
        )
    }

    override suspend fun enhanceMediaBrief(
        instruction: String,
        mediaType: String,
        assistantName: String
    ): AiReply {
        delay(150)
        return AiReply(
            text = "[Sample AI Preset — Firebase Required for Live Gemini]\n\n$assistantName mapped \"$instruction\" to on-device $mediaType grading & transformation parameters.",
            providerTag = "Gemini Vision (Placeholder)",
            isPlaceholder = true
        )
    }
}
