package com.example.ui

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiRepository
import com.example.data.ai.JarvisOrchestrator
import com.example.data.ai.OpenAiRepository
import com.example.data.ai.OrchestratedOutcome
import com.example.data.ai.PlaceholderGeminiAiRepository
import com.example.data.local.AutomationLogEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.JarvisDatabase
import com.example.data.local.JarvisLocalRepository
import com.example.data.local.NoteEntity
import com.example.data.local.ReminderEntity
import com.example.data.network.ConnectivityObserver
import com.example.data.network.OperatingMode
import com.example.data.preferences.AiRoutingMode
import com.example.data.preferences.AssistantSettings
import com.example.data.preferences.GreetingStyle
import com.example.data.preferences.JarvisPreferencesRepository
import com.example.data.preferences.VoiceGender
import com.example.documents.DocumentGenerator
import com.example.documents.GeneratedDocumentResult
import com.example.documents.ResumeInput
import com.example.media.PhotoAspectRatio
import com.example.media.PhotoEditConfig
import com.example.media.PhotoEditorEngine
import com.example.media.PhotoFilterPreset
import com.example.media.VideoEditConfig
import com.example.media.VideoEditorEngine
import com.example.media.VideoExportPreset
import com.example.media.VideoMetadataInfo
import com.example.media.VideoVisualStyle
import com.example.system.AssistantServiceState
import com.example.system.DeviceControlManager
import com.example.system.DeviceTelemetry
import com.example.system.JarvisAssistantService
import com.example.system.PendingSensitiveAction
import com.example.system.SensitiveActionType
import com.example.system.VoiceController
import com.example.system.VoiceEngineStatus
import com.example.system.VoiceProfileInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class PhotoStudioState(
    val sourceUri: Uri? = null,
    val sourceBitmap: Bitmap? = null,
    val previewBitmap: Bitmap? = null,
    val config: PhotoEditConfig = PhotoEditConfig(),
    val isProcessing: Boolean = false,
    val aiStatusMessage: String? = null,
    val savedUri: Uri? = null
)

data class VideoStudioState(
    val clips: List<VideoMetadataInfo> = emptyList(),
    val config: VideoEditConfig = VideoEditConfig(),
    val isExporting: Boolean = false,
    val exportProgress: Int = 0,
    val exportedFile: File? = null,
    val exportedGalleryUri: Uri? = null,
    val statusMessage: String? = null
)

