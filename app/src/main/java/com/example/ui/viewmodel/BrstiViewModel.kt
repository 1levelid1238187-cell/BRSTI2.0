package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BrstiApplication
import com.example.device.AssistantActionHost
import com.example.device.PendingActionConfirmation
import com.example.permissions.PermissionManager
import com.example.service.BrstiVoiceService
import com.example.ui.state.AssistantMood
import com.example.ui.state.BrstiUiState
import com.example.ui.state.ConnectionStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BrstiNavigationEvent {
    data object OpenMediaVision : BrstiNavigationEvent
    data object RequestMicPermission : BrstiNavigationEvent
    data class RequestPermissionHint(val permissionName: String) : BrstiNavigationEvent
    data object LaunchCameraCapture : BrstiNavigationEvent
}

class BrstiViewModel(application: Application) : AndroidViewModel(application), AssistantActionHost {

    private val container = (application as BrstiApplication).container
    private val liveManager = container.geminiLiveManager
    private val settingsRepo = container.settingsRepository
    private val memoryRepo = container.memoryRepository
    private val visionAnalyzer = container.geminiVisionAnalyzer
    private val actionExecutor = container.deviceActionExecutor

    private val _events = MutableSharedFlow<BrstiNavigationEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<BrstiNavigationEvent> = _events.asSharedFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingActionConfirmation?>(null)
    private val _statusBanner = MutableStateFlow<String?>(null)
    private val _permissions = MutableStateFlow(PermissionManager.captureSnapshot(application))
    private val _selectedBitmap = MutableStateFlow<Bitmap?>(null)
    private val _visionResult = MutableStateFlow<String?>(null)
    private val _isAnalyzingVision = MutableStateFlow(false)
    private val _visionError = MutableStateFlow<String?>(null)

    init {
        actionExecutor.actionHost = this
    }

    private data class CoreLiveSlice(
        val mood: AssistantMood,
        val status: ConnectionStatus,
        val isMic: Boolean,
        val isStandby: Boolean,
        val userLevel: Float,
        val assistantLevel: Float,
        val standbyLevel: Float
    )

    private val coreLiveFlow = combine(
        liveManager.mood,
        liveManager.connectionStatus,
        container.isMicActive,
        container.isWakeStandbyActive,
        liveManager.userAudioLevel,
        liveManager.assistantAudioLevel,
        container.standbyEnergyLevel
    ) { values ->
        CoreLiveSlice(
            mood = values[0] as AssistantMood,
            status = values[1] as ConnectionStatus,
            isMic = values[2] as Boolean,
            isStandby = values[3] as Boolean,
            userLevel = values[4] as Float,
            assistantLevel = values[5] as Float,
            standbyLevel = values[6] as Float
        )
    }

    private val transcriptAndMetaFlow = combine(
        liveManager.emotionTone,
        liveManager.inputTranscript,
        liveManager.outputTranscript,
        liveManager.activeToolLabel,
        liveManager.errorMessage
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        values
    }

    private val localDataFlow = combine(
        _pendingConfirmation,
        _statusBanner,
        settingsRepo.settingsFlow,
        _permissions,
        memoryRepo.memories,
        memoryRepo.reminders
    ) { values ->
        values
    }

    private val visionFlow = combine(
        _selectedBitmap,
        _visionResult,
        _isAnalyzingVision,
        _visionError
    ) { bitmap, result, analyzing, err ->
        arrayOf<Any?>(bitmap, result, analyzing, err)
    }

