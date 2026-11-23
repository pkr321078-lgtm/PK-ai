package com.example.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.JarvisAgent
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald
import com.example.ui.viewmodel.JarvisUiState
import com.example.ui.viewmodel.JarvisViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HubToolsScreen(
    state: JarvisUiState,
    viewModel: JarvisViewModel,
    onNavigateToJarvis: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Vision", "Knowledge", "Automation", "Device & Tools")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Intelligence & Control Hub",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            tabs.forEachIndexed { idx, label ->
                FilterChip(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    label = { Text(label) },
                    modifier = Modifier.testTag("hub_tab_$idx")
                )
            }
        }

        when (selectedTab) {
            0 -> VisionSubTab(state, viewModel, onNavigateToJarvis)
            1 -> KnowledgeSubTab(state, viewModel, onNavigateToJarvis)
            2 -> AutomationSubTab(state, viewModel)
            else -> DeviceAndOfflineToolsSubTab(state, viewModel)
        }
    }
}

@Composable
private fun VisionSubTab(
    state: JarvisUiState,
    viewModel: JarvisViewModel,
    onNavigateToJarvis: () -> Unit
) {
    var selectedUri by rememberSaveable { mutableStateOf<String?>(null) }
    var visionPrompt by rememberSaveable { mutableStateOf("Is photo mein kya hai? Visible text extract karo aur samjhao.") }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri.toString()
            viewModel.attachImage(uri)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("AI Vision, OCR & Screenshot Intelligence", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Uses Android's zero-permission Photo Picker for photos and screenshots. PK JARVIS always asks your explicit permission before sending any image to ${state.activeProvider.displayName}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pick Image / Screenshot")
                        }
                        OutlinedButton(
                            onClick = { viewModel.runDeviceAction { viewModel.deviceControl.openCameraApp() } }
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Open camera app")
                        }
                    }

                    if (selectedUri != null) {
                        AsyncImage(
                            model = Uri.parse(selectedUri),
                            contentDescription = "Selected image for vision analysis",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                    }

                    OutlinedTextField(
                        value = visionPrompt,
                        onValueChange = { visionPrompt = it },
                        label = { Text("Vision Question / OCR Command") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { visionPrompt = "Is image mein jo bhi text likha hai, usko exact OCR extract karo." },
                            modifier = Modifier.weight(1f)
                        ) { Text("OCR Text") }
                        OutlinedButton(
                            onClick = { visionPrompt = "Is screenshot / document ko simple points mein summarize aur explain karo." },
                            modifier = Modifier.weight(1f)
                        ) { Text("Explain Screen") }
                    }

                    Button(
                        onClick = {
                            selectedUri?.let { viewModel.attachImage(Uri.parse(it)) }
                            viewModel.submitMessage(visionPrompt, explicitAgent = JarvisAgent.VISION)
                            onNavigateToJarvis()
                        },
                        enabled = selectedUri != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Analyze with Vision Agent")
                    }
                }
            }
        }
    }
}

