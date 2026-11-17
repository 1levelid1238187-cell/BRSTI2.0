package com.example

import android.Manifest
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ai.configureAppCheck
import com.example.ui.screens.MediaVisionScreen
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VoiceStageScreen
import com.example.ui.theme.BrstiTheme
import com.example.ui.viewmodel.BrstiNavigationEvent
import com.example.ui.viewmodel.BrstiViewModel
import java.io.File

private const val ROUTE_VOICE_STAGE = "voice_stage"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_PERMISSIONS = "permissions"
private const val ROUTE_VISION = "media_vision"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI, ViewModels, or Firebase operations.
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            BrstiTheme {
                BrstiAppNavigation()
            }
        }
    }
}

@Composable
private fun BrstiAppNavigation(
    viewModel: BrstiViewModel = viewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val singlePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.refreshPermissions()
    }

    val micAndNotifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.refreshPermissions()
        if (results[Manifest.permission.RECORD_AUDIO] == true) {
            viewModel.toggleMicrophone()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bitmap = decodeUriToBitmap(context, uri)
            viewModel.setSelectedVisionBitmap(bitmap, autoQuestion = "Describe what you see in this photo.")
            if (navController.currentDestination?.route != ROUTE_VISION) {
                navController.navigate(ROUTE_VISION)
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            val bitmap = decodeUriToBitmap(context, uri)
            viewModel.setSelectedVisionBitmap(bitmap, autoQuestion = "Explain what is visible in this captured photo.")
            if (navController.currentDestination?.route != ROUTE_VISION) {
                navController.navigate(ROUTE_VISION)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                BrstiNavigationEvent.OpenMediaVision -> {
                    navController.navigate(ROUTE_VISION)
                }
                BrstiNavigationEvent.RequestMicPermission -> {
                    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        arrayOf(Manifest.permission.RECORD_AUDIO)
                    }
                    micAndNotifLauncher.launch(perms)
                }
                is BrstiNavigationEvent.RequestPermissionHint -> {
                    val perm = when (event.permissionName) {
                        "READ_CONTACTS" -> Manifest.permission.READ_CONTACTS
                        "CALL_PHONE" -> Manifest.permission.CALL_PHONE
                        else -> Manifest.permission.RECORD_AUDIO
                    }
                    singlePermissionLauncher.launch(perm)
                }
                BrstiNavigationEvent.LaunchCameraCapture -> {
                    val photoDir = File(context.cacheDir, "brsti_photos").apply { mkdirs() }
                    val file = File(photoDir, "capture_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    pendingCameraUri = uri
                    takePictureLauncher.launch(uri)
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_VOICE_STAGE
    ) {
        composable(ROUTE_VOICE_STAGE) {
            VoiceStageScreen(
                uiState = uiState,
                onToggleMic = viewModel::toggleMicrophone,
                onToggleWakeStandby = viewModel::toggleWakeWordStandby,
                onWakeNow = viewModel::triggerWakeWordManually,
                onConfirmAction = viewModel::confirmPendingAction,
                onDismissAction = viewModel::dismissPendingAction,
                onDismissBanner = viewModel::clearStatusBanner,
                onSendFallbackPrompt = viewModel::sendFallbackPrompt,
                onOpenPermissions = { navController.navigate(ROUTE_PERMISSIONS) },
                onOpenVision = { navController.navigate(ROUTE_VISION) },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) }
            )
        }

        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onUpdateOwnerName = viewModel::updateOwnerName,
                onUpdateVoice = viewModel::updateVoice,
                onUpdateLanguage = viewModel::updateLanguage,
                onUpdateWakeWord = viewModel::updateWakeWordEnabled,
                onUpdateMemoryConsent = viewModel::updateMemoryConsent,
                onUpdateLiveSubtitles = viewModel::updateLiveSubtitles,
                onDeleteMemory = viewModel::deleteMemory,
                onClearMemories = viewModel::clearMemories,
                onToggleReminder = viewModel::toggleReminder,
                onDeleteReminder = viewModel::deleteReminder
            )
        }

        composable(ROUTE_PERMISSIONS) {
            PermissionsScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onRequestPermission = { perm -> singlePermissionLauncher.launch(perm) }
            )
        }

        composable(ROUTE_VISION) {
            MediaVisionScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onPickPhoto = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onRequestCameraCapture = {
                    viewModel.requestCameraWithApproval()
                },
                onConfirmAction = viewModel::confirmPendingAction,
                onDismissAction = viewModel::dismissPendingAction,
                onAnalyzePhoto = viewModel::analyzeSelectedVision
            )
        }
    }
}

private fun decodeUriToBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }.getOrNull()
}
