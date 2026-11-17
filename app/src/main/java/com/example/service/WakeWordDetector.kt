package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

private const val TAG = "WakeWordDetector"
private const val SAMPLE_RATE = 16000

/**
 * Local AudioRecord energy monitor + on-device SpeechRecognizer burst verification
 * for the "Hey Brsti" / "Brsti" wake word when in Standby mode.
 * Never streams idle background audio to Gemini servers.
 */
class WakeWordDetector(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit,
    private val onEnergyUpdate: (Float) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isRunning = AtomicBoolean(false)
    private val isCheckingSpeech = AtomicBoolean(false)

    private var monitorJob: Job? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private val wakeKeywords = listOf(
        "hey brsti", "hi brsti", "ok brsti", "brsti",
        "hey bristi", "bristi", "brishti", "hey brishti",
        "বৃষ্টি", "হেই বৃষ্টি", "ওহে বৃষ্টি", "breeze tea", "christy"
    )

    fun start() {
        if (isRunning.getAndSet(true)) return
        monitorJob = scope.launch {
            runMonitorLoop()
        }
    }

    fun stop() {
        isRunning.set(false)
        monitorJob?.cancel()
        monitorJob = null
        mainHandler.post {
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
        }
        onEnergyUpdate(0f)
    }

    @SuppressLint("MissingPermission")
    private suspend fun runMonitorLoop() {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(3200)

        while (coroutineContext.isActive && isRunning.get()) {
            if (isCheckingSpeech.get()) {
                delay(150L)
                continue
            }

            val recorder = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                )
            }.getOrNull()

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                delay(1000L)
                continue
            }

            val buf = ByteArray(1600)
            var speechFrames = 0
            try {
                recorder.startRecording()
                while (coroutineContext.isActive && isRunning.get() && !isCheckingSpeech.get()) {
                    val read = recorder.read(buf, 0, buf.size)
                    if (read > 0) {
                        val energy = computeLevel(buf, read)
                        onEnergyUpdate(energy)
                        if (energy > 0.14f) {
                            speechFrames++
                            if (speechFrames >= 3) {
                                // Speech onset detected -> release AudioRecord and verify phrase locally
                                break
                            }
                        } else {
                            speechFrames = (speechFrames - 1).coerceAtLeast(0)
                        }
                    } else {
                        delay(50L)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord monitor paused", e)
            } finally {
                runCatching {
                    if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
                    recorder.release()
                }
            }

            if (coroutineContext.isActive && isRunning.get() && speechFrames >= 3) {
                verifyWakeWordWithSpeechRecognizer()
            }
        }
    }

    private suspend fun verifyWakeWordWithSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.d(TAG, "On-device SpeechRecognizer unavailable on this device.")
            delay(500L)
            return
        }
        isCheckingSpeech.set(true)
        mainHandler.post {
            if (!isRunning.get()) {
                isCheckingSpeech.set(false)
                return@post
            }
            try {
                speechRecognizer?.destroy()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer = recognizer
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {
                        checkResults(partialResults)
                    }
                    override fun onResults(results: Bundle?) {
                        checkResults(results)
                        isCheckingSpeech.set(false)
                    }
                    override fun onError(error: Int) {
                        isCheckingSpeech.set(false)
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                recognizer.startListening(intent)
            } catch (e: Exception) {
                Log.w(TAG, "SpeechRecognizer check failed", e)
                isCheckingSpeech.set(false)
            }
        }

        // Guard timeout so AudioRecord resumes cleanly even if recognizer hangs
        var waited = 0L
        while (isCheckingSpeech.get() && waited < 4500L && isRunning.get()) {
            delay(150L)
            waited += 150L
        }
        if (isCheckingSpeech.getAndSet(false)) {
            mainHandler.post { runCatching { speechRecognizer?.cancel() } }
        }
    }

    private fun checkResults(bundle: Bundle?) {
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val hit = matches.any { candidate ->
            val norm = candidate.lowercase().trim()
            wakeKeywords.any { kw -> norm.contains(kw) }
        }
        if (hit && isRunning.get()) {
            Log.i(TAG, "Wake word detected from matches: $matches")
            stop()
            onWakeWordDetected()
        }
    }

    private fun computeLevel(pcm: ByteArray, length: Int): Float {
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
        return ((sqrt(sum / count) / 10000.0).toFloat()).coerceIn(0f, 1f)
    }
}
