package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.AppLanguage
import com.example.domain.model.DiagnosticStatus
import com.example.domain.model.ResponseMode
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald
import com.example.ui.viewmodel.JarvisUiState
import com.example.ui.viewmodel.JarvisViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemDiagnosticsScreen(
    state: JarvisUiState,
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    var newProfileName by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (state.diagnostics.isEmpty()) {
            viewModel.runFullDiagnostics()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Profiles, Preferences & Diagnostics", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "Zero mandatory accounts. Each local profile maintains isolated encrypted API keys, voices, memory, and chat history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Multi-User Local Profiles (Strict Credential Isolation)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Local User Profiles (Isolated Vaults)", style = MaterialTheme.typography.titleMedium)

                    state.allProfiles.forEach { profile ->
                        val isCurrent = profile.id == state.activeProfile.id
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                1.dp,
                                if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(profile.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        if (isCurrent) "Active profile • Separate encrypted key vault" else "Tap Switch to isolate credentials",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!isCurrent) {
                                        Button(
                                            onClick = { viewModel.switchProfile(profile.id) },
                                            modifier = Modifier.testTag("switch_profile_${profile.id}")
                                        ) { Text("Switch") }
                                    }
                                    if (state.allProfiles.size > 1) {
                                        IconButton(onClick = { viewModel.deleteProfile(profile.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete profile", tint = StatusCrimson)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newProfileName,
                            onValueChange = { newProfileName = it },
                            label = { Text("New Local Profile Name") },
                            placeholder = { Text("e.g., Family Member / Work") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("new_profile_name_input")
                        )
                        Button(
                            onClick = {
                                viewModel.createProfile(newProfileName)
                                newProfileName = ""
                            },
                            enabled = newProfileName.isNotBlank(),
                            modifier = Modifier.testTag("create_profile_button")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Create profile")
                        }
                    }
                }
            }
        }

        // Personalization & Voice Preferences
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Personalization & Assistant Controls", style = MaterialTheme.typography.titleMedium)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Dark Obsidian HUD Theme", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = state.activeProfile.darkTheme,
                            onCheckedChange = { viewModel.setDarkTheme(it) },
                            modifier = Modifier.testTag("theme_dark_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Continuous Voice Conversation", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Auto-listens after PK JARVIS finishes speaking while the app is open on screen. Never records secretly in the background.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.activeProfile.continuousVoiceMode,
                            onCheckedChange = { viewModel.toggleContinuousVoice() }
                        )
                    }

                    Text("Language", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = state.activeProfile.languageCode == lang.code,
                                onClick = { viewModel.setLanguage(lang) },
                                label = { Text(lang.displayName) }
                            )
                        }
                    }

                    Text("Response Depth", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ResponseMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.activeProfile.responseModeId == mode.id,
                                onClick = { viewModel.setResponseMode(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }

                    Text("Preferred Wake Phrase (Foreground Only)", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Hey Jarvis", "Hey PK").forEach { phrase ->
                            FilterChip(
                                selected = state.activeProfile.wakeWord == phrase,
                                onClick = { viewModel.setWakeWord(phrase) },
                                label = { Text(phrase) }
                            )
                        }
                    }
                    Text(
                        text = "Honest platform note: Always-on screen-off wake-word detection requires a dedicated foreground audio service and wake-word DSP engine. To respect battery and privacy, PK JARVIS activates voice listening only when you tap the mic or orb.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Live Diagnostics Suite + Hindi Troubleshooting
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Live Diagnostics", style = MaterialTheme.typography.titleLarge)
                }
                Button(
                    onClick = { viewModel.runFullDiagnostics() },
                    enabled = !state.isRunningDiagnostics,
                    modifier = Modifier.testTag("run_diagnostics_button")
                ) {
                    if (state.isRunningDiagnostics) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(6.dp))
                        Text("Checking…")
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Run All Tests")
                    }
                }
            }
        }

        items(state.diagnostics, key = { it.id }) { diag ->
            val accent = when (diag.status) {
                DiagnosticStatus.WORKING, DiagnosticStatus.CONNECTED -> StatusEmerald
                DiagnosticStatus.PERMISSION_REQUIRED, DiagnosticStatus.NOT_CONFIGURED -> StatusAmber
                DiagnosticStatus.UNSUPPORTED, DiagnosticStatus.FAILED -> StatusCrimson
            }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(diag.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(diag.status.label, style = MaterialTheme.typography.labelMedium, color = accent)
                    }
                    Text(diag.detail, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "हिन्दी सुझाव: ${diag.hindiTip}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
