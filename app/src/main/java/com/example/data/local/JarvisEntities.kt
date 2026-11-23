package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.AiProvider
import com.example.domain.model.AppLanguage
import com.example.domain.model.ConnectionStateCode
import com.example.domain.model.ElevenLabsVoice
import com.example.domain.model.ResponseMode

/**
 * Local user profile entity. Contains ONLY non-secret preferences and configuration.
 * All API keys are stored exclusively in [com.example.security.SecureKeyVault] encrypted files.
 */
@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val activeProviderId: String = AiProvider.GEMINI.id,
    val useSecureProxy: Boolean = false,
    val secureProxyUrl: String = "",
    val selectedVoiceId: String = ElevenLabsVoice.GEORGE.voiceId,
    val voiceSpeed: Float = 1.0f,
    val voiceStability: Float = 0.55f,
    val voiceOutputEnabled: Boolean = true,
    val continuousVoiceMode: Boolean = false,
    val languageCode: String = AppLanguage.ENGLISH.code,
    val responseModeId: String = ResponseMode.SHORT.id,
    val darkTheme: Boolean = true,
    val customInstructions: String = "",
    val wakeWord: String = "Hey Jarvis",
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = "",
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Per-profile, per-provider non-secret settings and verification status.
 * Stores ONLY the masked key display hint (e.g. "sk-••••••••••••1234"), NEVER the plaintext key.
 */
@Entity(
    tableName = "provider_configs",
    primaryKeys = ["profileId", "providerId"]
)
data class ProviderConfigEntity(
    val profileId: String,
    val providerId: String,
    val maskedKey: String = "",
    val selectedModel: String = "",
    val discoveredModelsJson: String = "[]",
    val customBaseUrl: String = "",
    val connectionStatus: String = ConnectionStateCode.NOT_CONFIGURED.name,
    val connectionMessage: String = "Enter your personal API key to begin.",
    val detectedModel: String = "",
    val latencyMs: Long = 0L,
    val usageSummary: String = "",
    val lastVerifiedAt: Long = 0L
)

/**
 * Per-profile ElevenLabs non-secret voice configuration and verification status.
 */
@Entity(tableName = "voice_configs")
data class VoiceConfigEntity(
    @PrimaryKey val profileId: String,
    val maskedApiKey: String = "",
    val status: String = ConnectionStateCode.NOT_CONFIGURED.name,
    val statusMessage: String = "Optional: add your personal ElevenLabs key or use Android device speech.",
    val georgeVerified: Boolean = false,
    val sarahVerified: Boolean = false,
    val characterQuotaSummary: String = "",
    val lastVerifiedAt: Long = 0L
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val profileId: String,
    val role: String, // "user" or "assistant"
    val content: String,
    val agentId: String,
    val providerId: String = "",
    val modelId: String = "",
    val imageUri: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "memory_entries")
data class MemoryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val profileId: String,
    val title: String,
    val content: String,
    val category: String, // "preference", "fact", "note", "instruction"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "knowledge_docs")
data class KnowledgeDocEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val profileId: String,
    val title: String,
    val mimeType: String,
    val contentText: String,
    val charCount: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "automation_tasks")
data class AutomationTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val profileId: String,
    val title: String,
    val actionPrompt: String,
    val scheduleLabel: String,
    val hourOfDay: Int = 7,
    val minuteOfHour: Int = 0,
    val isRecurring: Boolean = true,
    val status: String = "ACTIVE", // ACTIVE, PAUSED, COMPLETED, CANCELLED, FAILED
    val requiresConfirmation: Boolean = false,
    val lastRunAt: Long = 0L,
    val lastResult: String = "Scheduled and ready",
    val createdAt: Long = System.currentTimeMillis()
)
