package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AiProvider
import com.example.domain.model.AppLanguage
import com.example.domain.model.ConnectionStateCode
import com.example.domain.model.ElevenLabsVoice
import com.example.ui.components.ConnectionStatusBadge
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald
import com.example.ui.viewmodel.JarvisUiState
import com.example.ui.viewmodel.JarvisViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ApiVaultScreen(
    state: JarvisUiState,
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val provider = state.selectedVaultProvider
    val config = state.selectedVaultConfig
    val statusCode = config?.let { ConnectionStateCode.fromName(it.connectionStatus) }
        ?: ConnectionStateCode.NOT_CONFIGURED
    val hasSavedKey = !config?.maskedKey.isNullOrBlank()

    var apiKeyInput by rememberSaveable(provider.id) { mutableStateOf("") }
    var showKeyInput by rememberSaveable(provider.id) { mutableStateOf(false) }
    var isEditingKey by rememberSaveable(provider.id) { mutableStateOf(false) }
    var customBaseUrlInput by rememberSaveable(provider.id) {
        mutableStateOf(config?.customBaseUrl?.ifBlank { provider.defaultBaseUrl } ?: provider.defaultBaseUrl)
    }
    var modelMenuExpanded by rememberSaveable(provider.id) { mutableStateOf(false) }
    var confirmDeleteProvider by rememberSaveable { mutableStateOf(false) }

    // ElevenLabs fields
    var elevenKeyInput by rememberSaveable { mutableStateOf("") }
    var showElevenKey by rememberSaveable { mutableStateOf(false) }
    var isEditingElevenKey by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteEleven by rememberSaveable { mutableStateOf(false) }

    // Proxy & Search fields
    var proxyUrlInput by rememberSaveable { mutableStateOf(state.activeProfile.secureProxyUrl) }
    var searchKeyInput by rememberSaveable { mutableStateOf("") }
    var showSearchKey by rememberSaveable { mutableStateOf(false) }

    var speedSlider by rememberSaveable(state.activeProfile.id) { mutableFloatStateOf(state.activeProfile.voiceSpeed) }
    var stabilitySlider by rememberSaveable(state.activeProfile.id) { mutableFloatStateOf(state.activeProfile.voiceStability) }

    LaunchedEffect(config?.customBaseUrl, provider.id) {
        if (!config?.customBaseUrl.isNullOrBlank()) {
            customBaseUrlInput = config!!.customBaseUrl
        } else {
            customBaseUrlInput = provider.defaultBaseUrl
        }
    }

    if (confirmDeleteProvider) {
        AlertDialog(
            onDismissRequest = { confirmDeleteProvider = false },
            title = { Text("Delete ${provider.displayName} API Key?") },
            text = { Text("The encrypted key file will be zero-wiped and removed from Android Keystore storage. Deleted credentials cannot be reused.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCrimson),
                    onClick = {
                        confirmDeleteProvider = false
                        apiKeyInput = ""
                        isEditingKey = false
                        viewModel.deleteApiKey(provider)
                    },
                    modifier = Modifier.testTag("confirm_delete_key_button")
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteProvider = false }) { Text("Cancel") }
            }
        )
    }

    if (confirmDeleteEleven) {
        AlertDialog(
            onDismissRequest = { confirmDeleteEleven = false },
            title = { Text("Delete ElevenLabs API Key?") },
            text = { Text("Your ElevenLabs key will be permanently wiped. Android device speech fallback will still work.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCrimson),
                    onClick = {
                        confirmDeleteEleven = false
                        elevenKeyInput = ""
                        isEditingElevenKey = false
                        viewModel.deleteElevenLabsKey()
                    }
                ) {
                    Text("Delete Key")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteEleven = false }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Personal API Key Vault",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Profile: ${state.activeProfile.name} • Bring Your Own Key (no developer keys, no mandatory login). Cloud providers may bill your account directly for usage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Provider Selector
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("1. Select AI Provider", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AiProvider.entries.forEach { item ->
                            val itemCfg = state.providerConfigs[item]
                            val itemConnected = itemCfg?.connectionStatus == ConnectionStateCode.CONNECTED.name
                            FilterChip(
                                selected = provider == item,
                                onClick = {
                                    isEditingKey = false
                                    apiKeyInput = ""
                                    viewModel.selectVaultProvider(item)
                                },
                                label = { Text(item.shortName) },
                                leadingIcon = if (itemConnected) {
                                    {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Connected",
                                            tint = StatusEmerald,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                modifier = Modifier.testTag("provider_chip_${item.id}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(provider.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (state.activeProvider == provider) {
                                    "Currently active on PK JARVIS AI Brain"
                                } else {
                                    "Not active — verify connection or activate below"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.activeProvider == provider) StatusEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (state.activeProvider != provider && statusCode == ConnectionStateCode.CONNECTED) {
                            OutlinedButton(
                                onClick = { viewModel.activateProvider(provider) },
                                modifier = Modifier.testTag("activate_provider_button")
                            ) {
                                Text("Activate")
                            }
                        }
                    }
                }
            }
        }

        // Credentials & Connection Verification Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("2. Encrypted API Key & Verification", style = MaterialTheme.typography.titleMedium)
                        ConnectionStatusBadge(status = statusCode)
                    }

                    // Custom HTTPS Base URL for OpenAI-compatible providers
                    if (provider == AiProvider.CUSTOM_COMPATIBLE) {
                        OutlinedTextField(
                            value = customBaseUrlInput,
                            onValueChange = {
                                customBaseUrlInput = it
                                viewModel.updateCustomBaseUrl(provider, it)
                            },
                            label = { Text("Provider HTTPS Base URL") },
                            placeholder = { Text("https://api.together.xyz/v1") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_base_url_input")
                        )
                    }

                    // Show masked saved key if stored and not currently replacing
                    if (hasSavedKey && !isEditingKey) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Encrypted with Android Keystore AES-256-GCM",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = config?.maskedKey.orEmpty(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = com.example.ui.theme.JetBrainsMonoFontFamily,
                                    modifier = Modifier.testTag("masked_api_key_text")
                                )
                                Text(
                                    text = "Full saved key is never displayed again for security.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            isEditingKey = true
                                            apiKeyInput = ""
                                        },
                                        modifier = Modifier.testTag("update_api_key_button")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.action_update_key))
                                    }
                                    OutlinedButton(
                                        onClick = { confirmDeleteProvider = true },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusCrimson),
                                        border = BorderStroke(1.dp, StatusCrimson.copy(alpha = 0.6f)),
                                        modifier = Modifier.testTag("delete_api_key_button")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.action_delete_key))
                                    }
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text("${provider.displayName} API Key") },
                            placeholder = { Text(provider.keyHint) },
                            singleLine = true,
                            visualTransformation = if (showKeyInput) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { showKeyInput = !showKeyInput },
                                    modifier = Modifier.testTag("toggle_key_visibility_button")
                                ) {
                                    Icon(
                                        imageVector = if (showKeyInput) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = stringResource(R.string.cd_show_hide_key)
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("api_key_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val keyToSave = apiKeyInput
                                    apiKeyInput = ""
                                    showKeyInput = false
                                    isEditingKey = false
                                    viewModel.saveApiKeySecurely(
                                        provider = provider,
                                        rawKey = keyToSave,
                                        customBaseUrl = if (provider == AiProvider.CUSTOM_COMPATIBLE) customBaseUrlInput else null,
                                        autoTestAfterSave = false
                                    )
                                },
                                enabled = apiKeyInput.isNotBlank(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_securely_button")
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.action_save_securely))
                            }
                            if (isEditingKey) {
                                TextButton(onClick = {
                                    isEditingKey = false
                                    apiKeyInput = ""
                                }) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }

                    // Real Connection Test / Retry
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.testProviderConnection(provider) },
                            enabled = hasSavedKey && !state.isTestingProvider,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_connection_button")
                        ) {
                            if (state.isTestingProvider) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Testing…")
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (statusCode == ConnectionStateCode.CONNECTED || statusCode == ConnectionStateCode.SAVED_SECURELY) {
                                        stringResource(R.string.action_test_connection)
                                    } else {
                                        stringResource(R.string.action_retry_connection)
                                    }
                                )
                            }
                        }
                    }

                    // Honest status & telemetry box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = config?.connectionMessage ?: "Not configured. Enter your personal key and tap Save Securely.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("connection_status_detail")
                            )
                            if (!config?.detectedModel.isNullOrBlank()) {
                                Text(
                                    text = "Detected Model: ${config!!.detectedModel}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = StatusEmerald
                                )
                            }
                            if (!config?.usageSummary.isNullOrBlank()) {
                                Text(
                                    text = "Telemetry: ${config!!.usageSummary}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Model Selection (Auto-discovered + Default)
                    val availableModels = state.discoveredModelsFor(provider)
                    val currentModel = config?.selectedModel?.ifBlank { availableModels.first() } ?: availableModels.first()

                    Text("3. Model Selection (Auto-Discovered on Verification)", style = MaterialTheme.typography.titleSmall)
                    ExposedDropdownMenuBox(
                        expanded = modelMenuExpanded,
                        onExpandedChange = { modelMenuExpanded = !modelMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = currentModel,
                            onValueChange = { viewModel.selectModel(provider, it) },
                            label = { Text("Active Model ID") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryEditable)
                                .testTag("model_selector_input")
                        )
                        ExposedDropdownMenu(
                            expanded = modelMenuExpanded,
                            onDismissRequest = { modelMenuExpanded = false }
                        ) {
                            availableModels.forEach { modelName ->
                                DropdownMenuItem(
                                    text = { Text(modelName, style = MaterialTheme.typography.labelMedium) },
                                    onClick = {
                                        viewModel.selectModel(provider, modelName)
                                        modelMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ElevenLabs Personal Two-Voice Studio (George & Sarah)
        item {
            val voiceCfg = state.voiceConfig
            val elStatus = voiceCfg?.let { ConnectionStateCode.fromName(it.status) }
                ?: ConnectionStateCode.NOT_CONFIGURED
            val hasElKey = !voiceCfg?.maskedApiKey.isNullOrBlank()
            val selectedVoice = ElevenLabsVoice.fromVoiceId(state.activeProfile.selectedVoiceId)

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("ElevenLabs Two-Voice Studio", style = MaterialTheme.typography.titleMedium)
                        }
                        ConnectionStatusBadge(status = elStatus)
                    }

                    Text(
                        text = "Uses your own personal ElevenLabs key (eleven_multilingual_v2). Falls back cleanly to Android device Text-to-Speech if not configured.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (hasElKey && !isEditingElevenKey) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        voiceCfg?.maskedApiKey.orEmpty(),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    if (!voiceCfg?.characterQuotaSummary.isNullOrBlank()) {
                                        Text(
                                            voiceCfg!!.characterQuotaSummary,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StatusEmerald
                                        )
                                    }
                                }
                                Row {
                                    TextButton(onClick = { isEditingElevenKey = true }) { Text("Update") }
                                    TextButton(
                                        onClick = { confirmDeleteEleven = true },
                                        colors = ButtonDefaults.textButtonColors(contentColor = StatusCrimson)
                                    ) { Text("Delete") }
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = elevenKeyInput,
                            onValueChange = { elevenKeyInput = it },
                            label = { Text("Personal ElevenLabs API Key") },
                            placeholder = { Text("sk_... or xi_...") },
                            singleLine = true,
                            visualTransformation = if (showElevenKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showElevenKey = !showElevenKey }) {
                                    Icon(
                                        if (showElevenKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle ElevenLabs key visibility"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("elevenlabs_key_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val k = elevenKeyInput
                                    elevenKeyInput = ""
                                    isEditingElevenKey = false
                                    viewModel.saveElevenLabsKey(k)
                                },
                                enabled = elevenKeyInput.isNotBlank(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_elevenlabs_button")
                            ) {
                                Text("Save ElevenLabs Key")
                            }
                            if (isEditingElevenKey) {
                                TextButton(onClick = { isEditingElevenKey = false }) { Text("Cancel") }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.testElevenLabsConnection() },
                        enabled = hasElKey && !state.isTestingElevenLabs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_elevenlabs_button")
                    ) {
                        Text(if (state.isTestingElevenLabs) "Verifying George & Sarah…" else "Verify ElevenLabs Key & Voices")
                    }

                    // Male – George & Female – Sarah Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ElevenLabsVoice.entries.forEach { voice ->
                            val isSelected = selectedVoice == voice
                            val isVerified = when (voice) {
                                ElevenLabsVoice.GEORGE -> voiceCfg?.georgeVerified == true
                                ElevenLabsVoice.SARAH -> voiceCfg?.sarahVerified == true
                            }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.selectVoice(voice) }
                                    .testTag("voice_card_${voice.name.lowercase()}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${voice.genderLabel} – ${voice.displayName}",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = voice.styleSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (isVerified) "Voice ID verified" else "ID: ${voice.voiceId.take(8)}…",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isVerified) StatusEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(
                                        onClick = { viewModel.previewVoice(voice) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Test Voice", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }

                    // Speed & Stability Sliders + Language
                    Text(
                        text = "Speed: ${"%.2f".format(speedSlider)}x",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = speedSlider,
                        onValueChange = { speedSlider = it },
                        onValueChangeFinished = { viewModel.updateVoiceTuning(speedSlider, stabilitySlider) },
                        valueRange = 0.7f..1.2f
                    )
                    Text(
                        text = "Stability: ${"%.2f".format(stabilitySlider)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = stabilitySlider,
                        onValueChange = { stabilitySlider = it },
                        onValueChangeFinished = { viewModel.updateVoiceTuning(speedSlider, stabilitySlider) },
                        valueRange = 0.0f..1.0f
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = state.activeProfile.languageCode == lang.code,
                                onClick = { viewModel.setLanguage(lang) },
                                label = { Text(lang.displayName) }
                            )
                        }
                    }
                }
            }
        }

        // Security Architecture & Optional Secure Backend Proxy
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Security & Optional Backend Proxy", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = StatusEmerald, modifier = Modifier.size(16.dp))
                        Text(
                            text = if (state.keystoreHardwareBacked) {
                                "Hardware-Backed Android Keystore (TEE / StrongBox) Verified"
                            } else {
                                "Android Keystore AES-256-GCM Vault Active"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = StatusEmerald
                        )
                    }
                    Text(
                        text = "Honest Security Notice: Local AES-GCM + Android Keystore encryption protects your API keys at rest from ordinary storage reads and excludes them from Android backups. However, no on-device vault can guarantee protection if the phone itself is rooted/compromised or an attacker inspects process memory while keys are in active use. For maximum isolation, route requests through your own HTTPS Secure Backend Proxy below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Route AI calls via Secure HTTPS Proxy", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = state.activeProfile.useSecureProxy,
                            onCheckedChange = { enabled ->
                                viewModel.updateSecureProxySettings(enabled, proxyUrlInput)
                            }
                        )
                    }

                    OutlinedTextField(
                        value = proxyUrlInput,
                        onValueChange = { proxyUrlInput = it },
                        label = { Text("Optional Secure Backend Proxy URL (HTTPS)") },
                        placeholder = { Text("https://your-proxy.example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = { viewModel.updateSecureProxySettings(state.activeProfile.useSecureProxy, proxyUrlInput) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Proxy Endpoint")
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Optional Personal Tavily Search API key for Research Agent
                    Text("Optional Research Search Key (Tavily)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (state.hasSearchKeySaved) {
                            "Personal Tavily Search key saved securely in Android Keystore."
                        } else {
                            "Optional: Research Agent uses public Wikipedia/DuckDuckGo by default, or add your personal Tavily key for live web indexing."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!state.hasSearchKeySaved) {
                        OutlinedTextField(
                            value = searchKeyInput,
                            onValueChange = { searchKeyInput = it },
                            label = { Text("Tavily API Key (tvly-...)") },
                            singleLine = true,
                            visualTransformation = if (showSearchKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showSearchKey = !showSearchKey }) {
                                    Icon(
                                        if (showSearchKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle search key visibility"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = {
                                val k = searchKeyInput
                                searchKeyInput = ""
                                viewModel.saveSearchApiKey(k)
                            },
                            enabled = searchKeyInput.isNotBlank()
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save Search Key")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.deleteSearchApiKey() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusCrimson)
                        ) {
                            Text("Delete Saved Search Key")
                        }
                    }
                }
            }
        }
    }
}
