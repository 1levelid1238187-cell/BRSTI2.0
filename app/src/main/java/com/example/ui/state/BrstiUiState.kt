package com.example.ui.state

import android.graphics.Bitmap
import com.example.data.local.BrstiSettings
import com.example.data.local.MemoryPreferenceEntity
import com.example.data.local.ReminderEntity
import com.example.device.PendingActionConfirmation
import com.example.permissions.PermissionSnapshot

enum class AssistantMood(val label: String) {
    LISTENING("Listening"),
    THINKING("Thinking"),
    SPEAKING("Speaking"),
    SLEEPING("Sleeping"),
    WAITING("Standby")
}

enum class ConnectionStatus(val label: String) {
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting..."),
    CONNECTED("Live • Gemini 3.1"),
    RECONNECTING("Reconnecting..."),
    STANDBY("Wake Word Ready"),
    ERROR("Connection Error")
}

enum class EmotionTone(val label: String) {
    PLAYFUL("Playful"),
    WARM("Warm & Sweet"),
    WITTY("Witty"),
    EMPATHETIC("Empathetic"),
    EXCITED("Excited")
}

data class TranscriptLine(
    val id: Long = System.nanoTime(),
    val speaker: String,
    val text: String,
    val isUser: Boolean
)

data class BrstiUiState(
    val mood: AssistantMood = AssistantMood.WAITING,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val emotionTone: EmotionTone = EmotionTone.PLAYFUL,
    val isMicActive: Boolean = false,
    val isWakeWordStandby: Boolean = false,
    val userAudioLevel: Float = 0f,
    val assistantAudioLevel: Float = 0f,
    val latestUserText: String = "",
    val latestBrstiText: String = "",
    val activeToolLabel: String? = null,
    val statusBanner: String? = null,
    val errorMessage: String? = null,
    val pendingConfirmation: PendingActionConfirmation? = null,
    val settings: BrstiSettings = BrstiSettings(),
    val permissions: PermissionSnapshot = PermissionSnapshot(),
    val memories: List<MemoryPreferenceEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val selectedVisionBitmap: Bitmap? = null,
    val visionAnalysisResult: String? = null,
    val isAnalyzingVision: Boolean = false,
    val visionError: String? = null
)