    val uiState: StateFlow<BrstiUiState> = combine(
        coreLiveFlow,
        transcriptAndMetaFlow,
        localDataFlow,
        visionFlow
    ) { live, meta, local, vision ->
        val effectiveUserLevel = if (live.isStandby) live.standbyLevel else live.userLevel
        val effectiveMood = when {
            live.isStandby && !live.isMic -> AssistantMood.SLEEPING
            else -> live.mood
        }
        val effectiveStatus = when {
            live.isStandby && !live.isMic -> ConnectionStatus.STANDBY
            else -> live.status
        }

        @Suppress("UNCHECKED_CAST")
        BrstiUiState(
            mood = effectiveMood,
            connectionStatus = effectiveStatus,
            emotionTone = meta[0] as com.example.ui.state.EmotionTone,
            isMicActive = live.isMic,
            isWakeWordStandby = live.isStandby,
            userAudioLevel = effectiveUserLevel,
            assistantAudioLevel = live.assistantLevel,
            latestUserText = meta[1] as String,
            latestBrstiText = meta[2] as String,
            activeToolLabel = meta[3] as String?,
            errorMessage = meta[4] as String?,
            pendingConfirmation = local[0] as PendingActionConfirmation?,
            statusBanner = local[1] as String?,
            settings = local[2] as com.example.data.local.BrstiSettings,
            permissions = local[3] as com.example.permissions.PermissionSnapshot,
            memories = local[4] as List<com.example.data.local.MemoryPreferenceEntity>,
            reminders = local[5] as List<com.example.data.local.ReminderEntity>,
            selectedVisionBitmap = vision[0] as Bitmap?,
            visionAnalysisResult = vision[1] as String?,
            isAnalyzingVision = vision[2] as Boolean,
            visionError = vision[3] as String?
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BrstiUiState()
    )

    fun refreshPermissions() {
        _permissions.value = PermissionManager.captureSnapshot(getApplication())
    }

    fun toggleMicrophone() {
        val snap = PermissionManager.captureSnapshot(getApplication())
        _permissions.value = snap
        if (!snap.recordAudioGranted) {
            _events.tryEmit(BrstiNavigationEvent.RequestMicPermission)
            return
        }
        if (container.isMicActive.value || liveManager.isRunning()) {
            val wakeEnabled = uiState.value.settings.wakeWordEnabled
            if (wakeEnabled) {
                BrstiVoiceService.startWakeStandby(getApplication())
                _statusBanner.value = "Switched to \"Hey Brsti\" standby."
            } else {
                BrstiVoiceService.stop(getApplication())
                _statusBanner.value = "Microphone turned OFF."
            }
        } else {
            _statusBanner.value = null
            BrstiVoiceService.startLive(getApplication())
        }
    }

    fun toggleWakeWordStandby() {
        val snap = PermissionManager.captureSnapshot(getApplication())
        _permissions.value = snap
        if (!snap.recordAudioGranted) {
            _events.tryEmit(BrstiNavigationEvent.RequestMicPermission)
            return
        }
        if (container.isWakeStandbyActive.value) {
            BrstiVoiceService.stop(getApplication())
            viewModelScope.launch { settingsRepo.setWakeWordEnabled(false) }
            _statusBanner.value = "Wake-word standby stopped."
        } else {
            viewModelScope.launch { settingsRepo.setWakeWordEnabled(true) }
            BrstiVoiceService.startWakeStandby(getApplication())
            _statusBanner.value = "Listening locally for \"Hey Brsti\"."
        }
    }

    fun triggerWakeWordManually() {
        val snap = PermissionManager.captureSnapshot(getApplication())
        _permissions.value = snap
        if (!snap.recordAudioGranted) {
            _events.tryEmit(BrstiNavigationEvent.RequestMicPermission)
            return
        }
        BrstiVoiceService.triggerWakeNow(getApplication())
    }

    fun stopAssistantCompletely() {
        BrstiVoiceService.stop(getApplication())
        _statusBanner.value = "BRSTI AI paused."
    }

    fun sendFallbackPrompt(prompt: String) {
        if (prompt.isBlank()) return
        val snap = PermissionManager.captureSnapshot(getApplication())
        if (snap.recordAudioGranted && !liveManager.isRunning()) {
            container.isMicActive.value = true
            BrstiVoiceService.startLive(getApplication())
        }
        liveManager.sendTextPrompt(prompt)
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun confirmPendingAction() {
        val action = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        if (action is PendingActionConfirmation.PhotoCapture) {
            _events.tryEmit(BrstiNavigationEvent.LaunchCameraCapture)
            return
        }
        val outcome = actionExecutor.executeConfirmedAction(action)
        _statusBanner.value = outcome
        liveManager.injectContextNote("System note: Owner confirmed and completed action: $outcome")
    }

    fun dismissPendingAction() {
        val action = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        _statusBanner.value = "Cancelled ${action.title}."
        liveManager.injectContextNote("System note: Owner cancelled ${action.title}. Acknowledge gracefully.")
    }

    fun setSelectedVisionBitmap(bitmap: Bitmap?, autoQuestion: String? = null) {
        _selectedBitmap.value = bitmap
        _visionResult.value = null
        _visionError.value = null
        if (bitmap != null && !autoQuestion.isNullOrBlank()) {
            analyzeSelectedVision(autoQuestion)
        }
    }

    fun analyzeSelectedVision(question: String) {
        val bitmap = _selectedBitmap.value ?: return
        viewModelScope.launch {
            _isAnalyzingVision.value = true
            _visionError.value = null
            val ownerName = uiState.value.settings.ownerName
            val result = visionAnalyzer.analyzeImage(bitmap, question, ownerName)
            _isAnalyzingVision.value = false
            result.fold(
                onSuccess = { text ->
                    _visionResult.value = text
                    if (liveManager.isRunning()) {
                        liveManager.injectContextNote("System visual analysis of owner's photo: $text. Briefly react or summarize it naturally if relevant.")
                    }
                },
                onFailure = { e ->
                    _visionError.value = e.localizedMessage ?: "Failed to analyze photo."
                }
            )
        }
    }

    fun requestCameraWithApproval(reason: String = "Take a photo so BRSTI can analyze what is in front of you.") {
        _pendingConfirmation.value = PendingActionConfirmation.PhotoCapture(reason = reason)
    }

    // AssistantActionHost callbacks
    override fun stageConfirmation(confirmation: PendingActionConfirmation) {
        _pendingConfirmation.value = confirmation
    }

    override fun openMediaVisionPicker() {
        _events.tryEmit(BrstiNavigationEvent.OpenMediaVision)
    }

    override fun requestSleepMode() {
        viewModelScope.launch {
            val settings = settingsRepo.getSnapshot()
            if (settings.wakeWordEnabled && PermissionManager.hasPermission(getApplication(), android.Manifest.permission.RECORD_AUDIO)) {
                BrstiVoiceService.startWakeStandby(getApplication())
            } else {
                BrstiVoiceService.stop(getApplication())
            }
        }
    }

    override fun requestRuntimePermissionHint(permissionName: String) {
        _events.tryEmit(BrstiNavigationEvent.RequestPermissionHint(permissionName))
    }

    // Settings & Memory actions
    fun updateOwnerName(name: String) = viewModelScope.launch { settingsRepo.setOwnerName(name) }
    fun updateVoice(voice: String) = viewModelScope.launch { settingsRepo.setAssistantVoice(voice) }
    fun updateLanguage(mode: String) = viewModelScope.launch { settingsRepo.setLanguageMode(mode) }
    fun updateWakeWordEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setWakeWordEnabled(enabled) }
        if (enabled && PermissionManager.hasPermission(getApplication(), android.Manifest.permission.RECORD_AUDIO)) {
            if (!container.isMicActive.value) {
                BrstiVoiceService.startWakeStandby(getApplication())
            }
        } else if (!enabled && container.isWakeStandbyActive.value) {
            BrstiVoiceService.stop(getApplication())
        }
    }
    fun updateMemoryConsent(granted: Boolean) = viewModelScope.launch { settingsRepo.setMemoryConsent(granted) }
    fun updateLiveSubtitles(enabled: Boolean) = viewModelScope.launch { settingsRepo.setLiveSubtitles(enabled) }
    fun deleteMemory(key: String) = viewModelScope.launch { memoryRepo.deleteMemory(key) }
    fun clearMemories() = viewModelScope.launch { memoryRepo.clearMemories() }
    fun toggleReminder(id: Long, completed: Boolean) = viewModelScope.launch { memoryRepo.toggleReminder(id, completed) }
    fun deleteReminder(id: Long) = viewModelScope.launch { memoryRepo.deleteReminder(id) }
}
