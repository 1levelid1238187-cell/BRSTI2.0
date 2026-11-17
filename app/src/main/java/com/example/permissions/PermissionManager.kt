package com.example.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class PermissionSnapshot(
    val recordAudioGranted: Boolean = false,
    val postNotificationsGranted: Boolean = true,
    val readContactsGranted: Boolean = false,
    val callPhoneGranted: Boolean = false,
    val notificationListenerEnabled: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false
)

object PermissionManager {
    fun captureSnapshot(context: Context): PermissionSnapshot {
        val mic = hasPermission(context, Manifest.permission.RECORD_AUDIO)
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
        val contacts = hasPermission(context, Manifest.permission.READ_CONTACTS)
        val call = hasPermission(context, Manifest.permission.CALL_PHONE)
        val notifListener = NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryIgnored = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false

        return PermissionSnapshot(
            recordAudioGranted = mic,
            postNotificationsGranted = notif,
            readContactsGranted = contacts,
            callPhoneGranted = call,
            notificationListenerEnabled = notifListener,
            batteryOptimizationIgnored = batteryIgnored
        )
    }

    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun openAppSettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun openNotificationListenerSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun openBatteryOptimizationSettingsIntent(): Intent {
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
