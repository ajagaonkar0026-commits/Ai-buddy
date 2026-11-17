package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_preferences")

enum class GreetingStyle(val label: String) {
    FORMAL("Formal (\"Sir / Ma'am\")"),
    FRIENDLY("Warm & Conversational"),
    TACTICAL("Concise HUD Briefing")
}

enum class VoiceGender(val label: String) {
    MALE("Male AI Voice"),
    FEMALE("Female AI Voice"),
    NEUTRAL("System Default")
}

enum class AiRoutingMode(val label: String) {
    AUTO("Smart Orchestrator"),
    GEMINI_PRIMARY("Gemini Brain"),
    OPENAI_KNOWLEDGE("OpenAI Knowledge"),
    OFFLINE_ONLY("Offline Engine Only")
}

data class AssistantSettings(
    val assistantName: String = "JARVIS",
    val userTitle: String = "Sir",
    val greetingStyle: GreetingStyle = GreetingStyle.FORMAL,
    val wakeWordEnabled: Boolean = true,
    val voiceGender: VoiceGender = VoiceGender.MALE,
    val selectedVoiceName: String = "",
    val voiceLanguageTag: String = "en-US",
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 0.95f,
    val continuousListening: Boolean = false,
    val autoSpeakReplies: Boolean = true,
    val openAiModel: String = "gpt-4.1-mini",
    val openAiWebSearch: Boolean = true,
    val aiRoutingMode: AiRoutingMode = AiRoutingMode.AUTO,
    val confirmCalls: Boolean = true,
    val confirmMessages: Boolean = true,
    val confirmDeletions: Boolean = true,
    val backgroundServiceEnabled: Boolean = false,
    val showNotificationStatusDetails: Boolean = true,
    val profileFullName: String = "",
    val profileRole: String = "",
    val profileEmail: String = "",
    val profileSkills: String = ""
)

class JarvisPreferencesRepository(private val context: Context) {

    private object Keys {
        val ASSISTANT_NAME = stringPreferencesKey("assistant_name")
        val USER_TITLE = stringPreferencesKey("user_title")
        val GREETING_STYLE = stringPreferencesKey("greeting_style")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val VOICE_GENDER = stringPreferencesKey("voice_gender")
        val SELECTED_VOICE_NAME = stringPreferencesKey("selected_voice_name")
        val VOICE_LANGUAGE_TAG = stringPreferencesKey("voice_language_tag")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val SPEECH_PITCH = floatPreferencesKey("speech_pitch")
        val CONTINUOUS_LISTENING = booleanPreferencesKey("continuous_listening")
        val AUTO_SPEAK_REPLIES = booleanPreferencesKey("auto_speak_replies")
        val OPENAI_MODEL = stringPreferencesKey("openai_model")
        val OPENAI_WEB_SEARCH = booleanPreferencesKey("openai_web_search")
        val AI_ROUTING_MODE = stringPreferencesKey("ai_routing_mode")
        val CONFIRM_CALLS = booleanPreferencesKey("confirm_calls")
        val CONFIRM_MESSAGES = booleanPreferencesKey("confirm_messages")
        val CONFIRM_DELETIONS = booleanPreferencesKey("confirm_deletions")
        val BACKGROUND_SERVICE_ENABLED = booleanPreferencesKey("background_service_enabled")
        val SHOW_NOTIFICATION_DETAILS = booleanPreferencesKey("show_notification_details")
        val PROFILE_FULL_NAME = stringPreferencesKey("profile_full_name")
        val PROFILE_ROLE = stringPreferencesKey("profile_role")
        val PROFILE_EMAIL = stringPreferencesKey("profile_email")
        val PROFILE_SKILLS = stringPreferencesKey("profile_skills")
    }