data class DocumentsState(
    val isGenerating: Boolean = false,
    val lastGenerated: GeneratedDocumentResult? = null,
    val pickedDocSummary: String? = null,
    val pickedDocPreview: Bitmap? = null,
    val statusMessage: String? = null
)

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = JarvisPreferencesRepository(application)
    private val localRepo = JarvisLocalRepository(JarvisDatabase.getInstance(application))
    private val connectivityObserver = ConnectivityObserver(application)
    val voiceController = VoiceController(application)
    val deviceControl = DeviceControlManager(application)
    val photoEditor = PhotoEditorEngine(application)
    val videoEditor = VideoEditorEngine(application)
    val documentGenerator = DocumentGenerator(application)

    private val aiRepository: AiRepository = PlaceholderGeminiAiRepository()
    val openAiRepository = OpenAiRepository()
    private val orchestrator = JarvisOrchestrator(aiRepository, openAiRepository, localRepo, deviceControl)

    val settings: StateFlow<AssistantSettings> = prefsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AssistantSettings())

    val operatingMode: StateFlow<OperatingMode> = connectivityObserver.mode
    val voiceStatus: StateFlow<VoiceEngineStatus> = voiceController.status
    val partialTranscript: StateFlow<String> = voiceController.partialTranscript
    val rmsLevel: StateFlow<Float> = voiceController.rmsLevel
    val availableVoices: StateFlow<List<VoiceProfileInfo>> = voiceController.availableVoices
    val voiceError: StateFlow<String?> = voiceController.lastError

    val notes: StateFlow<List<NoteEntity>> = localRepo.notes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val reminders: StateFlow<List<ReminderEntity>> = localRepo.reminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val chatMessages: StateFlow<List<ChatMessageEntity>> = localRepo.chatMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val automationLogs: StateFlow<List<AutomationLogEntity>> = localRepo.automationLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val serviceRunning: StateFlow<Boolean> = AssistantServiceState.isRunning
    val servicePaused: StateFlow<Boolean> = AssistantServiceState.isPaused

    private val _telemetry = MutableStateFlow(deviceControl.readTelemetry())
    val telemetry: StateFlow<DeviceTelemetry> = _telemetry.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _pendingAction = MutableStateFlow<PendingSensitiveAction?>(null)
    val pendingAction: StateFlow<PendingSensitiveAction?> = _pendingAction.asStateFlow()

    private val _statusFeedback = MutableStateFlow<String?>(null)
    val statusFeedback: StateFlow<String?> = _statusFeedback.asStateFlow()

    private val _navigationRequest = MutableStateFlow<String?>(null)
    val navigationRequest: StateFlow<String?> = _navigationRequest.asStateFlow()

    private val _photoState = MutableStateFlow(PhotoStudioState())
    val photoState: StateFlow<PhotoStudioState> = _photoState.asStateFlow()

    private val _videoState = MutableStateFlow(VideoStudioState())
    val videoState: StateFlow<VideoStudioState> = _videoState.asStateFlow()

    private val _docsState = MutableStateFlow(DocumentsState())
    val docsState: StateFlow<DocumentsState> = _docsState.asStateFlow()

    private var photoRenderJob: Job? = null

    init {
        viewModelScope.launch {
            settings.collect { latest ->
                voiceController.applySettings(latest)
            }
        }
        viewModelScope.launch {
            AssistantServiceState.voiceTriggerCounter.collect { count ->
                if (count > 0) {
                    startVoiceInput(null)
                }
            }
        }
    }

    fun consumeNavigationRequest() {
        _navigationRequest.value = null
    }

    fun clearStatusFeedback() {
        _statusFeedback.value = null
    }

    fun refreshTelemetry() {
        _telemetry.value = deviceControl.readTelemetry()
    }

    fun submitCommand(text: String, activity: Activity? = null) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val currentSettings = settings.value
            val currentMode = connectivityObserver.refresh()
            localRepo.appendChat("user", trimmed, "User")
            _isThinking.value = true
            try {
                when (val outcome = orchestrator.processInput(trimmed, currentSettings, currentMode, activity)) {
                    is OrchestratedOutcome.Reply -> {
                        localRepo.appendChat(
                            role = "assistant",
                            text = outcome.text,
                            providerTag = outcome.providerTag,
                            isPlaceholderAi = outcome.isPlaceholderAi
                        )
                        outcome.pendingConfirmation?.let { _pendingAction.value = it }
                        outcome.navigateToTab?.let { _navigationRequest.value = it }
                        if (currentSettings.autoSpeakReplies) {
                            voiceController.speak(outcome.text)
                        }
                        refreshTelemetry()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                localRepo.appendChat(
                    role = "assistant",
                    text = "Error processing command: ${e.localizedMessage ?: "Unknown error"}",
                    providerTag = "System Error"
                )
            } finally {
                _isThinking.value = false
            }
        }
    }

    fun startVoiceInput(activity: Activity? = null) {
        val preferOffline = operatingMode.value == OperatingMode.OFFLINE ||
            settings.value.aiRoutingMode == AiRoutingMode.OFFLINE_ONLY
        voiceController.startListening(preferOffline = preferOffline) { spoken ->
            submitCommand(spoken, activity)
            if (settings.value.continuousListening) {
                // Re-arm listening after brief delay only when user stops speaking
            }
        }
    }

    fun stopVoiceInput() {
        voiceController.stopListening()
        voiceController.stopSpeaking()
    }

    fun confirmPendingAction() {
        val action = _pendingAction.value ?: return
        _pendingAction.value = null
        viewModelScope.launch {
            if (action.actionType == SensitiveActionType.DELETE_ITEM) {
                val id = action.target.toLongOrNull()
                if (id != null) {
                    localRepo.deleteNote(id)
                    localRepo.deleteReminder(id)
                }
                _statusFeedback.value = "Deleted item."
            } else {
                val res = deviceControl.executeConfirmedSensitiveAction(action)
                _statusFeedback.value = res.message
                localRepo.appendChat("assistant", res.message, "Confirmed Action")
            }
        }
    }

    fun dismissPendingAction() {
        val action = _pendingAction.value
        _pendingAction.value = null
        if (action != null) {
            viewModelScope.launch {
                localRepo.appendChat("assistant", "Cancelled: ${action.title}.", "Security Gate")
            }
        }
    }

    // Device Controls
    fun toggleFlashlight() {
        val target = !_telemetry.value.flashlightOn
        val res = deviceControl.setFlashlight(target)
        _statusFeedback.value = res.message
        refreshTelemetry()
    }

    fun setVolumePercent(percent: Int) {
        deviceControl.setVolumePercent(percent)
        refreshTelemetry()
    }

    fun setBrightness(activity: Activity?, percent: Int) {
        val res = deviceControl.setBrightness(activity, percent)
        _statusFeedback.value = res.message
    }

    fun toggleDnd() {
        val res = deviceControl.setDoNotDisturb(!_telemetry.value.dndEnabled)
        _statusFeedback.value = res.message
        refreshTelemetry()
    }

    fun openWifiPanel() {
        _statusFeedback.value = deviceControl.openWifiSettings().message
    }

    fun openBluetoothPanel() {
        _statusFeedback.value = deviceControl.openBluetoothSettings().message
    }

    fun mediaControl(action: String) {
        _statusFeedback.value = deviceControl.dispatchMediaControl(action).message
    }

    fun launchApp(name: String) {
        _statusFeedback.value = deviceControl.openApplication(name).message
    }

    fun setAlarm(hour: Int, minute: Int, label: String) {
        _statusFeedback.value = deviceControl.setAlarm(hour, minute, label).message
    }

    fun requestCallWithGate(target: String) {
        if (target.isBlank()) return
        val resolved = deviceControl.findContactNumber(target)
        val number = resolved?.second ?: target
        val display = resolved?.first ?: target
        if (settings.value.confirmCalls) {
            _pendingAction.value = PendingSensitiveAction(
                id = "call_${System.currentTimeMillis()}",
                title = "Confirm Phone Call",
                description = "Place call to $display ($number)?",
                actionType = SensitiveActionType.CALL,
                target = number
            )
        } else {
            _statusFeedback.value = deviceControl.executeConfirmedSensitiveAction(
                PendingSensitiveAction("call", "Call", "", SensitiveActionType.CALL, number)
            ).message
        }
    }

    fun requestSmsWithGate(target: String, message: String) {
        if (target.isBlank()) return
        val resolved = deviceControl.findContactNumber(target)
        val number = resolved?.second ?: target
        val display = resolved?.first ?: target
        if (settings.value.confirmMessages) {
            _pendingAction.value = PendingSensitiveAction(
                id = "sms_${System.currentTimeMillis()}",
                title = "Confirm Message Draft",
                description = "Open SMS to $display ($number)?",
                actionType = SensitiveActionType.SMS,
                target = number,
                payload = message
            )
        } else {
            _statusFeedback.value = deviceControl.executeConfirmedSensitiveAction(
                PendingSensitiveAction("sms", "SMS", "", SensitiveActionType.SMS, number, message)
            ).message
        }
    }

    fun setBackgroundNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setBackgroundServiceEnabled(enabled)
            val app = getApplication<Application>()
            if (enabled) {
                JarvisAssistantService.start(app)
                _statusFeedback.value = "${settings.value.assistantName} status bar controls enabled."
            } else {
                JarvisAssistantService.stop(app)
                _statusFeedback.value = "Background notification service stopped."
            }
        }
    }

    // Photo Studio
    fun loadPhoto(uri: Uri) {
        viewModelScope.launch {
            _photoState.value = _photoState.value.copy(isProcessing = true, sourceUri = uri, savedUri = null)
            val bmp = photoEditor.loadBitmap(uri)
            if (bmp != null) {
                val cfg = PhotoEditConfig()
                val preview = photoEditor.renderPreview(bmp, cfg)
                _photoState.value = PhotoStudioState(
                    sourceUri = uri,
                    sourceBitmap = bmp,
                    previewBitmap = preview,
                    config = cfg,
                    isProcessing = false
                )
            } else {
                _photoState.value = _photoState.value.copy(isProcessing = false, aiStatusMessage = "Unable to decode selected image.")
            }
        }
    }

    fun updatePhotoConfig(transform: (PhotoEditConfig) -> PhotoEditConfig) {
        val current = _photoState.value
        val src = current.sourceBitmap ?: return
        val nextCfg = transform(current.config)
        _photoState.value = current.copy(config = nextCfg, savedUri = null)
        photoRenderJob?.cancel()
        photoRenderJob = viewModelScope.launch {
            val rendered = photoEditor.renderPreview(src, nextCfg)
            _photoState.value = _photoState.value.copy(previewBitmap = rendered)
        }
    }

    fun applyAiPhotoInstruction(instruction: String) {
        val lower = instruction.lowercase()
        viewModelScope.launch {
            val aiNote = aiRepository.enhanceMediaBrief(instruction, "photo", settings.value.assistantName)
            updatePhotoConfig { cfg ->
                var updated = cfg
                if (lower.contains("cinematic")) updated = updated.copy(preset = PhotoFilterPreset.CINEMATIC, contrast = 1.18f, vignette = 0.35f)
                if (lower.contains("portrait") || lower.contains("background") || lower.contains("blur")) {
                    updated = updated.copy(preset = PhotoFilterPreset.PORTRAIT_GLOW, portraitDepthBlur = true, vignette = 0.45f)
                }
                if (lower.contains("vivid") || lower.contains("enhance") || lower.contains("professional")) {
                    updated = updated.copy(preset = PhotoFilterPreset.VIVID_HDR, contrast = 1.15f, saturation = 1.2f, brightness = 0.04f)
                }
                if (lower.contains("noir") || lower.contains("black")) {
                    updated = updated.copy(preset = PhotoFilterPreset.NOIR, contrast = 1.25f)
                }
                if (lower.contains("thumbnail")) {
                    updated = updated.copy(aspectRatio = PhotoAspectRatio.LANDSCAPE_16_9, bannerTitle = cfg.bannerTitle.ifBlank { "FEATURED" })
                }
                updated
            }
            _photoState.value = _photoState.value.copy(aiStatusMessage = aiNote.text)
            localRepo.logAutomation("AI Photo Enhance", instruction, "Completed", "1. Interpreted instruction\n2. Rendered on-device preview")
        }
    }

    fun saveEditedPhoto() {
        val bmp = _photoState.value.previewBitmap ?: return
        viewModelScope.launch {
            _photoState.value = _photoState.value.copy(isProcessing = true)
            photoEditor.saveToGallery(bmp).onSuccess { uri ->
                _photoState.value = _photoState.value.copy(isProcessing = false, savedUri = uri, aiStatusMessage = "Saved to Pictures/JarvisStudio.")
            }.onFailure { err ->
                _photoState.value = _photoState.value.copy(isProcessing = false, aiStatusMessage = "Save failed: ${err.localizedMessage}")
            }
        }
    }

    fun shareEditedPhoto() {
        val bmp = _photoState.value.previewBitmap ?: return
        viewModelScope.launch {
            val uri = _photoState.value.savedUri ?: photoEditor.exportTempShareUri(bmp)
            if (uri != null) photoEditor.shareImage(uri)
        }
    }

    fun openInExternalPhotoEditor() {
        val uri = _photoState.value.savedUri ?: _photoState.value.sourceUri ?: return
        if (!photoEditor.launchExternalEditor(uri)) {
            _statusFeedback.value = "No external photo editor app found."
        }
    }

    // Video Studio
    fun addVideoClip(uri: Uri) {
        viewModelScope.launch {
            val info = videoEditor.inspectVideo(uri)
            if (info != null) {
                val updated = _videoState.value.clips + info
                val firstEnd = if (_videoState.value.clips.isEmpty()) info.durationMs else _videoState.value.config.endMs
                _videoState.value = _videoState.value.copy(
                    clips = updated,
                    config = _videoState.value.config.copy(endMs = firstEnd),
                    statusMessage = "Loaded clip (${info.width}x${info.height}, ${info.durationMs / 1000}s)."
                )
            } else {
                _videoState.value = _videoState.value.copy(statusMessage = "Could not inspect selected video.")
            }
        }
    }

    fun clearVideoClips() {
        videoEditor.cancelExport()
        _videoState.value = VideoStudioState()
    }

    fun updateVideoConfig(transform: (VideoEditConfig) -> VideoEditConfig) {
        _videoState.value = _videoState.value.copy(config = transform(_videoState.value.config))
    }

    fun exportVideo() {
        val clips = _videoState.value.clips
        if (clips.isEmpty()) return
        _videoState.value = _videoState.value.copy(isExporting = true, exportProgress = 0, statusMessage = "Transforming video with Media3...")
        videoEditor.exportVideo(
            clipUris = clips.map { it.uri },
            config = _videoState.value.config,
            onProgress = { pct ->
                _videoState.value = _videoState.value.copy(exportProgress = pct)
            },
            onCompleted = { file, galleryUri ->
                _videoState.value = _videoState.value.copy(
                    isExporting = false,
                    exportProgress = 100,
                    exportedFile = file,
                    exportedGalleryUri = galleryUri,
                    statusMessage = "Video exported to Movies/JarvisStudio."
                )
                viewModelScope.launch {
                    localRepo.logAutomation(
                        "Media3 Video Export",
                        "${clips.size} clip(s) • ${_videoState.value.config.preset.label}",
                        "Completed",
                        "1. Configured Media3 effects\n2. Exported MP4 to Movies/JarvisStudio"
                    )
                }
            },
            onError = { msg ->
                _videoState.value = _videoState.value.copy(
                    isExporting = false,
                    statusMessage = "Video export error: $msg"
                )
            }
        )
    }

    fun cancelVideoExport() {
        videoEditor.cancelExport()
        _videoState.value = _videoState.value.copy(isExporting = false, statusMessage = "Video export cancelled.")
    }

    fun shareExportedVideo() {
        val file = _videoState.value.exportedFile ?: return
        videoEditor.shareVideo(file, _videoState.value.exportedGalleryUri)
    }

    // Notes, Reminders & Documents
    fun saveNote(title: String, content: String, category: String = "General") {
        if (title.isBlank() && content.isBlank()) return
        viewModelScope.launch {
            localRepo.saveNote(title.ifBlank { "Note" }, content, category)
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch { localRepo.deleteNote(id) }
    }

    fun addReminder(title: String, due: String, priority: String) {
        if (title.isBlank()) return
        viewModelScope.launch { localRepo.addReminder(title, due.ifBlank { "Today" }, priority) }
    }

    fun toggleReminder(id: Long, completed: Boolean) {
        viewModelScope.launch { localRepo.toggleReminder(id, completed) }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch { localRepo.deleteReminder(id) }
    }

    fun generateResumePdf(input: ResumeInput) {
        viewModelScope.launch {
            _docsState.value = _docsState.value.copy(isGenerating = true, statusMessage = null)
            documentGenerator.generateResumePdf(input).onSuccess { res ->
                _docsState.value = _docsState.value.copy(
                    isGenerating = false,
                    lastGenerated = res,
                    statusMessage = "Resume PDF saved to Downloads/JarvisDocs: ${res.fileName}"
                )
                localRepo.logAutomation("Resume PDF Creation", res.fileName, "Completed", "1. Structured qualifications\n2. Rendered PDF\n3. Saved to Downloads")
            }.onFailure { err ->
                _docsState.value = _docsState.value.copy(isGenerating = false, statusMessage = "Failed: ${err.localizedMessage}")
            }
        }
    }

    fun generateReportDocument(title: String, body: String, format: String) {
        viewModelScope.launch {
            _docsState.value = _docsState.value.copy(isGenerating = true, statusMessage = null)
            val result = when (format) {
                "WORD" -> documentGenerator.generateWordCompatibleDoc(title, body)
                "EXCEL" -> {
                    val rows = listOf(listOf("Section", "Detail")) + body.lines().filter { it.isNotBlank() }.mapIndexed { i, line ->
                        listOf("Row ${i + 1}", line)
                    }
                    documentGenerator.generateExcelCsv(title, rows)
                }
                else -> documentGenerator.generateReportPdf(title, body)
            }
            result.onSuccess { res ->
                _docsState.value = _docsState.value.copy(
                    isGenerating = false,
                    lastGenerated = res,
                    statusMessage = "Generated ${res.fileName} in Downloads/JarvisDocs."
                )
                localRepo.logAutomation("Document Export ($format)", res.fileName, "Completed", "1. Formatted content\n2. Saved file")
            }.onFailure { err ->
                _docsState.value = _docsState.value.copy(isGenerating = false, statusMessage = "Export failed: ${err.localizedMessage}")
            }
        }
    }

    fun inspectPickedDocument(uri: Uri) {
        viewModelScope.launch {
            val (summaryText, bmp) = documentGenerator.inspectPickedDocument(uri)
            val aiSummary = aiRepository.summarizeDocumentText("Picked Document", summaryText, settings.value.assistantName)
            _docsState.value = _docsState.value.copy(
                pickedDocSummary = "${aiSummary.text}\n\nRaw Inspection:\n$summaryText",
                pickedDocPreview = bmp
            )
        }
    }

    fun shareGeneratedDocument() {
        _docsState.value.lastGenerated?.let { documentGenerator.shareDocument(it) }
    }

    fun clearChatHistory() {
        viewModelScope.launch { localRepo.clearChat() }
    }

    fun clearAutomationLogs() {
        viewModelScope.launch { localRepo.clearAutomationLogs() }
    }

    // Settings updates
    fun updateIdentity(name: String, userTitle: String, style: GreetingStyle, wakeWord: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateIdentity(name, userTitle, style, wakeWord)
            _statusFeedback.value = "Assistant identity updated to ${name.trim().ifBlank { "JARVIS" }}."
        }
    }

    fun updateVoiceSettings(
        gender: VoiceGender,
        voiceName: String,
        languageTag: String,
        rate: Float,
        pitch: Float,
        continuous: Boolean,
        autoSpeak: Boolean
    ) {
        viewModelScope.launch {
            prefsRepo.updateVoiceSettings(gender, voiceName, languageTag, rate, pitch, continuous, autoSpeak)
        }
    }

    fun previewVoice(candidate: AssistantSettings) {
        voiceController.previewVoice(candidate)
    }

    fun updateAiSettings(model: String, webSearch: Boolean, routing: AiRoutingMode) {
        viewModelScope.launch {
            prefsRepo.updateAiSettings(model, webSearch, routing)
        }
    }

    fun updateSecuritySettings(confirmCalls: Boolean, confirmMessages: Boolean, confirmDeletions: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateSecuritySettings(confirmCalls, confirmMessages, confirmDeletions)
        }
    }

    fun updateNotificationDetails(show: Boolean) {
        viewModelScope.launch { prefsRepo.setShowNotificationDetails(show) }
    }

    fun updateUserProfile(fullName: String, role: String, email: String, skills: String) {
        viewModelScope.launch {
            prefsRepo.updateUserProfile(fullName, role, email, skills)
            _statusFeedback.value = "Personal profile saved."
        }
    }

    override fun onCleared() {
        voiceController.destroy()
        videoEditor.cancelExport()
        super.onCleared()
    }
}
