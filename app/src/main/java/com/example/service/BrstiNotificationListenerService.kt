package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationDigestItem(
    val key: String,
    val appName: String,
    val title: String,
    val text: String,
    val postTime: Long
)

class BrstiNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isConnected.value = true
        refreshNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isConnected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshNotifications()
    }

    private fun refreshNotifications() {
        val current = runCatching { activeNotifications?.toList().orEmpty() }.getOrDefault(emptyList())
        val pm = applicationContext.packageManager
        val items = current
            .filter { it.packageName != applicationContext.packageName }
            .sortedByDescending { it.postTime }
            .take(12)
            .mapNotNull { sbn ->
                val extras = sbn.notification?.extras ?: return@mapNotNull null
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
                if (title.isBlank() && text.isBlank()) return@mapNotNull null
                val appLabel = runCatching {
                    val info = pm.getApplicationInfo(sbn.packageName, 0)
                    pm.getApplicationLabel(info).toString()
                }.getOrDefault(sbn.packageName)
                NotificationDigestItem(
                    key = sbn.key,
                    appName = appLabel,
                    title = title,
                    text = text,
                    postTime = sbn.postTime
                )
            }
        _recentNotifications.value = items
    }

    companion object {
        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _recentNotifications = MutableStateFlow<List<NotificationDigestItem>>(emptyList())
        val recentNotifications: StateFlow<List<NotificationDigestItem>> = _recentNotifications.asStateFlow()
    }
}