@Composable
private fun KnowledgeSubTab(
    state: JarvisUiState,
    viewModel: JarvisViewModel,
    onNavigateToJarvis: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var askQuestion by rememberSaveable { mutableStateOf("") }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importKnowledgeDocument(uri)
    }

    val filteredDocs = state.knowledgeDocs.filter { doc ->
        searchQuery.isBlank() ||
            doc.title.contains(searchQuery, ignoreCase = true) ||
            doc.contentText.contains(searchQuery, ignoreCase = true)
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Knowledge Center (Local Files)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Upload TXT, MD, CSV, JSON, or text PDFs. Files stay private on your device; sending excerpts to your AI provider always requires confirmation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { docPicker.launch(arrayOf("text/*", "application/pdf", "application/json")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Upload Document")
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search saved knowledge") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state.knowledgeDocs.isNotEmpty()) {
                        OutlinedTextField(
                            value = askQuestion,
                            onValueChange = { askQuestion = it },
                            label = { Text("Ask Knowledge Agent about your files") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                val q = askQuestion.ifBlank { "Summarize the key points from my uploaded knowledge documents." }
                                askQuestion = ""
                                viewModel.submitMessage(q, explicitAgent = JarvisAgent.KNOWLEDGE, includeKnowledgeDocs = true)
                                onNavigateToJarvis()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Ask Knowledge Agent")
                        }
                    }
                }
            }
        }

        items(filteredDocs, key = { it.id }) { doc ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(doc.title, style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            text = "${doc.charCount} chars • ${doc.mimeType}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = doc.contentText.take(140),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                    }
                    IconButton(onClick = { viewModel.deleteKnowledgeDoc(doc.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete document", tint = StatusCrimson)
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationSubTab(
    state: JarvisUiState,
    viewModel: JarvisViewModel
) {
    var title by rememberSaveable { mutableStateOf("") }
    var prompt by rememberSaveable { mutableStateOf("") }
    var hourText by rememberSaveable { mutableStateOf("7") }
    var minuteText by rememberSaveable { mutableStateOf("00") }
    var recurring by rememberSaveable { mutableStateOf(true) }
    var requireConfirm by rememberSaveable { mutableStateOf(false) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Smart Automation & Scheduled Reminders", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task / Reminder Title") },
                        placeholder = { Text("Morning briefing & hydration") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("Workflow Action / Message") },
                        placeholder = { Text("Jarvis, roz subah 7 baje yaad dilana") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = hourText,
                            onValueChange = { hourText = it.filter(Char::isDigit).take(2) },
                            label = { Text("Hour (0-23)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minuteText,
                            onValueChange = { minuteText = it.filter(Char::isDigit).take(2) },
                            label = { Text("Minute (0-59)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Repeat daily", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = recurring, onCheckedChange = { recurring = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Require confirmation before run", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = requireConfirm, onCheckedChange = { requireConfirm = it })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val h = hourText.toIntOrNull()?.coerceIn(0, 23) ?: 7
                                val m = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: 0
                                viewModel.createAutomation(title, prompt, h, m, recurring, requireConfirm)
                                title = ""
                                prompt = ""
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                                    !viewModel.deviceControl.hasNotificationPermission()
                                ) {
                                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            enabled = title.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save Schedule")
                        }
                        OutlinedButton(
                            onClick = {
                                val h = hourText.toIntOrNull()?.coerceIn(0, 23) ?: 7
                                val m = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: 0
                                viewModel.runDeviceAction {
                                    viewModel.deviceControl.setSystemAlarm(h, m, title.ifBlank { "PK JARVIS Alarm" })
                                }
                            }
                        ) {
                            Icon(Icons.Default.Alarm, contentDescription = "Set Android system alarm")
                        }
                    }
                }
            }
        }

        items(state.automations, key = { it.id }) { task ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(task.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${task.scheduleLabel} • Status: ${task.status}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (task.status == "ACTIVE") StatusEmerald else StatusAmber
                            )
                        }
                        Row {
                            IconButton(onClick = { viewModel.runAutomationNow(task) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Run automation now", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { viewModel.toggleAutomationStatus(task) }) {
                                Icon(
                                    if (task.status == "ACTIVE") Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Pause or resume automation"
                                )
                            }
                            IconButton(onClick = { viewModel.deleteAutomation(task.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete automation", tint = StatusCrimson)
                            }
                        }
                    }
                    Text(task.lastResult, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceAndOfflineToolsSubTab(
    state: JarvisUiState,
    viewModel: JarvisViewModel
) {
    // Offline Calculator state
    var calcA by rememberSaveable { mutableStateOf("") }
    var calcB by rememberSaveable { mutableStateOf("") }
    var calcResult by rememberSaveable { mutableStateOf("") }

    // Offline Unit Converter (km <-> miles, C <-> F, kg <-> lb)
    var unitInput by rememberSaveable { mutableStateOf("10") }
    var unitMode by rememberSaveable { mutableIntStateOf(0) }

    // Offline Stopwatch
    var stopwatchRunning by rememberSaveable { mutableStateOf(false) }
    var elapsedSeconds by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(stopwatchRunning) {
        while (stopwatchRunning) {
            delay(1000L)
            elapsedSeconds++
        }
    }

    // Emergency Contact confirmation dialog
    var emergencyName by rememberSaveable(state.activeProfile.id) { mutableStateOf(state.activeProfile.emergencyContactName) }
    var emergencyPhone by rememberSaveable(state.activeProfile.id) { mutableStateOf(state.activeProfile.emergencyContactPhone) }
    var showEmergencyDialConfirm by rememberSaveable { mutableStateOf(false) }

    if (showEmergencyDialConfirm) {
        AlertDialog(
            onDismissRequest = { showEmergencyDialConfirm = false },
            title = { Text("Confirm Emergency Dialer Shortcut") },
            text = { Text("Open Android Phone Dialer with $emergencyName ($emergencyPhone)? PK JARVIS never places calls silently without your confirmation.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCrimson),
                    onClick = {
                        showEmergencyDialConfirm = false
                        viewModel.runDeviceAction {
                            viewModel.deviceControl.openDialerWithNumber(emergencyPhone)
                        }
                    }
                ) { Text("Open Dialer") }
            },
            dismissButton = {
                TextButton(onClick = { showEmergencyDialConfirm = false }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Native Android Device Controls
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Native Android Device Control", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = { viewModel.toggleFlashlight() }) {
                            Icon(
                                if (state.torchOn) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.torchOn) "Flashlight OFF" else "Flashlight ON")
                        }
                        OutlinedButton(onClick = {
                            viewModel.runDeviceAction {
                                viewModel.deviceControl.openInstalledApp(
                                    "com.google.android.youtube",
                                    "YouTube",
                                    "https://www.youtube.com"
                                )
                            }
                        }) { Text("YouTube") }
                        OutlinedButton(onClick = {
                            viewModel.runDeviceAction {
                                viewModel.deviceControl.openInstalledApp(
                                    "com.whatsapp",
                                    "WhatsApp",
                                    "https://web.whatsapp.com"
                                )
                            }
                        }) { Text("WhatsApp") }
                        OutlinedButton(onClick = {
                            viewModel.runDeviceAction { viewModel.deviceControl.openCameraApp() }
                        }) { Text("Camera") }
                        OutlinedButton(onClick = {
                            viewModel.runDeviceAction {
                                viewModel.deviceControl.setSystemAlarm(7, 0, "PK JARVIS Morning Alarm")
                            }
                        }) { Text("Set Alarm") }
                        OutlinedButton(onClick = {
                            viewModel.runDeviceAction {
                                viewModel.deviceControl.shareText(
                                    "PK JARVIS Note",
                                    state.messages.lastOrNull()?.content ?: "Shared from PK JARVIS"
                                )
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Share Latest")
                        }
                    }
                }
            }
        }

        // Emergency Assistance Module
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, StatusCrimson.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = StatusCrimson)
                        Text("Emergency Assistance", style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedTextField(
                        value = emergencyName,
                        onValueChange = { emergencyName = it },
                        label = { Text("Emergency Contact Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = emergencyPhone,
                        onValueChange = { emergencyPhone = it },
                        label = { Text("Phone Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.saveEmergencyContact(emergencyName, emergencyPhone) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Save Contact") }
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = StatusCrimson),
                            enabled = emergencyPhone.isNotBlank(),
                            onClick = { showEmergencyDialConfirm = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Dial Shortcut")
                        }
                    }
                }
            }
        }

        // Built-in Offline Utility Tools (Calculator, Unit Converter, Stopwatch)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Offline Daily Tools", style = MaterialTheme.typography.titleMedium)
                    }

                    // Quick Calculator
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = calcA,
                            onValueChange = { calcA = it },
                            label = { Text("Value A") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = calcB,
                            onValueChange = { calcB = it },
                            label = { Text("Value B") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("+", "-", "×", "÷").forEach { op ->
                            OutlinedButton(
                                onClick = {
                                    val a = calcA.toDoubleOrNull()
                                    val b = calcB.toDoubleOrNull()
                                    calcResult = if (a == null || b == null) {
                                        "Enter valid numbers"
                                    } else when (op) {
                                        "+" -> "= ${a + b}"
                                        "-" -> "= ${a - b}"
                                        "×" -> "= ${a * b}"
                                        else -> if (b == 0.0) "Cannot divide by zero" else "= ${"%.4f".format(a / b)}"
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text(op) }
                        }
                    }
                    if (calcResult.isNotBlank()) {
                        Text(calcResult, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }

                    // Unit Converter
                    val valNum = unitInput.toDoubleOrNull() ?: 0.0
                    val converted = when (unitMode) {
                        0 -> "${"%.2f".format(valNum)} km = ${"%.2f".format(valNum * 0.621371)} miles"
                        1 -> "${"%.1f".format(valNum)} °C = ${"%.1f".format(valNum * 9.0 / 5.0 + 32.0)} °F"
                        else -> "${"%.2f".format(valNum)} kg = ${"%.2f".format(valNum * 2.20462)} lb"
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = unitInput,
                            onValueChange = { unitInput = it },
                            label = { Text("Convert") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = { unitMode = (unitMode + 1) % 3 }) {
                            Text(when (unitMode) { 0 -> "km→mi"; 1 -> "°C→°F"; else -> "kg→lb" })
                        }
                    }
                    Text(converted, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)

                    // Stopwatch
                    val mins = elapsedSeconds / 60
                    val secs = elapsedSeconds % 60
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Stopwatch: %02d:%02d".format(mins, secs),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(onClick = { stopwatchRunning = !stopwatchRunning }) {
                                    Text(if (stopwatchRunning) "Pause" else "Start")
                                }
                                OutlinedButton(onClick = {
                                    stopwatchRunning = false
                                    elapsedSeconds = 0L
                                }) { Text("Reset") }
                            }
                        }
                    }
                }
            }
        }
    }
}
