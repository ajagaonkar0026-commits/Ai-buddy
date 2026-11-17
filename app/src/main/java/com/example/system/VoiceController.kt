package com.example.system

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.data.preferences.AssistantSettings
import com.example.data.preferences.VoiceGender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

data class VoiceProfileInfo(
    val id: String,
    val displayName: String,
    val languageTag: String,
    val localeDisplay: String,
    val inferredGender: VoiceGender,
    val isOfflineAvailable: Boolean
)

enum class VoiceEngineStatus {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING
}

class VoiceController(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null

    private val _status = MutableStateFlow(VoiceEngineStatus.IDLE)
    val status: StateFlow<VoiceEngineStatus> = _status.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<VoiceProfileInfo>>(emptyList())
    val availableVoices: StateFlow<List<VoiceProfileInfo>> = _availableVoices.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var currentSettings: AssistantSettings = AssistantSettings()
    private var onFinalTranscriptCallback: ((String) -> Unit)? = null

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context.applicationContext) { initStatus ->
            if (initStatus == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _status.value = VoiceEngineStatus.SPEAKING
                        AssistantServiceState.setListeningState("Speaking")
                    }

                    override fun onDone(utteranceId: String?) {
                        if (_status.value == VoiceEngineStatus.SPEAKING) {
                            _status.value = VoiceEngineStatus.IDLE
                            AssistantServiceState.setListeningState("Standby")
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (_status.value == VoiceEngineStatus.SPEAKING) {
                            _status.value = VoiceEngineStatus.IDLE
                            AssistantServiceState.setListeningState("Standby")
                        }
                    }
                })
                refreshAvailableVoices()
                applySettings(currentSettings)
            }
        }
    }

    fun refreshAvailableVoices() {
        if (!ttsReady) return
        val voices = tts?.voices.orEmpty()
            .filter { !it.isNetworkConnectionRequired || it.locale.language.isNotBlank() }
            .map { voice ->
                VoiceProfileInfo(
                    id = voice.name,
                    displayName = formatVoiceDisplayName(voice),
                    languageTag = voice.locale.toLanguageTag(),
                    localeDisplay = voice.locale.displayName,
                    inferredGender = inferGender(voice),
                    isOfflineAvailable = !voice.isNetworkConnectionRequired
                )
            }
            .sortedWith(compareBy({ it.languageTag }, { it.inferredGender.name }, { it.displayName }))
        _availableVoices.value = voices
    }

    fun applySettings(settings: AssistantSettings) {
        currentSettings = settings
        if (!ttsReady) return
        val engine = tts ?: return

        val targetLocale = Locale.forLanguageTag(settings.voiceLanguageTag)
        engine.language = targetLocale

        val allVoices = engine.voices.orEmpty()
        val matchingVoice = allVoices.firstOrNull { it.name == settings.selectedVoiceName }
            ?: pickBestVoiceForGenderAndLocale(allVoices, settings.voiceGender, targetLocale)

        if (matchingVoice != null) {
            engine.voice = matchingVoice
        }

        val effectivePitch = when {
            settings.selectedVoiceName.isNotBlank() -> settings.speechPitch
            settings.voiceGender == VoiceGender.MALE -> (settings.speechPitch * 0.90f).coerceIn(0.6f, 1.8f)
            settings.voiceGender == VoiceGender.FEMALE -> (settings.speechPitch * 1.12f).coerceIn(0.7f, 2.0f)
            else -> settings.speechPitch
        }
        engine.setSpeechRate(settings.speechRate.coerceIn(0.5f, 2.0f))
        engine.setPitch(effectivePitch)
    }

    fun speak(text: String, interrupt: Boolean = true) {
        if (!ttsReady || text.isBlank()) return
        val cleanText = text
            .replace(Regex("\\[.*?]"), "")
            .replace("**", "")
            .replace("`", "")
            .trim()
        if (cleanText.isBlank()) return

        applySettings(currentSettings)
        val queueMode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(cleanText, queueMode, null, utteranceId)
    }

    fun previewVoice(
        settings: AssistantSettings,
        sampleText: String = "Good day, ${settings.userTitle}. I am ${settings.assistantName}, your personal AI assistant."
    ) {
        applySettings(settings)
        speak(sampleText, interrupt = true)
    }

    fun stopSpeaking() {
        tts?.stop()
        if (_status.value == VoiceEngineStatus.SPEAKING) {
            _status.value = VoiceEngineStatus.IDLE
            AssistantServiceState.setListeningState("Standby")
        }
    }

    fun startListening(
        preferOffline: Boolean,
        onTranscript: (String) -> Unit
    ) {
        _lastError.value = null
        // Allow user interruption if TTS is currently speaking
        stopSpeaking()

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _lastError.value = "Speech recognition service is not available on this device."
            _status.value = VoiceEngineStatus.IDLE
            return
        }

        onFinalTranscriptCallback = onTranscript
        _partialTranscript.value = ""

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _status.value = VoiceEngineStatus.LISTENING
                        AssistantServiceState.setListeningState("Listening...")
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        _rmsLevel.value = normalized
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _status.value = VoiceEngineStatus.PROCESSING
                        _rmsLevel.value = 0f
                    }

                    override fun onError(error: Int) {
                        _rmsLevel.value = 0f
                        _status.value = VoiceEngineStatus.IDLE
                        AssistantServiceState.setListeningState("Standby")
                        _lastError.value = mapSpeechError(error)
                    }

                    override fun onResults(results: Bundle?) {
                        _rmsLevel.value = 0f
                        _status.value = VoiceEngineStatus.IDLE
                        AssistantServiceState.setListeningState("Standby")
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spoken = matches?.firstOrNull()?.trim().orEmpty()
                        _partialTranscript.value = ""
                        if (spoken.isNotBlank()) {
                            onFinalTranscriptCallback?.invoke(spoken)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        if (partial.isNotBlank()) {
                            _partialTranscript.value = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentSettings.voiceLanguageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            if (preferOffline) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

        _status.value = VoiceEngineStatus.LISTENING
        AssistantServiceState.setListeningState("Listening...")
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _rmsLevel.value = 0f
        if (_status.value == VoiceEngineStatus.LISTENING) {
            _status.value = VoiceEngineStatus.IDLE
            AssistantServiceState.setListeningState("Standby")
        }
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun pickBestVoiceForGenderAndLocale(
        voices: Set<Voice>,
        gender: VoiceGender,
        locale: Locale
    ): Voice? {
        val localeMatches = voices.filter {
            it.locale.language.equals(locale.language, ignoreCase = true)
        }
        val pool = localeMatches.ifEmpty { voices.toList() }
        val exactLocalePool = pool.filter {
            it.locale.country.equals(locale.country, ignoreCase = true)
        }.ifEmpty { pool }

        return when (gender) {
            VoiceGender.NEUTRAL -> exactLocalePool.firstOrNull()
            VoiceGender.MALE -> exactLocalePool.firstOrNull { inferGender(it) == VoiceGender.MALE }
                ?: exactLocalePool.firstOrNull()
            VoiceGender.FEMALE -> exactLocalePool.firstOrNull { inferGender(it) == VoiceGender.FEMALE }
                ?: exactLocalePool.firstOrNull()
        }
    }

    private fun inferGender(voice: Voice): VoiceGender {
        val name = voice.name.lowercase(Locale.US)
        val femaleHints = listOf("female", "woman", "-sfg", "-tpf", "-iol", "-iob", "-iom-f", "en-us-x-sfg", "en-gb-x-gba", "en-gb-x-gbc", "en-in-x-ena", "en-in-x-enc", "samantha", "victoria", "karen")
        val maleHints = listOf("male", "man", "-tpd", "-iom", "-iog", "-rjs", "en-us-x-iom", "en-us-x-tpd", "en-gb-x-gbb", "en-gb-x-gbd", "en-gb-x-rjs", "en-in-x-end", "en-in-x-ene", "daniel", "alex", "oliver")
        return when {
            femaleHints.any { name.contains(it) } && !name.contains("female").not() -> VoiceGender.FEMALE
            femaleHints.any { name.contains(it) } -> VoiceGender.FEMALE
            maleHints.any { name.contains(it) } -> VoiceGender.MALE
            else -> VoiceGender.NEUTRAL
        }
    }

    private fun formatVoiceDisplayName(voice: Voice): String {
        val genderTag = when (inferGender(voice)) {
            VoiceGender.MALE -> "Male"
            VoiceGender.FEMALE -> "Female"
            VoiceGender.NEUTRAL -> "Standard"
        }
        val offlineTag = if (!voice.isNetworkConnectionRequired) "Offline" else "Cloud"
        val shortCode = voice.name.substringAfterLast("-x-").take(6).uppercase(Locale.US)
        return "${voice.locale.displayName} • $genderTag ($shortCode • $offlineTag)"
    }

    private fun mapSpeechError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice recognition."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during cloud speech recognition. Offline mode will use local recognition if installed."
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please speak clearly and try again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected before timeout."
        else -> "Voice recognition failed (code $code)."
    }
}
