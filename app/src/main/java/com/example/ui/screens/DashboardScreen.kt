package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.ChatMessageEntity
import com.example.domain.model.ConnectionStateCode
import com.example.domain.model.JarvisAgent
import com.example.domain.model.JarvisState
import com.example.ui.components.ConnectionStatusBadge
import com.example.ui.components.JarvisOrbVisualizer
import com.example.ui.components.LiveAudioWaveform
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCrimson
import com.example.ui.viewmodel.JarvisUiState
import com.example.ui.viewmodel.JarvisViewModel

@Composable
fun DashboardScreen(
    state: JarvisUiState,
    viewModel: JarvisViewModel,
    onNavigateToApiVault: () -> Unit,
    onNavigateToSystem: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.attachImage(uri)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceListening()
        } else {
            viewModel.runDeviceAction {
                com.example.service.DeviceActionResult(
                    false,
                    "Microphone permission denied. You can still type messages normally."
                )
            }
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    // Explicit permission dialog before sending an image or private knowledge docs to external AI provider
    val pendingConsent = state.pendingConsent
    if (pendingConsent != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPendingExternalSend() },
            title = { Text("Confirm External AI Transfer") },
            text = {
                Text(
                    buildString {
                        append("You are about to send ")
                        if (pendingConsent.imageUri != null) append("an image ")
                        if (pendingConsent.includeKnowledgeDocs) {
                            if (pendingConsent.imageUri != null) append("and ")
                            append("saved Knowledge Center documents ")
                        }
                        append("to your configured provider (${state.activeProvider.displayName}) over HTTPS. ")
                        append("Continue?")
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmPendingExternalSend() },
                    modifier = Modifier.testTag("confirm_external_send_button")
                ) {
                    Text("Send Securely")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPendingExternalSend() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Offline alert banner
        AnimatedVisibility(visible = !state.isOnline) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(14.dp),
                color = StatusAmber.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.WifiOff, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Offline mode — Local notes, cached history & tools work. Cloud AI requires internet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Top HUD Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PK JARVIS",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "YOUR PERSONAL INTELLIGENCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clickable { onNavigateToSystem() }
                        .testTag("profile_chip_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = stringResource(R.string.cd_switch_profile),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = state.activeProfile.name,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 90.dp)
                        )
                    }
                }
                ConnectionStatusBadge(
                    status = state.activeConnectionStatus,
                    modifier = Modifier
                        .clickable { onNavigateToApiVault() }
                        .testTag("header_connection_badge")
                )
            }
        }

        // Futuristic Neural Core Card with Hero Art + Animated Orb + Waveform
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(id = R.drawable.img_jarvis_core_banner_1795411427764),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0.30f)
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${state.activeProvider.displayName}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = state.activeProviderConfig?.selectedModel?.ifBlank {
                                    state.activeProvider.defaultModels.first()
                                } ?: "Configure your personal API key",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Agent: ${state.activeAgent.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                IconButton(
                                    onClick = { viewModel.toggleVoiceOutput() },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .testTag("voice_output_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (state.activeProfile.voiceOutputEnabled) {
                                            Icons.AutoMirrored.Filled.VolumeUp
                                        } else {
                                            Icons.AutoMirrored.Filled.VolumeOff
                                        },
                                        contentDescription = stringResource(R.string.cd_voice_toggle),
                                        tint = if (state.activeProfile.voiceOutputEnabled) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.stopAllVoiceAndGeneration() },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (state.isSpeaking || state.isListening || state.jarvisState == JarvisState.THINKING) {
                                                StatusCrimson.copy(alpha = 0.22f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        )
                                        .testTag("stop_jarvis_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = stringResource(R.string.cd_stop_button),
                                        tint = if (state.isSpeaking || state.isListening) StatusCrimson else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (state.activeConnectionStatus != ConnectionStateCode.CONNECTED) {
                                    OutlinedButton(
                                        onClick = onNavigateToApiVault,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier
                                            .height(40.dp)
                                            .testTag("setup_api_cta_button")
                                    ) {
                                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Connect Key", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }

                        JarvisOrbVisualizer(
                            jarvisState = state.jarvisState,
                            audioLevel = state.audioLevel,
                            size = 112.dp,
                            onClick = {
                                if (state.isListening) {
                                    viewModel.stopAllVoiceAndGeneration()
                                } else if (viewModel.deviceControl.hasRecordAudioPermission()) {
                                    viewModel.startVoiceListening()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    LiveAudioWaveform(
                        jarvisState = state.jarvisState,
                        audioLevel = state.audioLevel
                    )
                    if (state.partialTranscript.isNotBlank()) {
                        Text(
                            text = "Hearing: \"${state.partialTranscript}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2
                        )
                    }
                }
            }
        }

        // 10-Agent Horizontal Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = state.manualAgentOverride == null,
                onClick = { viewModel.setManualAgent(null) },
                label = { Text("Auto Route") },
                leadingIcon = {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )
            JarvisAgent.entries.forEach { agent ->
                FilterChip(
                    selected = state.manualAgentOverride == agent,
                    onClick = { viewModel.setManualAgent(agent) },
                    label = { Text(agent.shortTitle) }
                )
            }
        }

        // Conversation Stream or Empty Onboarding Quick Prompts
        if (state.messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = if (state.activeConnectionStatus == ConnectionStateCode.CONNECTED) {
                            "PK JARVIS Brain Active"
                        } else {
                            "Connect Your Personal API Key"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (state.activeConnectionStatus == ConnectionStateCode.CONNECTED) {
                            "Speak or type in English, Hindi, or Hinglish. All 10 specialist agents are ready."
                        } else {
                            "Zero mandatory login. Add your own Gemini, OpenAI, Claude, Groq, or OpenRouter key in API Vault — encrypted with Android Keystore AES-GCM."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val quickPrompts = listOf(
                            "Aaj ka productive daily plan banao",
                            "Jarvis, mujhe roz subah 7 baje yaad dilana",
                            "Research latest Android Keystore best practices",
                            "Write a clean Kotlin coroutine retry helper"
                        )
                        quickPrompts.forEach { sample ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.clickable {
                                    inputText = sample
                                }
                            ) {
                                Text(
                                    text = sample,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(state.messages, key = { it.id }) { msg ->
                    ChatBubbleCard(
                        message = msg,
                        onSpeak = { viewModel.speakResponse(msg.content) },
                        onCopy = {
                            viewModel.runDeviceAction {
                                viewModel.deviceControl.copyToClipboard("PK JARVIS Message", msg.content)
                            }
                        }
                    )
                }
            }
        }

        // Attached Vision Image Preview
        AnimatedVisibility(visible = state.pendingImageUri != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AsyncImage(
                        model = state.pendingImageUri,
                        contentDescription = "Selected vision photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Column {
                        Text("Vision image attached", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Will ask confirmation before sending to ${state.activeProvider.shortName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { viewModel.attachImage(null) }) {
                    Icon(Icons.Default.Close, contentDescription = "Remove attached image")
                }
            }
        }

        // Command Input Dock
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .testTag("attach_vision_image_button")
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = stringResource(R.string.cd_attach_image),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Ask PK JARVIS (EN / HI / Hinglish)…") },
                singleLine = false,
                maxLines = 3,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_prompt_input")
            )

            IconButton(
                onClick = {
                    if (state.isListening) {
                        viewModel.stopAllVoiceAndGeneration()
                    } else if (viewModel.deviceControl.hasRecordAudioPermission()) {
                        viewModel.startVoiceListening()
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (state.isListening) StatusCrimson else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .testTag("mic_voice_button")
            ) {
                Icon(
                    imageVector = if (state.isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = stringResource(R.string.cd_mic_button),
                    tint = if (state.isListening) Color.White else MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = {
                    val toSend = inputText
                    inputText = ""
                    viewModel.submitMessage(toSend)
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .testTag("send_message_button")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.cd_send_message),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun ChatBubbleCard(
    message: ChatMessageEntity,
    onSpeak: () -> Unit,
    onCopy: () -> Unit
) {
    val isUser = message.role == "user"
    val agent = JarvisAgent.fromId(message.agentId)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 520.dp),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            border = BorderStroke(
                1.dp,
                if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.outline
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "You" else "PK JARVIS • ${agent.shortTitle}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                    )
                    if (!isUser && message.modelId.isNotBlank()) {
                        Text(
                            text = message.modelId,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                if (!isUser) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = onSpeak, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Read response aloud",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy response",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
