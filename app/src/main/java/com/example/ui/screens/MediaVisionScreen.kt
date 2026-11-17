package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.ConfirmationSheet
import com.example.ui.state.BrstiUiState
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.IndigoOutline
import com.example.ui.theme.LavenderMuted
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianDeep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaVisionScreen(
    uiState: BrstiUiState,
    onBack: () -> Unit,
    onPickPhoto: () -> Unit,
    onRequestCameraCapture: () -> Unit,
    onConfirmAction: () -> Unit,
    onDismissAction: () -> Unit,
    onAnalyzePhoto: (String) -> Unit
) {
    var question by remember { mutableStateOf("") }

    Scaffold(
        containerColor = ObsidianDeep,
        topBar = {
            TopAppBar(
                title = { Text("Photo & Camera Vision") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("vision_back_button")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Select a photo using Android's zero-permission Photo Picker or capture one with your approval so BRSTI can analyze it with Gemini.",
                style = MaterialTheme.typography.bodyMedium,
                color = LavenderMuted
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onPickPhoto,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ObsidianDeep),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("pick_photo_button")
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pick Photo")
                }
                OutlinedButton(
                    onClick = onRequestCameraCapture,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("capture_photo_button")
                ) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Camera")
                }
            }

            ConfirmationSheet(
                confirmation = uiState.pendingConfirmation,
                onConfirm = onConfirmAction,
                onCancel = onDismissAction
            )

            val bitmap = uiState.selectedVisionBitmap
            if (bitmap != null) {
                Surface(
                    color = ObsidianCard,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, NeonViolet.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Selected photo for BRSTI analysis",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = question,
                            onValueChange = { question = it },
                            label = { Text("Ask BRSTI about this photo (বাংলা / EN / HI)") },
                            placeholder = { Text("এই ছবিতে কী দেখা যাচ্ছে?") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vision_question_input")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { onAnalyzePhoto(question) },
                            enabled = !uiState.isAnalyzingVision,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonViolet, contentColor = ObsidianDeep),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("analyze_vision_button")
                        ) {
                            if (uiState.isAnalyzingVision) {
                                CircularProgressIndicator(
                                    color = ObsidianDeep,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.height(20.dp).width(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyzing with Gemini...")
                            } else {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyze Photo")
                            }
                        }
                    }
                }
            } else {
                Surface(
                    color = ObsidianCard.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, IndigoOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            tint = NeonViolet,
                            modifier = Modifier.height(40.dp).width(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Photo Selected",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap Pick Photo or Camera above to let BRSTI explain what's in your picture.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LavenderMuted
                        )
                    }
                }
            }

            if (!uiState.visionError.isNullOrBlank()) {
                Surface(
                    color = CrimsonError.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CrimsonError),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.visionError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            if (!uiState.visionAnalysisResult.isNullOrBlank()) {
                Surface(
                    color = ObsidianCard,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vision_result_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "BRSTI's Visual Insight",
                            style = MaterialTheme.typography.titleMedium,
                            color = NeonCyan
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.visionAnalysisResult,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