    val settingsFlow: Flow<AssistantSettings> = context.jarvisDataStore.data.map { prefs ->
        AssistantSettings(
            assistantName = prefs[Keys.ASSISTANT_NAME]?.takeIf { it.isNotBlank() } ?: "JARVIS",
            userTitle = prefs[Keys.USER_TITLE]?.takeIf { it.isNotBlank() } ?: "Sir",
            greetingStyle = enumValueOrDefault(prefs[Keys.GREETING_STYLE], GreetingStyle.FORMAL),
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: true,
            voiceGender = enumValueOrDefault(prefs[Keys.VOICE_GENDER], VoiceGender.MALE),
            selectedVoiceName = prefs[Keys.SELECTED_VOICE_NAME] ?: "",
            voiceLanguageTag = prefs[Keys.VOICE_LANGUAGE_TAG] ?: "en-US",
            speechRate = (prefs[Keys.SPEECH_RATE] ?: 1.0f).coerceIn(0.5f, 2.0f),
            speechPitch = (prefs[Keys.SPEECH_PITCH] ?: 0.95f).coerceIn(0.5f, 2.0f),
            continuousListening = prefs[Keys.CONTINUOUS_LISTENING] ?: false,
            autoSpeakReplies = prefs[Keys.AUTO_SPEAK_REPLIES] ?: true,
            openAiModel = prefs[Keys.OPENAI_MODEL] ?: "gpt-4.1-mini",
            openAiWebSearch = prefs[Keys.OPENAI_WEB_SEARCH] ?: true,
            aiRoutingMode = enumValueOrDefault(prefs[Keys.AI_ROUTING_MODE], AiRoutingMode.AUTO),
            confirmCalls = prefs[Keys.CONFIRM_CALLS] ?: true,
            confirmMessages = prefs[Keys.CONFIRM_MESSAGES] ?: true,
            confirmDeletions = prefs[Keys.CONFIRM_DELETIONS] ?: true,
            backgroundServiceEnabled = prefs[Keys.BACKGROUND_SERVICE_ENABLED] ?: false,
            showNotificationStatusDetails = prefs[Keys.SHOW_NOTIFICATION_DETAILS] ?: true,
            profileFullName = prefs[Keys.PROFILE_FULL_NAME] ?: "",
            profileRole = prefs[Keys.PROFILE_ROLE] ?: "",
            profileEmail = prefs[Keys.PROFILE_EMAIL] ?: "",
            profileSkills = prefs[Keys.PROFILE_SKILLS] ?: ""
        )
    }

    suspend fun updateIdentity(name: String, userTitle: String, style: GreetingStyle, wakeWord: Boolean) {
        val cleanName = name.trim().ifBlank { "JARVIS" }.take(24)
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.ASSISTANT_NAME] = cleanName
            prefs[Keys.USER_TITLE] = userTitle.trim().ifBlank { "Sir" }.take(24)
            prefs[Keys.GREETING_STYLE] = style.name
            prefs[Keys.WAKE_WORD_ENABLED] = wakeWord
        }
    }

    suspend fun updateVoiceSettings(
        gender: VoiceGender,
        voiceName: String,
        languageTag: String,
        rate: Float,
        pitch: Float,
        continuous: Boolean,
        autoSpeak: Boolean
    ) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.VOICE_GENDER] = gender.name
            prefs[Keys.SELECTED_VOICE_NAME] = voiceName
            prefs[Keys.VOICE_LANGUAGE_TAG] = languageTag
            prefs[Keys.SPEECH_RATE] = rate.coerceIn(0.5f, 2.0f)
            prefs[Keys.SPEECH_PITCH] = pitch.coerceIn(0.5f, 2.0f)
            prefs[Keys.CONTINUOUS_LISTENING] = continuous
            prefs[Keys.AUTO_SPEAK_REPLIES] = autoSpeak
        }
    }

    suspend fun updateAiSettings(model: String, webSearch: Boolean, routingMode: AiRoutingMode) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.OPENAI_MODEL] = model
            prefs[Keys.OPENAI_WEB_SEARCH] = webSearch
            prefs[Keys.AI_ROUTING_MODE] = routingMode.name
        }
    }

    suspend fun updateSecuritySettings(confirmCalls: Boolean, confirmMessages: Boolean, confirmDeletions: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.CONFIRM_CALLS] = confirmCalls
            prefs[Keys.CONFIRM_MESSAGES] = confirmMessages
            prefs[Keys.CONFIRM_DELETIONS] = confirmDeletions
        }
    }

    suspend fun setBackgroundServiceEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.BACKGROUND_SERVICE_ENABLED] = enabled
        }
    }

    suspend fun setShowNotificationDetails(show: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.SHOW_NOTIFICATION_DETAILS] = show
        }
    }

    suspend fun updateUserProfile(fullName: String, role: String, email: String, skills: String) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.PROFILE_FULL_NAME] = fullName.trim()
            prefs[Keys.PROFILE_ROLE] = role.trim()
            prefs[Keys.PROFILE_EMAIL] = email.trim()
            prefs[Keys.PROFILE_SKILLS] = skills.trim()
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, default: T): T {
        return enumValues<T>().firstOrNull { it.name == raw } ?: default
    }
}
