package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.permissions.PermissionManager
import com.example.ui.state.BrstiUiState
import com.example.ui.theme.IndigoOutline
import com.example.ui.theme.LavenderMuted
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianDeep

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: BrstiUiState,
    onBack: () -> Unit,
    onUpdateOwnerName: (String) -> Unit,
    onUpdateVoice: (String) -> Unit,
    onUpdateLanguage: (String) -> Unit,
    onUpdateWakeWord: (Boolean) -> Unit,
    onUpdateMemoryConsent: (Boolean) -> Unit,
    onUpdateLiveSubtitles: (Boolean) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onClearMemories: () -> Unit,
    onToggleReminder: (Long, Boolean) -> Unit,
    onDeleteReminder: (Long) -> Unit
) {
    val context = LocalContext.current
    var ownerDraft by remember(uiState.settings.ownerName) { mutableStateOf(uiState.settings.ownerName) }

    val voices = listOf(
        "Leda" to "Youthful & Playful",
        "Aoede" to "Breezy & Sweet",
        "Zephyr" to "Bright & Witty",
        "Sulafat" to "Warm & Caring",
        "Kore" to "Confident & Crisp"
    )
    val languages = listOf(
        "auto" to "Auto (বাংলা / EN / HI)",
        "bn" to "বাংলা (Bengali)",
        "en" to "English",
        "hi" to "हिन्दी (Hindi)"
    )

    Scaffold(
        containerColor = ObsidianDeep,
        topBar = {
            TopAppBar(
                title = { Text("BRSTI AI Settings") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_back_button")
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SettingsSectionCard(title = "Owner Personalization") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = ownerDraft,
                            onValueChange = { ownerDraft = it },
                            label = { Text("Owner Name") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("owner_name_input")
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = { onUpdateOwnerName(ownerDraft) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ObsidianDeep),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Save")
                        }
                    }
                }
            }

            item {
                SettingsSectionCard(title = "BRSTI Voice & Personality") {
                    Text(
                        text = "Select feminine Gemini Live voice",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LavenderMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        voices.forEach { (voiceId, desc) ->
                            val selected = uiState.settings.assistantVoice == voiceId
                            FilterChip(
                                selected = selected,
                                onClick = { onUpdateVoice(voiceId) },
                                label = { Text("$voiceId • $desc") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonViolet.copy(alpha = 0.25f),
                                    selectedLabelColor = NeonCyan
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Response Language",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LavenderMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        languages.forEach { (code, label) ->
                            FilterChip(
                                selected = uiState.settings.languageMode == code,
                                onClick = { onUpdateLanguage(code) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            item {
                SettingsSectionCard(title = "Wake Word & Background Service") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hey Brsti Standby Mode", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Runs a visible Android Microphone Foreground Service with persistent notification.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LavenderMuted
                            )
                        }
                        Switch(
                            checked = uiState.settings.wakeWordEnabled,
                            onCheckedChange = onUpdateWakeWord,
                            modifier = Modifier.testTag("wake_word_switch")
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Live Voice Subtitles", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Show compact floating transcript below BRSTI's orb.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LavenderMuted
                            )
                        }
                        Switch(
                            checked = uiState.settings.liveSubtitlesEnabled,
                            onCheckedChange = onUpdateLiveSubtitles
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = ObsidianDeep.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, IndigoOutline)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null, tint = NeonCyan)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Battery & Background Restrictions", style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Android restricts background microphone access and battery usage by design. " +
                                    "BRSTI uses an explicit foreground service notification and cannot bypass OS security policies. " +
                                    "For reliable minimized wake-word standby, set BRSTI AI to Unrestricted in Battery settings.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LavenderMuted
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(PermissionManager.openBatteryOptimizationSettingsIntent())
                                    }
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("Open Battery Optimization Settings")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(PermissionManager.openNotificationListenerSettingsIntent())
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (uiState.permissions.notificationListenerEnabled) {
                                "Notification Access Enabled (Manage)"
                            } else {
                                "Enable Notification Access (Optional)"
                            }
                        )
                    }
                }
            }

            item {
                SettingsSectionCard(title = "Conversational Memory (With Consent)") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Remember Preferences", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Allow BRSTI to store preferences you share locally on device.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LavenderMuted
                            )
                        }
                        Switch(
                            checked = uiState.settings.memoryConsentGranted,
                            onCheckedChange = onUpdateMemoryConsent
                        )
                    }
                    if (uiState.memories.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        uiState.memories.forEach { mem ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(mem.key, style = MaterialTheme.typography.labelMedium, color = NeonCyan)
                                    Text(mem.value, style = MaterialTheme.typography.bodyMedium)
                                }
                                IconButton(
                                    onClick = { onDeleteMemory(mem.key) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete memory", tint = LavenderMuted)
                                }
                            }
                        }
                        TextButton(onClick = onClearMemories) {
                            Text("Clear All Remembered Preferences")
                        }
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No saved preferences yet. Tell BRSTI something you want her to remember!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LavenderMuted
                        )
                    }
                }
            }

            item {
                SettingsSectionCard(title = "BRSTI Reminders (${uiState.reminders.size})") {
                    if (uiState.reminders.isEmpty()) {
                        Text(
                            text = "No reminders yet. Say \"আমার জন্য একটা reminder সেট করো\" to create one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LavenderMuted
                        )
                    } else {
                        uiState.reminders.forEach { reminder ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                IconButton(
                                    onClick = { onToggleReminder(reminder.id, !reminder.isCompleted) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = if (reminder.isCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                        contentDescription = "Toggle reminder",
                                        tint = if (reminder.isCompleted) NeonCyan else LavenderMuted
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(reminder.title, style = MaterialTheme.typography.bodyLarge)
                                    Text(reminder.scheduleText, style = MaterialTheme.typography.labelSmall, color = LavenderMuted)
                                }
                                IconButton(
                                    onClick = { onDeleteReminder(reminder.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete reminder", tint = LavenderMuted)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = ObsidianCard,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, IndigoOutline.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = NeonViolet
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}
