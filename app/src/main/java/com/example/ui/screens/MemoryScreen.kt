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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.theme.StatusCrimson
import com.example.ui.viewmodel.JarvisUiState
import com.example.ui.viewmodel.JarvisViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryScreen(
    state: JarvisUiState,
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var titleInput by rememberSaveable { mutableStateOf("") }
    var contentInput by rememberSaveable { mutableStateOf("") }
    var categoryInput by rememberSaveable { mutableStateOf("preference") }
    var editingId by rememberSaveable { mutableLongStateOf(0L) }
    var customInstructions by rememberSaveable(state.activeProfile.id) {
        mutableStateOf(state.activeProfile.customInstructions)
    }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var importJsonText by rememberSaveable { mutableStateOf("") }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Memory JSON") },
            text = {
                OutlinedTextField(
                    value = importJsonText,
                    onValueChange = { importJsonText = it },
                    label = { Text("Paste exported PK JARVIS JSON") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.importMemoriesFromJson(importJsonText)
                    importJsonText = ""
                    showImportDialog = false
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            }
        )
    }

    val filtered = state.memories.filter { item ->
        searchQuery.isBlank() ||
            item.title.contains(searchQuery, ignoreCase = true) ||
            item.content.contains(searchQuery, ignoreCase = true)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Smart Memory & Notes", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "Stored 100% locally on this device for \"${state.activeProfile.name}\". Nothing is saved silently — only entries you approve are kept.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Custom AI Instructions
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Custom Assistant Instructions", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = customInstructions,
                        onValueChange = { customInstructions = it },
                        label = { Text("How should PK JARVIS address & assist you?") },
                        placeholder = { Text("e.g., Address me as PK, prefer Hinglish bullets, highlight action items") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = { viewModel.setCustomInstructions(customInstructions) }) {
                        Text("Save Custom Instructions")
                    }
                }
            }
        }

        // Add / Edit User-Approved Memory
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (editingId == 0L) "Add Memory / Saved Note" else "Edit Memory Entry",
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = contentInput,
                        onValueChange = { contentInput = it },
                        label = { Text("Details (never store passwords or API keys here)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("preference", "fact", "note", "instruction").forEach { cat ->
                            FilterChip(
                                selected = categoryInput == cat,
                                onClick = { categoryInput = cat },
                                label = { Text(cat.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.saveMemory(titleInput, contentInput, categoryInput, editingId)
                                titleInput = ""
                                contentInput = ""
                                editingId = 0L
                            },
                            enabled = titleInput.isNotBlank() && contentInput.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (editingId == 0L) "Save Memory" else "Update Memory")
                        }
                        if (editingId != 0L) {
                            TextButton(onClick = {
                                editingId = 0L
                                titleInput = ""
                                contentInput = ""
                            }) { Text("Cancel") }
                        }
                    }
                }
            }
        }

        // Search, Export, Import, Clear Chat
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search memories & notes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.exportMemoriesToClipboard() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Export JSON")
                    }
                    OutlinedButton(
                        onClick = { showImportDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Import JSON")
                    }
                }
                OutlinedButton(
                    onClick = { viewModel.clearChatHistory() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusCrimson),
                    border = BorderStroke(1.dp, StatusCrimson.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clear Conversation History (${state.messages.size} messages)")
                }
            }
        }

        items(filtered, key = { it.id }) { mem ->
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
                        Text(mem.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            mem.category.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(mem.content, style = MaterialTheme.typography.bodySmall)
                    }
                    Row {
                        IconButton(onClick = {
                            editingId = mem.id
                            titleInput = mem.title
                            contentInput = mem.content
                            categoryInput = mem.category
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit memory")
                        }
                        IconButton(onClick = { viewModel.deleteMemory(mem.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete memory", tint = StatusCrimson)
                        }
                    }
                }
            }
        }
    }
}
