package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.BrstiApplication
import com.example.MainActivity
import com.example.R
import com.example.ui.state.AssistantMood
import com.example.ui.state.ConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val TAG = "BrstiVoiceService"
private const val CHANNEL_ID = "brsti_voice_channel"
private const val NOTIFICATION_ID = 1001

class BrstiVoiceService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeWordDetector: WakeWordDetector? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ASSISTANT -> {
                stopAllVoiceModes()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_LIVE -> {
                startInForeground("Microphone active • Talking with BRSTI")
                startLiveVoiceSession(greeting = null)
            }
            ACTION_START_WAKE_STANDBY -> {
                startInForeground("Standby • Listening locally for \"Hey Brsti\"")
                startWakeWordStandby()
            }
            ACTION_WAKE_NOW -> {
                startInForeground("Microphone active • \"Hey Brsti\" triggered")
                val owner = (application as BrstiApplication).container.settingsRepository
                serviceScope.launch {
                    val settings = owner.getSnapshot()
                    startLiveVoiceSession("Hey Brsti! (Owner ${settings.ownerName} just called your wake word. Greet them playfully in their language!)")
                }
            }
            else -> {
                startInForeground("Standby • BRSTI Voice Service Ready")
            }
        }
        return START_STICKY
    }

    private fun startInForeground(statusText: String) {
        val notification = buildNotification(statusText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(statusText: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    private fun startLiveVoiceSession(greeting: String?) {
        wakeWordDetector?.stop()
        wakeWordDetector = null
        val container = (application as BrstiApplication).container
        container.isWakeStandbyActive.value = false
        container.isMicActive.value = true
        serviceScope.launch {
            val settings = container.settingsRepository.getSnapshot()
            val memories = container.memoryRepository.getMemoriesSnapshot()
            container.geminiLiveManager.startLiveSession(settings, memories, greeting)
        }
    }

    private fun startWakeWordStandby() {
        val container = (application as BrstiApplication).container
        container.geminiLiveManager.stopSession(
            nextMood = AssistantMood.SLEEPING,
            nextStatus = ConnectionStatus.STANDBY
        )
        container.isMicActive.value = false
        container.isWakeStandbyActive.value = true

        wakeWordDetector?.stop()
        wakeWordDetector = WakeWordDetector(
            context = applicationContext,
            onWakeWordDetected = {
                serviceScope.launch {
                    val settings = container.settingsRepository.getSnapshot()
                    updateNotification("Microphone active • \"Hey Brsti\" awake!")
                    startLiveVoiceSession("Hey Brsti! (${settings.ownerName} woke you with 'Hey Brsti'. Greet warmly and playfully!)")
                }
            },
            onEnergyUpdate = { level ->
                container.standbyEnergyLevel.value = level
            }
        ).also { it.start() }
    }

    private fun stopAllVoiceModes() {
        wakeWordDetector?.stop()
        wakeWordDetector = null
        val container = (application as BrstiApplication).container
        container.isWakeStandbyActive.value = false
        container.isMicActive.value = false
        container.standbyEnergyLevel.value = 0f
        container.geminiLiveManager.stopSession(
            nextMood = AssistantMood.WAITING,
            nextStatus = ConnectionStatus.DISCONNECTED
        )
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, BrstiVoiceService::class.java).apply { action = ACTION_STOP_ASSISTANT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(statusText)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Mic", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.voice_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.voice_channel_description)
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopAllVoiceModes()
        serviceScope.cancel()
        Log.d(TAG, "BrstiVoiceService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_LIVE = "com.example.action.START_LIVE"
        const val ACTION_START_WAKE_STANDBY = "com.example.action.START_WAKE_STANDBY"
        const val ACTION_WAKE_NOW = "com.example.action.WAKE_NOW"
        const val ACTION_STOP_ASSISTANT = "com.example.action.STOP_ASSISTANT"

        fun startLive(context: Context) {
            val intent = Intent(context, BrstiVoiceService::class.java).apply { action = ACTION_START_LIVE }
            dispatchService(context, intent)
        }

        fun startWakeStandby(context: Context) {
            val intent = Intent(context, BrstiVoiceService::class.java).apply { action = ACTION_START_WAKE_STANDBY }
            dispatchService(context, intent)
        }

        fun triggerWakeNow(context: Context) {
            val intent = Intent(context, BrstiVoiceService::class.java).apply { action = ACTION_WAKE_NOW }
            dispatchService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BrstiVoiceService::class.java).apply { action = ACTION_STOP_ASSISTANT }
            runCatching { context.startService(intent) }
        }

        private fun dispatchService(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
