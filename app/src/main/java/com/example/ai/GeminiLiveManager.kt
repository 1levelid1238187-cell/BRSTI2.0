package com.example.ai

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.data.local.BrstiSettings
import com.example.data.local.MemoryPreferenceEntity
import com.example.device.DeviceActionExecutor
import com.example.ui.state.AssistantMood
import com.example.ui.state.ConnectionStatus
import com.example.ui.state.EmotionTone
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.ContextWindowCompressionConfig
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LiveServerContent
import com.google.firebase.ai.type.LiveServerGoAway
import com.google.firebase.ai.type.LiveServerToolCall
import com.google.firebase.ai.type.LiveServerToolCallCancellation
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.LiveSessionResumptionUpdate
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SessionResumptionConfig
import com.google.firebase.ai.type.SlidingWindow
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.ActivityDetectionConfig
import com.google.firebase.ai.type.RealtimeInputConfig
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

private const val TAG = "GeminiLiveManager"
private const val LIVE_MODEL_NAME = "gemini-3.1-flash-live-preview"
private const val INPUT_SAMPLE_RATE = 16000
private const val OUTPUT_SAMPLE_RATE = 24000

@OptIn(PublicPreviewAPI::class)
class GeminiLiveManager(
    private val context: Context,
    private val deviceActionExecutor: DeviceActionExecutor
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _mood = MutableStateFlow(AssistantMood.WAITING)
    val mood: StateFlow<AssistantMood> = _mood.asStateFlow()

    private val _emotionTone = MutableStateFlow(EmotionTone.PLAYFUL)
    val emotionTone: StateFlow<EmotionTone> = _emotionTone.asStateFlow()

    private val _userAudioLevel = MutableStateFlow(0f)
    val userAudioLevel: StateFlow<Float> = _userAudioLevel.asStateFlow()

    private val _assistantAudioLevel = MutableStateFlow(0f)
    val assistantAudioLevel: StateFlow<Float> = _assistantAudioLevel.asStateFlow()

    private val _inputTranscript = MutableStateFlow("")
    val inputTranscript: StateFlow<String> = _inputTranscript.asStateFlow()

    private val _outputTranscript = MutableStateFlow("")
    val outputTranscript: StateFlow<String> = _outputTranscript.asStateFlow()

    private val _activeToolLabel = MutableStateFlow<String?>(null)
    val activeToolLabel: StateFlow<String?> = _activeToolLabel.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var liveSession: LiveSession? = null
    private var sessionJob: Job? = null
    private var audioCaptureJob: Job? = null
    private var playbackJob: Job? = null
    private var audioTrack: AudioTrack? = null
    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    private var playbackChannel = Channel<ByteArray>(capacity = Channel.UNLIMITED)
    private val isIntentionalStop = AtomicBoolean(false)
    private val isSessionActive = AtomicBoolean(false)
    private var activeSessionHandle: String? = null
    private var lastSettings: BrstiSettings = BrstiSettings()
    private var lastMemories: List<MemoryPreferenceEntity> = emptyList()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val userTranscriptBuffer = StringBuilder()
    private val modelTranscriptBuffer = StringBuilder()

    fun isRunning(): Boolean = isSessionActive.get()

    fun startLiveSession(
        settings: BrstiSettings,
        memories: List<MemoryPreferenceEntity>,
        greetingTriggerPrompt: String? = null
    ) {
        if (isSessionActive.getAndSet(true)) return
        isIntentionalStop.set(false)
        lastSettings = settings
        lastMemories = memories
        _errorMessage.value = null
        registerNetworkMonitor()

        sessionJob = scope.launch {
            connectWithRetry(greetingTriggerPrompt)
        }
    }

    private suspend fun connectWithRetry(greetingTriggerPrompt: String?) {
        var attempt = 0
        var initialPrompt = greetingTriggerPrompt
        while (isSessionActive.get() && !isIntentionalStop.get()) {
            try {
                _connectionStatus.value = if (attempt == 0) {
                    ConnectionStatus.CONNECTING
                } else {
                    ConnectionStatus.RECONNECTING
                }
                _mood.value = AssistantMood.THINKING

                val sysInstruction = BrstiPersonality.buildSystemInstruction(lastSettings, lastMemories)
                val voiceName = lastSettings.assistantVoice.ifBlank { "Leda" }

                val liveModel = Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
                    modelName = LIVE_MODEL_NAME,
                    generationConfig = liveGenerationConfig {
                        responseModality = ResponseModality.AUDIO
                        speechConfig = SpeechConfig(voice = Voice(voiceName))
                        inputAudioTranscription = AudioTranscriptionConfig()
                        outputAudioTranscription = AudioTranscriptionConfig()
                        contextWindowCompression = ContextWindowCompressionConfig(
                            triggerTokens = 10000,
                            slidingWindow = SlidingWindow(targetTokens = 2000)
                        )
                        realtimeInputConfig = RealtimeInputConfig.builder()
                            .setAutomaticActivityDetection(
                                ActivityDetectionConfig.builder()
                                    .setStartSensitivity(ActivityDetectionConfig.Sensitivity.LOW)
                                    .setEndSensitivity(ActivityDetectionConfig.Sensitivity.LOW)
                                    .setPrefixPaddingMs(30)
                                    .setSilenceDurationMs(550)
                                    .build()
                            )
                            .setActivityHandling(RealtimeInputConfig.ActivityHandling.INTERRUPT)
                            .setTurnCoverage(RealtimeInputConfig.TurnCoverage.ONLY_ACTIVITY)
                            .build()
                    },
                    tools = BrstiTools.buildAllTools(),
                    systemInstruction = content { text(sysInstruction) }
                )

                val session = withTimeout(30_000L) {
                    val handle = activeSessionHandle
                    if (!handle.isNullOrBlank()) {
                        liveModel.connect(SessionResumptionConfig(handle))
                    } else {
                        liveModel.connect(SessionResumptionConfig())
                    }
                }
                liveSession = session
                attempt = 0
                _connectionStatus.value = ConnectionStatus.CONNECTED
                _mood.value = AssistantMood.LISTENING
                _errorMessage.value = null

                startAudioPlaybackLoop()
                startMicrophoneStreamingLoop(session)

                val promptToSend = initialPrompt
                if (!promptToSend.isNullOrBlank()) {
                    session.send(content("user") { text(promptToSend) }, turnComplete = true)
                    initialPrompt = null
                }

                listenToServerMessages(session)

                // If receive() finishes cleanly without intentional stop, attempt seamless resume
                if (isIntentionalStop.get() || !isSessionActive.get()) break
                delay(700L)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Live session error on attempt $attempt", e)
                stopAudioLoopsOnly()
                runCatching { liveSession?.close() }
                liveSession = null

                if (isIntentionalStop.get() || !isSessionActive.get()) break

                attempt++
                if (attempt >= 4) {
                    _connectionStatus.value = ConnectionStatus.ERROR
                    _errorMessage.value = e.localizedMessage ?: "Failed to connect to Gemini Live API."
                    _mood.value = AssistantMood.WAITING
                    isSessionActive.set(false)
                    break
                }
                _connectionStatus.value = ConnectionStatus.RECONNECTING
                delay((attempt * 1500L).coerceAtMost(5000L))
            }
        }
    }

    private suspend fun listenToServerMessages(session: LiveSession) {
        session.receive().collect { message ->
            when (message) {
                is LiveServerContent -> handleServerContent(message)
                is LiveServerToolCall -> handleToolCall(session, message)
                is LiveServerToolCallCancellation -> {
                    Log.d(TAG, "Tool call cancelled by interruption: ${message.functionIds}")
                    _activeToolLabel.value = null
                }
                is LiveSessionResumptionUpdate -> {
                    if (message.resumable == true && !message.newHandle.isNullOrBlank()) {
                        activeSessionHandle = message.newHandle
                    }
                }
                is LiveServerGoAway -> {
                    Log.w(TAG, "Server going away in ${message.timeLeft}; preparing reconnect.")
                    _connectionStatus.value = ConnectionStatus.RECONNECTING
                }
            }
        }
    }

    private fun handleServerContent(contentMsg: LiveServerContent) {
        if (contentMsg.interrupted) {
            Log.d(TAG, "User interrupted BRSTI; flushing audio playback immediately.")
            flushAudioPlayback()
            modelTranscriptBuffer.clear()
            _assistantAudioLevel.value = 0f
            _mood.value = AssistantMood.LISTENING
            return
        }

        contentMsg.inputTranscription?.text?.let { chunk ->
            if (chunk.isNotBlank()) {
                userTranscriptBuffer.append(chunk)
                val text = userTranscriptBuffer.toString().trim()
                _inputTranscript.value = text
                detectEmotionFromText(text)
            }
        }

        contentMsg.outputTranscription?.text?.let { chunk ->
            if (chunk.isNotBlank()) {
                modelTranscriptBuffer.append(chunk)
                _outputTranscript.value = modelTranscriptBuffer.toString().trim()
            }
        }

        val parts = contentMsg.content?.parts.orEmpty()
        var hasAudio = false
        for (part in parts) {
            if (part is InlineDataPart && part.mimeType.startsWith("audio/pcm")) {
                hasAudio = true
                playbackChannel.trySend(part.inlineData)
            }
        }
        if (hasAudio) {
            _mood.value = AssistantMood.SPEAKING
            userTranscriptBuffer.clear()
        }

        if (contentMsg.turnComplete) {
            modelTranscriptBuffer.clear()
            userTranscriptBuffer.clear()
            scope.launch {
                delay(280L)
                if (_mood.value == AssistantMood.SPEAKING) {
                    _assistantAudioLevel.value = 0f
                    _mood.value = AssistantMood.LISTENING
                }
            }
        }
    }

    private suspend fun handleToolCall(session: LiveSession, toolCall: LiveServerToolCall) {
        _mood.value = AssistantMood.THINKING
        val responses = mutableListOf<FunctionResponsePart>()
        for (call in toolCall.functionCalls) {
            _activeToolLabel.value = humanizeToolName(call.name)
            val resultJson = try {
                deviceActionExecutor.execute(call.name, call.args)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Tool execution failed for ${call.name}", e)
                kotlinx.serialization.json.buildJsonObject {
                    put("success", kotlinx.serialization.json.JsonPrimitive(false))
                    put("error", kotlinx.serialization.json.JsonPrimitive(e.localizedMessage ?: "Tool failed"))
                }
            }
            responses.add(FunctionResponsePart(call.name, resultJson, call.id))
        }
        _activeToolLabel.value = null
        runCatching { session.sendFunctionResponse(responses) }
            .onFailure { Log.e(TAG, "Failed sending function response", it) }
    }

    @SuppressLint("MissingPermission")
    private fun startMicrophoneStreamingLoop(session: LiveSession) {
        audioCaptureJob?.cancel()
        audioCaptureJob = scope.launch {
            val minBuf = AudioRecord.getMinBufferSize(
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(3200)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                _errorMessage.value = "Microphone hardware could not be initialized."
                return@launch
            }

            audioRecord = recorder
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = runCatching {
                    AcousticEchoCanceler.create(recorder.audioSessionId)?.apply { enabled = true }
                }.getOrNull()
            }
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = runCatching {
                    NoiseSuppressor.create(recorder.audioSessionId)?.apply { enabled = true }
                }.getOrNull()
            }

            val chunkSize = 1280 // 40ms at 16kHz 16-bit mono
            val buffer = ByteArray(chunkSize)

            try {
                recorder.startRecording()
                while (isActive && isSessionActive.get() && !isIntentionalStop.get()) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val pcmCopy = buffer.copyOf(read)
                        val level = computeRmsLevel(pcmCopy, read)
                        _userAudioLevel.value = level
                        session.sendAudioRealtime(InlineData(pcmCopy, "audio/pcm;rate=16000"))
                    } else if (read < 0) {
                        delay(50L)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Microphone streaming error", e)
            } finally {
                _userAudioLevel.value = 0f
                releaseRecorder()
            }
        }
    }

    private fun startAudioPlaybackLoop() {
        playbackJob?.cancel()
        playbackChannel.close()
        playbackChannel = Channel(Channel.UNLIMITED)

        playbackJob = scope.launch {
            val minBuf = AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4800)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(OUTPUT_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(minBuf * 2)
                .build()

            audioTrack = track
            track.play()

            try {
                for (pcmBytes in playbackChannel) {
                    if (!isActive || isIntentionalStop.get()) break
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        track.play()
                    }
                    _assistantAudioLevel.value = computeRmsLevel(pcmBytes, pcmBytes.size)
                    track.write(pcmBytes, 0, pcmBytes.size)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AudioTrack playback error", e)
            } finally {
                _assistantAudioLevel.value = 0f
                releaseTrack()
            }
        }
    }

    private fun flushAudioPlayback() {
        while (playbackChannel.tryReceive().isSuccess) {
            // drain queued stale audio chunks
        }
        runCatching {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.pause()
                    track.flush()
                    track.play()
                }
            }
        }
    }

    fun sendTextPrompt(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        _inputTranscript.value = trimmed
        detectEmotionFromText(trimmed)
        scope.launch {
            val session = liveSession
            if (session != null && !session.isClosed()) {
                _mood.value = AssistantMood.THINKING
                runCatching {
                    session.send(content("user") { text(trimmed) }, turnComplete = true)
                }.onFailure {
                    Log.e(TAG, "Failed sending text turn", it)
                    _errorMessage.value = it.localizedMessage ?: "Failed to send message."
                }
            } else {
                startLiveSession(lastSettings, lastMemories, greetingTriggerPrompt = trimmed)
            }
        }
    }

    fun injectContextNote(note: String) {
        scope.launch {
            val session = liveSession
            if (session != null && !session.isClosed()) {
                runCatching {
                    session.send(content("user") { text(note) }, turnComplete = true)
                }
            }
        }
    }

    fun stopSession(nextMood: AssistantMood = AssistantMood.WAITING, nextStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED) {
        isIntentionalStop.set(true)
        isSessionActive.set(false)
        unregisterNetworkMonitor()
        stopAudioLoopsOnly()
        sessionJob?.cancel()
        sessionJob = null
        scope.launch {
            runCatching { liveSession?.stopReceiving() }
            runCatching { liveSession?.close() }
            liveSession = null
        }
        _userAudioLevel.value = 0f
        _assistantAudioLevel.value = 0f
        _activeToolLabel.value = null
        _mood.value = nextMood
        _connectionStatus.value = nextStatus
    }

    private fun stopAudioLoopsOnly() {
        audioCaptureJob?.cancel()
        audioCaptureJob = null
        playbackJob?.cancel()
        playbackJob = null
        playbackChannel.close()
        releaseRecorder()
        releaseTrack()
    }

    private fun releaseRecorder() {
        runCatching { echoCanceler?.release() }
        echoCanceler = null
        runCatching { noiseSuppressor?.release() }
        noiseSuppressor = null
        runCatching {
            audioRecord?.let { rec ->
                if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) rec.stop()
                rec.release()
            }
        }
        audioRecord = null
    }

    private fun releaseTrack() {
        runCatching {
            audioTrack?.let { trk ->
                if (trk.playState == AudioTrack.PLAYSTATE_PLAYING) trk.stop()
                trk.flush()
                trk.release()
            }
        }
        audioTrack = null
    }

    private fun registerNetworkMonitor() {
        if (networkCallback != null) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                if (isSessionActive.get() && !isIntentionalStop.get()) {
                    _connectionStatus.value = ConnectionStatus.RECONNECTING
                }
            }

            override fun onAvailable(network: Network) {
                if (isSessionActive.get() && _connectionStatus.value == ConnectionStatus.ERROR) {
                    _errorMessage.value = null
                    sessionJob?.cancel()
                    sessionJob = scope.launch { connectWithRetry(null) }
                }
            }
        }
        networkCallback = cb
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { cm.registerNetworkCallback(req, cb) }
    }

    private fun unregisterNetworkMonitor() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        networkCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        networkCallback = null
    }

    private fun computeRmsLevel(pcm: ByteArray, length: Int): Float {
        if (length < 2) return 0f
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < length) {
            val sample = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort().toDouble()
            sum += sample * sample
            count++
            i += 2
        }
        if (count == 0) return 0f
        val rms = sqrt(sum / count)
        return ((rms / 12000.0).toFloat()).coerceIn(0f, 1f)
    }

    private fun detectEmotionFromText(text: String) {
        val lower = text.lowercase()
        _emotionTone.value = when {
            listOf("sad", "upset", "tired", "খারাপ", "মন খারাপ", "দুঃখ", "ক্লান্ত", "udaas").any { lower.contains(it) } -> EmotionTone.EMPATHETIC
            listOf("awesome", "great", "yay", "love", "দারুণ", "খুশি", "ভালো", "মজা", "khush").any { lower.contains(it) } -> EmotionTone.EXCITED
            listOf("angry", "hate", "annoying", "রাগ", "বিরক্ত").any { lower.contains(it) } -> EmotionTone.WARM
            listOf("joke", "funny", "tease", "হাসি", "মজা").any { lower.contains(it) } -> EmotionTone.WITTY
            else -> EmotionTone.PLAYFUL
        }
    }

    private fun humanizeToolName(name: String): String = when (name) {
        "openYouTube" -> "Opening YouTube..."
        "openWhatsApp", "prepareWhatsAppMessage", "sendWhatsAppMessage" -> "WhatsApp Action..."
        "makePhoneCall", "searchContact" -> "Checking Contacts..."
        "prepareSMS", "openSMSApp" -> "Preparing SMS..."
        "prepareGmail", "openGmailCompose" -> "Preparing Gmail..."
        "getWeather" -> "Fetching Live Weather..."
        "searchInternet" -> "Searching Internet..."
        "calculateMath" -> "Calculating..."
        "setAlarm" -> "Setting Alarm..."
        "createReminder" -> "Saving Reminder..."
        "readNotifications" -> "Reading Notifications..."
        else -> "Executing $name..."
    }
}
