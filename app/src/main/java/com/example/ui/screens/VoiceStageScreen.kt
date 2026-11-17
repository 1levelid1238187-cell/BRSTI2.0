package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.BrstiOrbAvatar
import com.example.ui.components.ConfirmationSheet
import com.example.ui.state.AssistantMood
import com.example.ui.state.BrstiUiState
import com.example.ui.state.ConnectionStatus
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldStatus
import com.example.ui.theme.IndigoOutline
import com.example.ui.theme.LavenderMuted
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.WarmGold

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VoiceStageScreen(
    uiState: BrstiUiState,
    onToggleMic: () -> Unit,
    onToggleWakeStandby: () -> Unit,
    onWakeNow: () -> Unit,
    onConfirmAction: () -> Unit,
    onDismissAction: () -> Unit,
    onDismissBanner: () -> Unit,
    onSendFallbackPrompt: (String) -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenVision: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var showPromptSheet by remember { mutableStateOf(false) }
    var draftPrompt by remember { mutableStateOf("") }

    val statusDotColor = when (uiState.connectionStatus) {
        ConnectionStatus.CONNECTED -> EmeraldStatus
        ConnectionStatus.CONNECTING, ConnectionStatus.RECONNECTING -> WarmGold
        ConnectionStatus.STANDBY -> NeonCyan
        ConnectionStatus.ERROR -> CrimsonError
        ConnectionStatus.DISCONNECTED -> LavenderMuted
    }

    val pulseTransition = rememberInfiniteTransition(label = "mic_pulse")
    val micRingScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_ring"
    )

    Scaffold(
        containerColor = ObsidianDeep,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ObsidianDeep,
                            Color(0xFF0E0A28),
                            Color(0xFF070B1E),
                            ObsidianDeep
                        )
                    )
                )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .align(Alignment.Center)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // Top Futuristic Status & Navigation Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = ObsidianCard.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, statusDotColor.copy(alpha = 0.45f)),
                        modifier = Modifier.testTag("connection_status_pill")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusDotColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.connectionStatus.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Permissions Icon Button with alert badge if mic missing
                    IconButton(
                        onClick = onOpenPermissions,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("permissions_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (!uiState.permissions.recordAudioGranted) {
                                    Badge(containerColor = CrimsonError)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = stringResource(R.string.cd_permissions),
                                tint = if (uiState.permissions.recordAudioGranted) NeonCyan else CrimsonError
                            )
                        }
                    }

                    // Camera & Photo Vision Icon Button
                    IconButton(
                        onClick = onOpenVision,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("vision_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoCamera,
                            contentDescription = stringResource(R.string.cd_vision),
                            tint = NeonViolet
                        )
                    }

                    // Settings Icon Button
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.cd_settings),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Scrollable middle stage so smaller screens never clip
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Personal Companion for ${uiState.settings.ownerName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = LavenderMuted
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    BrstiOrbAvatar(
                        mood = uiState.mood,
                        emotionTone = uiState.emotionTone,
                        userAudioLevel = uiState.userAudioLevel,
                        assistantAudioLevel = uiState.assistantAudioLevel,
                        onAvatarTap = {
                            if (uiState.isWakeWordStandby && !uiState.isMicActive) {
                                onWakeNow()
                            } else {
                                onToggleMic()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Active Tool Call Chip
                    AnimatedVisibility(visible = uiState.activeToolLabel != null) {
                        Surface(
                            color = ElectricBlue.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = uiState.activeToolLabel.orEmpty(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NeonCyan
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Compact Translucent Live Subtitle Strip (NOT a text-chat wall)
                    if (uiState.settings.liveSubtitlesEnabled) {
                        Surface(
                            color = ObsidianSurface.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, IndigoOutline.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                if (uiState.latestUserText.isNotBlank()) {
                                    Text(
                                        text = "${uiState.settings.ownerName}: ${uiState.latestUserText}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = NeonCyan.copy(alpha = 0.9f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                val brstiDisplay = when {
                                    uiState.latestBrstiText.isNotBlank() -> uiState.latestBrstiText
                                    uiState.isWakeWordStandby -> "\"Hey Brsti\" বলুন অথবা আমাকে ট্যাপ করুন…"
                                    uiState.isMicActive -> "শুনছি ${uiState.settings.ownerName}… বাংলা, English বা हिन्दी-তে কথা বলুন!"
                                    else -> "হেই ${uiState.settings.ownerName}! কথা বলতে মাইক বাটনে ট্যাপ করো!"
                                }
                                Text(
                                    text = brstiDisplay,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Transparent Error or Status Notice Banner
                    val bannerText = uiState.errorMessage ?: uiState.statusBanner
                    AnimatedVisibility(visible = !bannerText.isNullOrBlank()) {
                        val isError = uiState.errorMessage != null
                        Surface(
                            color = if (isError) CrimsonError.copy(alpha = 0.16f) else ObsidianCard.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isError) CrimsonError.copy(alpha = 0.65f) else NeonViolet.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = null,
                                    tint = if (isError) CrimsonError else NeonViolet,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = bannerText.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (!isError) {
                                    IconButton(
                                        onClick = onDismissBanner,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.cd_close),
                                            tint = LavenderMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Staged Owner Confirmation Card (Call / WhatsApp / SMS / Gmail / Camera)
                ConfirmationSheet(
                    confirmation = uiState.pendingConfirmation,
                    onConfirm = onConfirmAction,
                    onCancel = onDismissAction,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Bottom Voice Control Dock
                Surface(
                    color = ObsidianCard.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(32.dp),
                    border = BorderStroke(1.dp, IndigoOutline.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        // Wake Word Standby Toggle
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(onClick = onToggleWakeStandby)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("wake_word_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Hearing,
                                contentDescription = stringResource(R.string.cd_wake_word_toggle),
                                tint = if (uiState.isWakeWordStandby) NeonCyan else LavenderMuted,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (uiState.isWakeWordStandby) "Standby ON" else "Hey Brsti",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (uiState.isWakeWordStandby) NeonCyan else LavenderMuted
                            )
                        }

                        // Primary Glowing Microphone ON/OFF Control
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(82.dp)
                        ) {
                            if (uiState.isMicActive) {
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .scale(micRingScale)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.radialGradient(
                                                listOf(NeonMagenta.copy(alpha = 0.45f), Color.Transparent)
                                            )
                                        )
                                )
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(
                                        brush = if (uiState.isMicActive) {
                                            Brush.linearGradient(listOf(NeonMagenta, NeonViolet))
                                        } else {
                                            Brush.linearGradient(listOf(ElectricBlue, NeonViolet))
                                        }
                                    )
                                    .border(
                                        width = 2.dp,
                                        color = if (uiState.isMicActive) NeonCyan else NeonViolet.copy(alpha = 0.7f),
                                        shape = CircleShape
                                    )
                                    .clickable(onClick = onToggleMic)
                                    .testTag("mic_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (uiState.isMicActive) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                                    contentDescription = stringResource(R.string.cd_mic_toggle),
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        // Quick Prompt / Accessibility Fallback Sheet
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { showPromptSheet = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("text_fallback_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Keyboard,
                                contentDescription = stringResource(R.string.cd_send_prompt),
                                tint = LavenderMuted,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Quick Ask",
                                style = MaterialTheme.typography.labelSmall,
                                color = LavenderMuted
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPromptSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPromptSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = ObsidianSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Quick Voice Starter / Text Prompt",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))

                val starterPrompts = listOf(
                    "হেই বৃষ্টি! কেমন আছো?",
                    "YouTube খোলো",
                    "আমার ফোনের ব্যাটারি কত?",
                    "Dhaka-র আবহাওয়া কেমন?",
                    "125 * 48 + sqrt(144) কত?"
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    starterPrompts.forEach { sample ->
                        AssistChip(
                            onClick = {
                                onSendFallbackPrompt(sample)
                                showPromptSheet = false
                            },
                            label = { Text(sample, style = MaterialTheme.typography.bodyMedium) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = ObsidianCard,
                                labelColor = NeonCyan
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = draftPrompt,
                        onValueChange = { draftPrompt = it },
                        placeholder = { Text(stringResource(R.string.type_fallback_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("fallback_prompt_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (draftPrompt.isNotBlank()) {
                                onSendFallbackPrompt(draftPrompt)
                                draftPrompt = ""
                                showPromptSheet = false
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("send_fallback_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = stringResource(R.string.cd_send_prompt),
                            tint = NeonCyan
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
