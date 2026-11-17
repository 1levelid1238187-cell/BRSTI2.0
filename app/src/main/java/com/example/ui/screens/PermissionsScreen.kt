package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.permissions.PermissionManager
import com.example.ui.state.BrstiUiState
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldStatus
import com.example.ui.theme.IndigoOutline
import com.example.ui.theme.LavenderMuted
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianDeep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    uiState: BrstiUiState,
    onBack: () -> Unit,
    onRequestPermission: (String) -> Unit
) {
    val context = LocalContext.current
    val perms = uiState.permissions

    Scaffold(
        containerColor = ObsidianDeep,
        topBar = {
            TopAppBar(
                title = { Text("Permissions & Privacy") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("permissions_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianDeep,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    color = NeonViolet.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, NeonViolet.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, tint = NeonCyan)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "BRSTI AI follows strict least-privilege privacy: permissions are requested only when needed, " +
                                "microphone activity always shows a persistent notification, and calls, WhatsApp, SMS, or Gmail never send without your explicit confirmation.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            item {
                PermissionCard(
                    icon = Icons.Rounded.Mic,
                    title = "Microphone (RECORD_AUDIO)",
                    description = "Required for real-time voice conversations with Gemini Live and local 'Hey Brsti' wake-word detection.",
                    granted = perms.recordAudioGranted,
                    actionLabel = if (perms.recordAudioGranted) "Granted" else "Grant Mic",
                    onAction = { onRequestPermission(Manifest.permission.RECORD_AUDIO) }
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    PermissionCard(
                        icon = Icons.Rounded.Notifications,
                        title = "Notifications (POST_NOTIFICATIONS)",
                        description = "Shows the required Foreground Service indicator whenever BRSTI's microphone is active or in standby.",
                        granted = perms.postNotificationsGranted,
                        actionLabel = if (perms.postNotificationsGranted) "Granted" else "Allow Alerts",
                        onAction = { onRequestPermission(Manifest.permission.POST_NOTIFICATIONS) }
                    )
                }
            }

            item {
                PermissionCard(
                    icon = Icons.Rounded.Contacts,
                    title = "Contacts (READ_CONTACTS)",
                    description = "Lets BRSTI look up phone numbers when you ask to call, WhatsApp, or SMS a contact.",
                    granted = perms.readContactsGranted,
                    actionLabel = if (perms.readContactsGranted) "Granted" else "Grant Contacts",
                    onAction = { onRequestPermission(Manifest.permission.READ_CONTACTS) }
                )
            }

            item {
                PermissionCard(
                    icon = Icons.Rounded.Call,
                    title = "Phone Call (CALL_PHONE)",
                    description = "Optional: places confirmed calls directly after you tap Confirm. Otherwise BRSTI opens the Dialer safely.",
                    granted = perms.callPhoneGranted,
                    actionLabel = if (perms.callPhoneGranted) "Granted" else "Grant Call",
                    onAction = { onRequestPermission(Manifest.permission.CALL_PHONE) }
                )
            }

            item {
                PermissionCard(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "Notification Reader (Optional)",
                    description = "Allows BRSTI to summarize recent notifications on your phone when you ask. Must be enabled manually in Android Settings.",
                    granted = perms.notificationListenerEnabled,
                    actionLabel = if (perms.notificationListenerEnabled) "Enabled (Settings)" else "Open Settings",
                    onAction = {
                        runCatching {
                            context.startActivity(PermissionManager.openNotificationListenerSettingsIntent())
                        }
                    }
                )
            }

            item {
                PermissionCard(
                    icon = Icons.Rounded.BatterySaver,
                    title = "Battery Optimization Guidance",
                    description = "Set BRSTI AI to Unrestricted in Android Battery settings if you want minimized 'Hey Brsti' standby to stay active longer.",
                    granted = perms.batteryOptimizationIgnored,
                    actionLabel = if (perms.batteryOptimizationIgnored) "Unrestricted" else "Battery Settings",
                    onAction = {
                        runCatching {
                            context.startActivity(PermissionManager.openBatteryOptimizationSettingsIntent())
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                OutlinedButton(
                    onClick = {
                        runCatching { context.startActivity(PermissionManager.openAppSettingsIntent(context)) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                ) {
                    Text("Open Android App Info & Permissions")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Surface(
        color = ObsidianCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (granted) EmeraldStatus.copy(alpha = 0.45f) else IndigoOutline
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (granted) EmeraldStatus else NeonViolet
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (granted) "ACTIVE" else "OFF",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (granted) EmeraldStatus else CrimsonError
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = LavenderMuted
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (granted) ObsidianDeep else NeonCyan,
                    contentColor = if (granted) NeonCyan else ObsidianDeep
                ),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(actionLabel)
            }
        }
    }
}
