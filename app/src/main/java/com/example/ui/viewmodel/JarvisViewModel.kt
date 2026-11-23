package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AutomationTaskEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.JarvisDatabase
import com.example.data.local.KnowledgeDocEntity
import com.example.data.local.MemoryEntryEntity
import com.example.data.local.ProviderConfigEntity
import com.example.data.local.UserProfileEntity
import com.example.data.local.VoiceConfigEntity
import com.example.data.repository.JarvisRepository
import com.example.domain.model.AiProvider
import com.example.domain.model.AppLanguage
import com.example.domain.model.ConnectionStateCode
import com.example.domain.model.DiagnosticItem
import com.example.domain.model.DiagnosticStatus
import com.example.domain.model.ElevenLabsVoice
import com.example.domain.model.JarvisAgent
import com.example.domain.model.JarvisState
import com.example.domain.model.ResponseMode
import com.example.network.AiProviderClient
import com.example.network.ElevenLabsAudioResult
import com.example.network.ElevenLabsClient
import com.example.network.WebResearchClient
import com.example.security.AndroidKeystoreCryptoProvider
import com.example.security.SecureKeyVault
import com.example.service.DeviceControlService
import com.example.service.VoiceAudioController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File

data class PendingExternalSend(
    val prompt: String,
    val imageUri: Uri? = null,
    val includeKnowledgeDocs: Boolean = false,
    val explicitAgent: JarvisAgent? = null
)

data class JarvisUiState(
    val activeProfile: UserProfileEntity = UserProfileEntity(id = "profile_primary", name = "Primary Profile", isActive = true),
    val allProfiles: List<UserProfileEntity> = emptyList(),
    val providerConfigs: Map<AiProvider, ProviderConfigEntity> = emptyMap(),
    val selectedVaultProvider: AiProvider = AiProvider.GEMINI,
    val voiceConfig: VoiceConfigEntity? = null,
    val messages: List<ChatMessageEntity> = emptyList(),
    val memories: List<MemoryEntryEntity> = emptyList(),
    val knowledgeDocs: List<KnowledgeDocEntity> = emptyList(),
    val automations: List<AutomationTaskEntity> = emptyList(),
    val jarvisState: JarvisState = JarvisState.IDLE,
    val activeAgent: JarvisAgent = JarvisAgent.PERSONAL_ASSISTANT,
    val manualAgentOverride: JarvisAgent? = null,
    val isOnline: Boolean = true,
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val isSpeechPaused: Boolean = false,
    val audioLevel: Float = 0f,
    val partialTranscript: String = "",
    val pendingImageUri: Uri? = null,
    val pendingConsent: PendingExternalSend? = null,
    val bannerMessage: String? = null,
    val bannerIsError: Boolean = false,
    val isTestingProvider: Boolean = false,
    val isTestingElevenLabs: Boolean = false,
    val isPreviewingVoice: Boolean = false,
    val isRunningDiagnostics: Boolean = false,
    val diagnostics: List<DiagnosticItem> = emptyList(),
    val torchOn: Boolean = false,
    val keystoreHardwareBacked: Boolean = false,
    val hasSearchKeySaved: Boolean = false
) {
    val activeProvider: AiProvider
        get() = AiProvider.fromId(activeProfile.activeProviderId)

    val activeProviderConfig: ProviderConfigEntity?
        get() = providerConfigs[activeProvider]

    val activeConnectionStatus: ConnectionStateCode
        get() = activeProviderConfig?.let { ConnectionStateCode.fromName(it.connectionStatus) }
            ?: ConnectionStateCode.NOT_CONFIGURED

    val selectedVaultConfig: ProviderConfigEntity?
        get() = providerConfigs[selectedVaultProvider]

    fun discoveredModelsFor(provider: AiProvider): List<String> {
        val raw = providerConfigs[provider]?.discoveredModelsJson ?: "[]"
        val parsed = runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
        return if (parsed.isNotEmpty()) parsed else provider.defaultModels
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val db = JarvisDatabase.getInstance(application)
    private val vault = SecureKeyVault(
        vaultDirectory = File(application.filesDir, "pk_jarvis_vault"),
        cryptoProvider = AndroidKeystoreCryptoProvider()
    )
    private val repository = JarvisRepository(
        db = db,
        vault = vault,
        aiClient = AiProviderClient(),
        elevenLabsClient = ElevenLabsClient(),
        researchClient = WebResearchClient()
    )
    val voiceController = VoiceAudioController(application)
    val deviceControl = DeviceControlService(application)

    private val _ephemeral = MutableStateFlow(EphemeralState())
    private var activeGenerationJob: Job? = null

    private data class EphemeralState(
        val selectedVaultProvider: AiProvider = AiProvider.GEMINI,
        val jarvisState: JarvisState = JarvisState.IDLE,
        val activeAgent: JarvisAgent = JarvisAgent.PERSONAL_ASSISTANT,
        val manualAgentOverride: JarvisAgent? = null,
        val isOnline: Boolean = true,
        val pendingImageUri: Uri? = null,
        val pendingConsent: PendingExternalSend? = null,
        val bannerMessage: String? = null,
        val bannerIsError: Boolean = false,
        val isTestingProvider: Boolean = false,
        val isTestingElevenLabs: Boolean = false,
        val isPreviewingVoice: Boolean = false,
        val isRunningDiagnostics: Boolean = false,
        val diagnostics: List<DiagnosticItem> = emptyList(),
        val torchOn: Boolean = false,
        val keystoreHardwareBacked: Boolean = false,
        val hasSearchKeySaved: Boolean = false
    )

    private val activeProfileFlow = repository.observeActiveProfile()

    private val profileScopedDataFlow = activeProfileFlow.flatMapLatest { profile ->
        if (profile == null) {
            flowOf(ProfileBundle())
        } else {
            combine(
                repository.observeProviderConfigs(profile.id),
                repository.observeVoiceConfig(profile.id),
                repository.observeMessages(profile.id),
                repository.observeMemories(profile.id),
                repository.observeKnowledgeDocs(profile.id),
                repository.observeAutomations(profile.id)
            ) { arr ->
                @Suppress("UNCHECKED_CAST")
                val configs = arr[0] as List<ProviderConfigEntity>
                val voiceCfg = arr[1] as VoiceConfigEntity?
                @Suppress("UNCHECKED_CAST")
                val msgs = arr[2] as List<ChatMessageEntity>
                @Suppress("UNCHECKED_CAST")
                val mems = arr[3] as List<MemoryEntryEntity>
                @Suppress("UNCHECKED_CAST")
                val docs = arr[4] as List<KnowledgeDocEntity>
                @Suppress("UNCHECKED_CAST")
                val tasks = arr[5] as List<AutomationTaskEntity>
                ProfileBundle(
                    profile = profile,
                    configs = configs.associateBy { AiProvider.fromId(it.providerId) },
                    voiceConfig = voiceCfg,
                    messages = msgs,
                    memories = mems,
                    docs = docs,
                    tasks = tasks
                )
            }
        }
    }

    private data class ProfileBundle(
        val profile: UserProfileEntity? = null,
        val configs: Map<AiProvider, ProviderConfigEntity> = emptyMap(),
        val voiceConfig: VoiceConfigEntity? = null,
        val messages: List<ChatMessageEntity> = emptyList(),
        val memories: List<MemoryEntryEntity> = emptyList(),
        val docs: List<KnowledgeDocEntity> = emptyList(),
        val tasks: List<AutomationTaskEntity> = emptyList()
    )

    private val voiceStateFlow = combine(
        voiceController.isListening,
        voiceController.isSpeaking,
        voiceController.isPaused,
        voiceController.audioLevel,
        voiceController.partialTranscript
    ) { listening, speaking, paused, level, partial ->
        VoiceLiveState(listening, speaking, paused, level, partial)
    }

    private data class VoiceLiveState(
        val isListening: Boolean = false,
        val isSpeaking: Boolean = false,
        val isPaused: Boolean = false,
        val audioLevel: Float = 0f,
        val partialTranscript: String = ""
    )

    val uiState: StateFlow<JarvisUiState> = combine(
        repository.observeAllProfiles(),
        profileScopedDataFlow,
        voiceStateFlow,
        _ephemeral
    ) { profiles, bundle, voiceLive, eph ->
        val profile = bundle.profile ?: profiles.firstOrNull()
            ?: UserProfileEntity(id = "profile_primary", name = "Primary Profile", isActive = true)
        val computedJarvisState = when {
            !eph.isOnline -> JarvisState.OFFLINE
            voiceLive.isListening -> JarvisState.LISTENING
            voiceLive.isSpeaking -> JarvisState.SPEAKING
            else -> eph.jarvisState
        }
        JarvisUiState(
            activeProfile = profile,
            allProfiles = profiles,
            providerConfigs = bundle.configs,
            selectedVaultProvider = eph.selectedVaultProvider,
            voiceConfig = bundle.voiceConfig,
            messages = bundle.messages,
            memories = bundle.memories,
            knowledgeDocs = bundle.docs,
            automations = bundle.tasks,
            jarvisState = computedJarvisState,
            activeAgent = eph.manualAgentOverride ?: eph.activeAgent,
            manualAgentOverride = eph.manualAgentOverride,
            isOnline = eph.isOnline,
            isListening = voiceLive.isListening,
            isSpeaking = voiceLive.isSpeaking,
            isSpeechPaused = voiceLive.isPaused,
            audioLevel = voiceLive.audioLevel,
            partialTranscript = voiceLive.partialTranscript,
            pendingImageUri = eph.pendingImageUri,
            pendingConsent = eph.pendingConsent,
            bannerMessage = eph.bannerMessage,
            bannerIsError = eph.bannerIsError,
            isTestingProvider = eph.isTestingProvider,
            isTestingElevenLabs = eph.isTestingElevenLabs,
            isPreviewingVoice = eph.isPreviewingVoice,
            isRunningDiagnostics = eph.isRunningDiagnostics,
            diagnostics = eph.diagnostics,
            torchOn = eph.torchOn,
            keystoreHardwareBacked = eph.keystoreHardwareBacked,
            hasSearchKeySaved = repository.hasSavedSearchKey(profile.id)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = JarvisUiState()
    )

    init {
        viewModelScope.launch {
            val profile = repository.ensureDefaultProfile()
            val online = deviceControl.isNetworkOnline()
            val hwBacked = repository.isHardwareBackedKeystore()
            _ephemeral.update {
                it.copy(
                    selectedVaultProvider = AiProvider.fromId(profile.activeProviderId),
                    isOnline = online,
                    keystoreHardwareBacked = hwBacked,
                    hasSearchKeySaved = repository.hasSavedSearchKey(profile.id)
                )
            }
        }
    }

    fun clearBanner() {
        _ephemeral.update { it.copy(bannerMessage = null, bannerIsError = false) }
    }

    private fun showBanner(message: String, isError: Boolean = false) {
        _ephemeral.update { it.copy(bannerMessage = message, bannerIsError = isError) }
    }

    fun refreshConnectivity() {
        _ephemeral.update { it.copy(isOnline = deviceControl.isNetworkOnline()) }
    }

    // --- Profile Management ---
    fun createProfile(name: String) {
        viewModelScope.launch {
            val created = repository.createProfile(name)
            _ephemeral.update {
                it.copy(
                    selectedVaultProvider = AiProvider.fromId(created.activeProviderId),
                    pendingImageUri = null
                )
            }
            showBanner("Switched to isolated profile \"${created.name}\".")
        }
    }

    fun switchProfile(profileId: String) {
        viewModelScope.launch {
            voiceController.stopSpeaking()
            voiceController.stopListening()
            repository.switchProfile(profileId)
            val target = uiState.value.allProfiles.firstOrNull { it.id == profileId }
            if (target != null) {
                _ephemeral.update {
                    it.copy(
                        selectedVaultProvider = AiProvider.fromId(target.activeProviderId),
                        pendingImageUri = null
                    )
                }
                showBanner("Active profile: ${target.name} (isolated vault).")
            }
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            val deleted = repository.deleteProfile(profileId)
            if (deleted) {
                showBanner("Profile and all its encrypted API keys permanently deleted.")
            } else {
                showBanner("Cannot delete the only remaining profile.", isError = true)
            }
        }
    }

    // --- API Vault & Real Connection Verification ---
    fun selectVaultProvider(provider: AiProvider) {
        _ephemeral.update { it.copy(selectedVaultProvider = provider) }
    }

    fun saveApiKeySecurely(
        provider: AiProvider,
        rawKey: String,
        customBaseUrl: String? = null,
        autoTestAfterSave: Boolean = false
    ) {
        val profile = uiState.value.activeProfile
        viewModelScope.launch {
            val res = repository.saveAiApiKey(profile.id, provider, rawKey, customBaseUrl)
            if (res.success) {
                showBanner("Encrypted & saved securely (${res.maskedKey}).")
                if (autoTestAfterSave) {
                    testProviderConnection(provider)
                }
            } else {
                showBanner(res.errorMessage ?: "Failed to save key.", isError = true)
            }
        }
    }

    fun deleteApiKey(provider: AiProvider) {
        val profile = uiState.value.activeProfile
        viewModelScope.launch {
            repository.deleteAiApiKey(profile.id, provider)
            showBanner("${provider.displayName} key overwritten and deleted from vault.")
        }
    }

    fun selectModel(provider: AiProvider, model: String) {
        val profile = uiState.value.activeProfile
        viewModelScope.launch {
            repository.selectModelForProvider(profile.id, provider, model)
            showBanner("Selected model: $model")
        }
    }

    fun updateCustomBaseUrl(provider: AiProvider, url: String) {
        val profile = uiState.value.activeProfile
        viewModelScope.launch {
            repository.updateCustomBaseUrl(profile.id, provider, url)
        }
    }

    fun updateSecureProxySettings(enabled: Boolean, proxyUrl: String) {
        val profile = uiState.value.activeProfile
        if (enabled) {
            val err = AiProviderClient.validateEndpointUrl(proxyUrl)
            if (err != null) {
                showBanner("Proxy URL error: $err", isError = true)
                return
            }
        }
        viewModelScope.launch {
            repository.updateProfile(profile.copy(useSecureProxy = enabled, secureProxyUrl = proxyUrl.trim()))
            showBanner(if (enabled) "Secure HTTPS proxy enabled." else "Direct provider HTTPS mode active.")
        }
    }

    fun activateProvider(provider: AiProvider) {
        val profile = uiState.value.activeProfile
        viewModelScope.launch {
            repository.selectProviderForProfile(profile, provider)
            _ephemeral.update { it.copy(selectedVaultProvider = provider) }
            showBanner("Switched active brain provider to ${provider.displayName}.")
        }
    }

    fun testProviderConnection(provider: AiProvider) {
        val profile = uiState.value.activeProfile
        refreshConnectivity()
        viewModelScope.launch {
            _ephemeral.update { it.copy(isTestingProvider = true) }
            val result = repository.testProviderConnection(profile, provider)
            _ephemeral.update { it.copy(isTestingProvider = false) }
            val ok = result.status == ConnectionStateCode.CONNECTED
            showBanner(
                message = if (ok) {
                    "Connected to ${provider.displayName} • Active model: ${result.detectedModel ?: "verified"}"
                } else {
                    "${result.status.label}: ${result.message}"
                },
                isError = !ok
            )
        }
    }

    // --- ElevenLabs Dual-Voice Studio ---
    fun saveElevenLabsKey(rawKey: String) {
        val profile = uiState.value.activeProfile
        viewModelScope.launch {
            val res = repository.saveElevenLabsKey(profile.id, rawKey)
            if (res.success) {
                showBanner("ElevenLabs key encrypted & saved (${res.maskedKey}).")
            } else {
                showBanner(res.errorMessage ?: "Invalid ElevenLabs key.", isError = true)
            }
        }
    }

    fun deleteElevenLabsKey() {
        val profile = uiState.value.activeProfile
        viewModelScope.launch {
            repository.deleteElevenLabsKey(profile.id)
            showBanner("ElevenLabs API key deleted from vault.")
        }
    }

    fun testElevenLabsConnection() {
        val profile = uiState.value.activeProfile
        viewModelScope.launch {
            _ephemeral.update { it.copy(isTestingElevenLabs = true) }
            val res = repository.testElevenLabsConnection(profile.id)
            _ephemeral.update { it.copy(isTestingElevenLabs = false) }
            showBanner(res.message, isError = res.status != ConnectionStateCode.CONNECTED)
        }
    }

    fun selectVoice(voice: ElevenLabsVoice) {
        val profile = uiState.value.activeProfile
        viewModelScope.launch {
            repository.updateProfile(profile.copy(selectedVoiceId = voice.voiceId))
            showBanner("Selected voice: ${voice.displayName} (${voice.genderLabel}).")
        }
    }

    fun updateVoiceTuning(speed: Float, stability: Float) {
        val profile = uiState.value.activeProfile
        viewModelScope.launch {
            repository.updateProfile(
                profile.copy(
                    voiceSpeed = speed.coerceIn(0.7f, 1.2f),
                    voiceStability = stability.coerceIn(0.0f, 1.0f)
                )
            )
        }
    }

    fun previewVoice(voice: ElevenLabsVoice) {
        val profile = uiState.value.activeProfile
        val language = AppLanguage.fromCode(profile.languageCode)
        val sampleText = when (language) {
            AppLanguage.HINDI -> "नमस्ते, मैं पीके जार्विस हूँ। आपकी व्यक्तिगत बुद्धिमत्ता प्रणाली तैयार है।"
            AppLanguage.HINGLISH -> "Hello! Main PK JARVIS hoon, aapka personal AI assistant. Voice calibration complete hai."
            AppLanguage.ENGLISH -> "Hello. I am PK JARVIS, your personal intelligence assistant. Voice output is calibrated and ready."
        }
        speakResponse(sampleText, voiceOverride = voice)
    }

    fun saveSearchApiKey(rawKey: String) {
        val profile = uiState.value.activeProfile
        viewModelScope.launch {
            val res = repository.saveOptionalSearchApiKey(profile.id, rawKey)
            if (res.success) {
                _ephemeral.update { it.copy(hasSearchKeySaved = true) }
                showBanner("Personal Tavily Search key saved securely (${res.maskedKey}).")
            } else {
                showBanner(res.errorMessage ?: "Invalid search key.", isError = true)
            }
        }
    }

    fun deleteSearchApiKey() {
        val profile = uiState.value.activeProfile
        viewModelScope.launch {
            repository.deleteOptionalSearchApiKey(profile.id)
            _ephemeral.update { it.copy(hasSearchKeySaved = false) }
            showBanner("Personal Search API key deleted.")
        }
    }

    // --- Preferences ---
    fun toggleVoiceOutput() {
        val profile = uiState.value.activeProfile
        val next = !profile.voiceOutputEnabled
        if (!next) voiceController.stopSpeaking()
        viewModelScope.launch {
            repository.updateProfile(profile.copy(voiceOutputEnabled = next))
        }
    }

    fun toggleContinuousVoice() {
        val profile = uiState.value.activeProfile
        viewModelScope.launch {
            repository.updateProfile(profile.copy(continuousVoiceMode = !profile.continuousVoiceMode))
        }
    }

    fun setLanguage(language: AppLanguage) {
        val profile = uiState.value.activeProfile
        viewModelScope.launch {
            repository.updateProfile(profile.copy(languageCode = language.code))
        }
    }

    fun setResponseMode(mode: ResponseMode) {
        val profile = uiState.value.activeProfile
        viewModelScope.launch {
            repository.updateProfile(profile.copy(responseModeId = mode.id))
        }
    }

    fun setDarkTheme(dark: Boolean) {
        val profile = uiState.value.activeProfile
        viewModelScope.launch {
            repository.updateProfile(profile.copy(darkTheme = dark))
        }
    }

    fun setCustomInstructions(instructions: String) {
        val profile = uiState.value.activeProfile
        viewModelScope.launch {
            repository.updateProfile(profile.copy(customInstructions = instructions.trim()))
            showBanner("Custom AI instructions saved.")
        }
    }

    fun setWakeWord(wakeWord: String) {
        val profile = uiState.value.activeProfile
        viewModelScope.launch {
            repository.updateProfile(profile.copy(wakeWord = wakeWord))
        }
    }

    fun saveEmergencyContact(name: String, phone: String) {
        val profile = uiState.value.activeProfile
        viewModelScope.launch {
            repository.updateProfile(
                profile.copy(
                    emergencyContactName = name.trim(),
                    emergencyContactPhone = phone.trim()
                )
            )
            showBanner("Emergency contact updated locally.")
        }
    }

    fun setManualAgent(agent: JarvisAgent?) {
        _ephemeral.update { it.copy(manualAgentOverride = agent) }
    }

    // --- Vision Attachment & External Provider Consent ---
    fun attachImage(uri: Uri?) {
        _ephemeral.update { it.copy(pendingImageUri = uri) }
    }

    fun submitMessage(
        rawPrompt: String,
        explicitAgent: JarvisAgent? = null,
        includeKnowledgeDocs: Boolean = false
    ) {
        val prompt = rawPrompt.trim()
        val attachedImage = uiState.value.pendingImageUri
        if (prompt.isEmpty() && attachedImage == null) return

        val finalPrompt = prompt.ifEmpty { "Analyze this image, extract any readable text, and explain what you see." }
        val chosenAgent = explicitAgent ?: uiState.value.manualAgentOverride

        // Require explicit user permission before sending images or private knowledge docs to external AI provider
        if (attachedImage != null || includeKnowledgeDocs || chosenAgent == JarvisAgent.KNOWLEDGE) {
            _ephemeral.update {
                it.copy(
                    pendingConsent = PendingExternalSend(
                        prompt = finalPrompt,
                        imageUri = attachedImage,
                        includeKnowledgeDocs = includeKnowledgeDocs || chosenAgent == JarvisAgent.KNOWLEDGE,
                        explicitAgent = chosenAgent
                    )
                )
            }
            return
        }

        executeBrainRequest(
            PendingExternalSend(
                prompt = finalPrompt,
                imageUri = null,
                includeKnowledgeDocs = false,
                explicitAgent = chosenAgent
            )
        )
    }

    fun confirmPendingExternalSend() {
        val pending = uiState.value.pendingConsent ?: return
        _ephemeral.update { it.copy(pendingConsent = null) }
        executeBrainRequest(pending)
    }

    fun dismissPendingExternalSend() {
        _ephemeral.update { it.copy(pendingConsent = null) }
    }

    private fun executeBrainRequest(request: PendingExternalSend) {
        val profile = uiState.value.activeProfile
        refreshConnectivity()

        // Quick local automation capture if user asks to schedule a daily reminder
        maybeCaptureQuickReminder(profile.id, request.prompt)

        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            _ephemeral.update {
                it.copy(
                    jarvisState = JarvisState.PROCESSING,
                    pendingImageUri = null
                )
            }

            val visionPayload = request.imageUri?.let { uri ->
                deviceControl.encodeImageUriToVisionAttachment(uri)
            }

            _ephemeral.update { it.copy(jarvisState = JarvisState.THINKING) }

            val response = repository.sendToJarvisBrain(
                profile = profile,
                userPrompt = request.prompt,
                explicitAgent = request.explicitAgent,
                visionAttachment = visionPayload,
                imageUri = request.imageUri?.toString(),
                includeKnowledgeDocs = request.includeKnowledgeDocs
            )

            _ephemeral.update {
                it.copy(
                    activeAgent = response.agentUsed,
                    jarvisState = if (response.success) JarvisState.IDLE else JarvisState.ERROR
                )
            }

            if (response.success) {
                if (profile.voiceOutputEnabled) {
                    speakResponse(response.replyText)
                }
            } else {
                showBanner(response.errorDetail ?: "AI request failed.", isError = true)
            }
        }
    }

    private fun maybeCaptureQuickReminder(profileId: String, prompt: String) {
        val lower = prompt.lowercase()
        val isReminderIntent = lower.contains("yaad dilana") || lower.contains("remind me") || lower.contains("roz subah")
        if (!isReminderIntent) return
        val hourMatch = Regex("(\\d{1,2})\\s*(baje|am|pm|:00)?").find(lower)
        val parsedHour = hourMatch?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 23) ?: 7
        val isPm = lower.contains("pm") || lower.contains("shaam") || lower.contains("raat")
        val finalHour = if (isPm && parsedHour in 1..11) parsedHour + 12 else parsedHour
        viewModelScope.launch {
            repository.upsertAutomation(
                AutomationTaskEntity(
                    profileId = profileId,
                    title = prompt.take(48),
                    actionPrompt = prompt,
                    scheduleLabel = "Daily at %02d:00".format(finalHour),
                    hourOfDay = finalHour,
                    minuteOfHour = 0,
                    isRecurring = true,
                    status = "ACTIVE",
                    lastResult = "Captured by Automation Agent"
                )
            )
        }
    }

    fun speakResponse(text: String, voiceOverride: ElevenLabsVoice? = null) {
        val profile = uiState.value.activeProfile
        val voice = voiceOverride ?: ElevenLabsVoice.fromVoiceId(profile.selectedVoiceId)
        val language = AppLanguage.fromCode(profile.languageCode)

        viewModelScope.launch {
            if (repository.hasSavedElevenLabsKey(profile.id) && deviceControl.isNetworkOnline()) {
                _ephemeral.update { it.copy(isPreviewingVoice = true, jarvisState = JarvisState.SPEAKING) }
                val res = repository.synthesizeElevenLabsSpeech(
                    profile = profile,
                    voice = voice,
                    text = text,
                    cacheDir = getApplication<Application>().cacheDir
                )
                _ephemeral.update { it.copy(isPreviewingVoice = false) }
                when (res) {
                    is ElevenLabsAudioResult.Success -> {
                        voiceController.playAudioFile(
                            file = res.audioFile,
                            onCompleted = {
                                _ephemeral.update { it.copy(jarvisState = JarvisState.IDLE) }
                                if (uiState.value.activeProfile.continuousVoiceMode &&
                                    deviceControl.hasRecordAudioPermission()
                                ) {
                                    startVoiceListening()
                                }
                            },
                            onError = { err ->
                                showBanner("$err Falling back to device speech.", isError = true)
                                voiceController.speakWithDeviceTts(text, language, profile.voiceSpeed)
                            }
                        )
                    }
                    is ElevenLabsAudioResult.Failure -> {
                        showBanner("${res.message} Using Android device speech fallback.", isError = true)
                        voiceController.speakWithDeviceTts(text, language, profile.voiceSpeed)
                    }
                }
            } else {
                voiceController.speakWithDeviceTts(text, language, profile.voiceSpeed)
            }
        }
    }

    fun startVoiceListening() {
        val profile = uiState.value.activeProfile
        val language = AppLanguage.fromCode(profile.languageCode)
        voiceController.startListening(
            language = language,
            onFinalResult = { transcript ->
                submitMessage(transcript)
            },
            onError = { err ->
                showBanner(err, isError = true)
            }
        )
    }

    fun stopAllVoiceAndGeneration() {
        activeGenerationJob?.cancel()
        voiceController.stopListening()
        voiceController.stopSpeaking()
        _ephemeral.update { it.copy(jarvisState = JarvisState.IDLE) }
    }

    // --- Memory, Knowledge, Automation Actions ---
    fun saveMemory(title: String, content: String, category: String, existingId: Long = 0L) {
        val profile = uiState.value.activeProfile
        if (title.isBlank() || content.isBlank()) return
        viewModelScope.launch {
            repository.addOrUpdateMemory(
                MemoryEntryEntity(
                    id = existingId,
                    profileId = profile.id,
                    title = title.trim(),
                    content = content.trim(),
                    category = category,
                    updatedAt = System.currentTimeMillis()
                )
            )
            showBanner(if (existingId == 0L) "Saved to local memory." else "Memory updated.")
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            repository.deleteMemory(id)
            showBanner("Memory deleted.")
        }
    }

    fun clearChatHistory() {
        val profile = uiState.value.activeProfile
        viewModelScope.launch {
            repository.clearChatHistory(profile.id)
            showBanner("Conversation history cleared for ${profile.name}.")
        }
    }

    fun exportMemoriesToClipboard() {
        val profile = uiState.value.activeProfile
        viewModelScope.launch {
            val json = repository.exportMemoriesJson(profile.id)
            val res = deviceControl.copyToClipboard("PK JARVIS Memory Export", json)
            showBanner(res.message, isError = !res.success)
        }
    }

    fun importMemoriesFromJson(rawJson: String) {
        val profile = uiState.value.activeProfile
        viewModelScope.launch {
            val count = runCatching { repository.importMemoriesJson(profile.id, rawJson) }.getOrElse { -1 }
            if (count >= 0) {
                showBanner("Imported $count memory entries locally.")
            } else {
                showBanner("Invalid memory JSON format.", isError = true)
            }
        }
    }

    fun importKnowledgeDocument(uri: Uri) {
        val profile = uiState.value.activeProfile
        viewModelScope.launch {
            val loaded = deviceControl.readDocumentTextFromUri(uri)
            if (loaded != null) {
                repository.addKnowledgeDoc(
                    KnowledgeDocEntity(
                        profileId = profile.id,
                        title = loaded.title,
                        mimeType = loaded.mimeType,
                        contentText = loaded.contentText,
                        charCount = loaded.contentText.length
                    )
                )
                showBanner("Added \"${loaded.title}\" to Knowledge Center.")
            } else {
                showBanner("Could not read selected file.", isError = true)
            }
        }
    }

    fun deleteKnowledgeDoc(id: Long) {
        viewModelScope.launch {
            repository.deleteKnowledgeDoc(id)
            showBanner("Document removed from Knowledge Center.")
        }
    }

    fun createAutomation(
        title: String,
        actionPrompt: String,
        hour: Int,
        minute: Int,
        isRecurring: Boolean,
        requiresConfirmation: Boolean
    ) {
        val profile = uiState.value.activeProfile
        if (title.isBlank()) return
        viewModelScope.launch {
            val label = if (isRecurring) {
                "Daily at %02d:%02d".format(hour, minute)
            } else {
                "One-time at %02d:%02d".format(hour, minute)
            }
            repository.upsertAutomation(
                AutomationTaskEntity(
                    profileId = profile.id,
                    title = title.trim(),
                    actionPrompt = actionPrompt.trim().ifBlank { title.trim() },
                    scheduleLabel = label,
                    hourOfDay = hour,
                    minuteOfHour = minute,
                    isRecurring = isRecurring,
                    status = "ACTIVE",
                    requiresConfirmation = requiresConfirmation
                )
            )
            showBanner("Automation \"$title\" saved ($label).")
        }
    }

    fun toggleAutomationStatus(task: AutomationTaskEntity) {
        viewModelScope.launch {
            val nextStatus = if (task.status == "ACTIVE") "PAUSED" else "ACTIVE"
            repository.upsertAutomation(task.copy(status = nextStatus))
        }
    }

    fun runAutomationNow(task: AutomationTaskEntity) {
        viewModelScope.launch {
            _ephemeral.update { it.copy(jarvisState = JarvisState.EXECUTING) }
            val notifRes = deviceControl.postLocalNotification(
                title = "PK JARVIS: ${task.title}",
                body = task.actionPrompt
            )
            val resultText = if (notifRes.success) {
                "Triggered & notification sent"
            } else {
                "Executed locally (${notifRes.message})"
            }
            repository.upsertAutomation(
                task.copy(
                    lastRunAt = System.currentTimeMillis(),
                    lastResult = resultText
                )
            )
            _ephemeral.update { it.copy(jarvisState = JarvisState.IDLE) }
            showBanner("Automation executed: $resultText")
        }
    }

    fun deleteAutomation(id: Long) {
        viewModelScope.launch {
            repository.deleteAutomation(id)
            showBanner("Automation removed.")
        }
    }

    // --- Device Actions ---
    fun toggleFlashlight() {
        val res = deviceControl.toggleFlashlight()
        _ephemeral.update { it.copy(torchOn = deviceControl.isTorchEnabled()) }
        showBanner(res.message, isError = !res.success)
    }

    fun runDeviceAction(action: () -> com.example.service.DeviceActionResult) {
        val res = action()
        showBanner(res.message, isError = !res.success)
    }

    // --- Live Diagnostics Suite ---
    fun runFullDiagnostics() {
        val state = uiState.value
        val profile = state.activeProfile
        val provider = state.activeProvider
        viewModelScope.launch {
            _ephemeral.update { it.copy(isRunningDiagnostics = true) }
            val results = mutableListOf<DiagnosticItem>()

            // 1. Network
            val online = deviceControl.isNetworkOnline()
            _ephemeral.update { it.copy(isOnline = online) }
            results.add(
                DiagnosticItem(
                    id = "network",
                    title = "Internet Connectivity",
                    status = if (online) DiagnosticStatus.CONNECTED else DiagnosticStatus.FAILED,
                    detail = if (online) "Active internet connection detected." else "Offline mode — cloud AI requires internet.",
                    hindiTip = "इंटरनेट बंद होने पर केवल ऑफ़लाइन नोट्स और टूल्स काम करेंगे। Wi-Fi या मोबाइल डेटा चालू करें।"
                )
            )

            // 2. Android Keystore Vault
            val hwBacked = repository.isHardwareBackedKeystore()
            results.add(
                DiagnosticItem(
                    id = "keystore",
                    title = "Android Keystore AES-256-GCM Vault",
                    status = DiagnosticStatus.WORKING,
                    detail = if (hwBacked) {
                        "Hardware-backed Keystore (TEE/StrongBox) active."
                    } else {
                        "Android Keystore AES-GCM active (software/emulator keystore on this device)."
                    },
                    hindiTip = "आपकी API key एन्क्रिप्ट होकर केवल इस फ़ोन के सुरक्षित वॉल्ट में रहती है।"
                )
            )

            // 3. Active AI Provider Live Verification
            if (!repository.hasSavedAiKey(profile.id, provider)) {
                results.add(
                    DiagnosticItem(
                        id = "ai_provider",
                        title = "AI Provider (${provider.displayName})",
                        status = DiagnosticStatus.NOT_CONFIGURED,
                        detail = "No API key saved for ${provider.displayName}.",
                        hindiTip = "API Vault टैब में जाकर अपनी ${provider.displayName} API key सेव करें और Test Connection दबाएँ।"
                    )
                )
            } else {
                val test = repository.testProviderConnection(profile, provider)
                val diagStatus = when (test.status) {
                    ConnectionStateCode.CONNECTED -> DiagnosticStatus.CONNECTED
                    ConnectionStateCode.NOT_CONFIGURED -> DiagnosticStatus.NOT_CONFIGURED
                    else -> DiagnosticStatus.FAILED
                }
                results.add(
                    DiagnosticItem(
                        id = "ai_provider",
                        title = "AI Provider (${provider.displayName})",
                        status = diagStatus,
                        detail = "${test.status.label}: ${test.message}",
                        hindiTip = when (test.status) {
                            ConnectionStateCode.INVALID_API_KEY -> "आपकी API key गलत है। प्रोवाइडर डैशबोर्ड से सही key कॉपी करके अपडेट करें।"
                            ConnectionStateCode.INSUFFICIENT_CREDITS -> "आपके अकाउंट में क्रेडिट या कोटा समाप्त हो गया है। बिलिंग चेक करें।"
                            ConnectionStateCode.RATE_LIMITED -> "बहुत ज़्यादा अनुरोध भेजे गए हैं। कुछ सेकंड बाद पुनः प्रयास करें।"
                            ConnectionStateCode.NETWORK_UNAVAILABLE -> "इंटरनेट कनेक्शन जांचें और फिर से कोशिश करें।"
                            else -> "कनेक्शन सक्रिय है और मॉडल तैयार है।"
                        }
                    )
                )
            }

            // 4. ElevenLabs & Dual Voices
            if (!repository.hasSavedElevenLabsKey(profile.id)) {
                results.add(
                    DiagnosticItem(
                        id = "elevenlabs",
                        title = "ElevenLabs Voices (George & Sarah)",
                        status = DiagnosticStatus.NOT_CONFIGURED,
                        detail = "Not configured — Android device speech fallback is active.",
                        hindiTip = "प्रीमियम जॉर्ज और सारा आवाज़ के लिए API Vault में अपनी ElevenLabs API key जोड़ें।"
                    )
                )
            } else {
                val elRes = repository.testElevenLabsConnection(profile.id)
                results.add(
                    DiagnosticItem(
                        id = "elevenlabs",
                        title = "ElevenLabs Voices (George & Sarah)",
                        status = if (elRes.status == ConnectionStateCode.CONNECTED) DiagnosticStatus.CONNECTED else DiagnosticStatus.FAILED,
                        detail = "${elRes.message} ${elRes.characterQuotaSummary}".trim(),
                        hindiTip = "यदि आवाज़ नहीं चल रही है, तो ElevenLabs में बचे हुए कैरेक्टर और API key जांचें।"
                    )
                )
            }

            // 5. Speech Recognition & Mic Permission
            val micGranted = deviceControl.hasRecordAudioPermission()
            val sttAvail = voiceController.isSpeechRecognitionAvailable()
            results.add(
                DiagnosticItem(
                    id = "speech",
                    title = "Voice Recognition (Microphone)",
                    status = when {
                        !sttAvail -> DiagnosticStatus.UNSUPPORTED
                        !micGranted -> DiagnosticStatus.PERMISSION_REQUIRED
                        else -> DiagnosticStatus.WORKING
                    },
                    detail = when {
                        !sttAvail -> "No Android SpeechRecognizer service installed."
                        !micGranted -> "Microphone permission not yet granted."
                        else -> "Android SpeechRecognizer ready for Hindi, English & Hinglish."
                    },
                    hindiTip = "बोलकर बात करने के लिए माइक आइकन दबाकर माइक्रोफ़ोन की अनुमति (Permission) दें।"
                )
            )

            // 6. Device TTS Fallback
            results.add(
                DiagnosticItem(
                    id = "device_tts",
                    title = "Device Text-to-Speech Fallback",
                    status = if (voiceController.isDeviceTtsReady()) DiagnosticStatus.WORKING else DiagnosticStatus.UNSUPPORTED,
                    detail = if (voiceController.isDeviceTtsReady()) "Android TTS engine initialized." else "Android TTS engine unavailable.",
                    hindiTip = "फ़ोन की Settings > Text-to-Speech में Google Speech Services सक्षम रखें।"
                )
            )

            // 7. Notifications & Flashlight
            results.add(
                DiagnosticItem(
                    id = "notifications",
                    title = "Local Reminder Notifications",
                    status = if (deviceControl.hasNotificationPermission()) DiagnosticStatus.WORKING else DiagnosticStatus.PERMISSION_REQUIRED,
                    detail = if (deviceControl.hasNotificationPermission()) "Notifications permitted." else "Grant notification permission for reminder alerts.",
                    hindiTip = "रिमाइंडर अलर्ट पाने के लिए नोटिफिकेशन अनुमति चालू करें।"
                )
            )

            results.add(
                DiagnosticItem(
                    id = "flashlight",
                    title = "Native Device Flashlight",
                    status = if (deviceControl.hasFlashlightHardware()) DiagnosticStatus.WORKING else DiagnosticStatus.UNSUPPORTED,
                    detail = if (deviceControl.hasFlashlightHardware()) "Camera flash hardware available." else "No flash hardware on this device.",
                    hindiTip = "यदि फ़्लैशलाइट न चले तो सुनिश्चित करें कि कैमरा किसी अन्य ऐप में खुला न हो।"
                )
            )

            // 8. Local Room Storage
            results.add(
                DiagnosticItem(
                    id = "storage",
                    title = "Local Memory, Knowledge & Automations DB",
                    status = DiagnosticStatus.WORKING,
                    detail = "${state.memories.size} memories • ${state.knowledgeDocs.size} docs • ${state.automations.size} automations",
                    hindiTip = "आपका डेटा स्थानीय रूप से सुरक्षित है और केवल आपके प्रोफ़ाइल के लिए अलग रखा गया है।"
                )
            )

            _ephemeral.update {
                it.copy(
                    isRunningDiagnostics = false,
                    diagnostics = results,
                    keystoreHardwareBacked = hwBacked
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceController.release()
    }
}
