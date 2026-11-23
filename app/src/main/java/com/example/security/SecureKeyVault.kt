package com.example.security

import com.example.domain.model.AiProvider
import java.io.File
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Arrays

data class KeyValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

data class VaultSaveResult(
    val success: Boolean,
    val maskedKey: String? = null,
    val errorMessage: String? = null
)

/**
 * Isolated AES-256-GCM encrypted API key vault stored exclusively inside app-private files
 * (`filesDir/pk_jarvis_vault`).
 *
 * Security invariants:
 * - Plaintext keys are NEVER persisted to SharedPreferences, DataStore, Room, or plain files.
 * - Each secret is bound to its `(profileId, slotId)` via AES-GCM Associated Authenticated Data
 *   (AAD), preventing cross-profile ciphertext substitution.
 * - Decrypted buffers (`ByteArray` and `CharArray`) are explicitly zeroed in `finally` blocks.
 * - Deleting a secret overwrites the file with zeros before unlinking.
 */
class SecureKeyVault(
    private val vaultDirectory: File,
    private val cryptoProvider: KeyCryptoProvider
) {
    init {
        if (!vaultDirectory.exists()) {
            vaultDirectory.mkdirs()
        }
    }

    val isKeystoreBacked: Boolean
        get() = cryptoProvider.isAndroidKeystoreBacked

    fun isHardwareBacked(): Boolean = cryptoProvider.isHardwareBacked()

    fun validateAiKey(provider: AiProvider, rawInput: String): KeyValidationResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return KeyValidationResult(false, "API key cannot be empty.")
        }
        if (trimmed.any { it.isWhitespace() }) {
            return KeyValidationResult(false, "API key must not contain spaces or line breaks.")
        }
        if (trimmed.length < 12) {
            return KeyValidationResult(false, "API key looks too short (minimum 12 characters).")
        }
        if (trimmed.length > 512) {
            return KeyValidationResult(false, "API key exceeds maximum supported length.")
        }
        if (provider.expectedPrefixes.isNotEmpty() &&
            provider.expectedPrefixes.none { trimmed.startsWith(it) }
        ) {
            val expected = provider.expectedPrefixes.joinToString(" or ")
            return KeyValidationResult(
                false,
                "${provider.displayName} keys normally start with \"$expected\". Please verify you selected the right provider."
            )
        }
        return KeyValidationResult(true)
    }

    fun validateGenericSecret(rawInput: String, label: String): KeyValidationResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return KeyValidationResult(false, "$label cannot be empty.")
        if (trimmed.any { it.isWhitespace() }) {
            return KeyValidationResult(false, "$label must not contain whitespace.")
        }
        if (trimmed.length < 10) {
            return KeyValidationResult(false, "$label appears too short.")
        }
        return KeyValidationResult(true)
    }

    fun saveSecret(profileId: String, slotId: String, rawSecret: String): VaultSaveResult {
        val trimmed = rawSecret.trim()
        if (trimmed.isEmpty()) {
            return VaultSaveResult(false, errorMessage = "Secret cannot be blank.")
        }
        val plainBytes = trimmed.toByteArray(StandardCharsets.UTF_8)
        return try {
            val aad = buildAad(profileId, slotId)
            val encrypted = cryptoProvider.encrypt(plainBytes, aad)
            val targetFile = resolveSlotFile(profileId, slotId)
            val tempFile = File(vaultDirectory, "${targetFile.name}.tmp")
            tempFile.writeBytes(encrypted)
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                targetFile.writeBytes(encrypted)
                tempFile.delete()
            }
            VaultSaveResult(
                success = true,
                maskedKey = maskKey(trimmed)
            )
        } catch (t: Throwable) {
            VaultSaveResult(
                success = false,
                errorMessage = "Secure encryption failed: ${SecretSanitizer.redact(t.message ?: "Keystore error")}"
            )
        } finally {
            Arrays.fill(plainBytes, 0.toByte())
        }
    }

    fun hasSecret(profileId: String, slotId: String): Boolean {
        val file = resolveSlotFile(profileId, slotId)
        return file.exists() && file.length() > 16L
    }

    /**
     * Decrypts the key into a transient [CharArray], executes [block], and unconditionally wipes
     * both the UTF-8 byte buffer and char buffer from memory in `finally`.
     */
    suspend fun <T> withDecryptedSecret(
        profileId: String,
        slotId: String,
        block: suspend (CharArray) -> T
    ): T? {
        val file = resolveSlotFile(profileId, slotId)
        if (!file.exists()) return null

        var decryptedBytes: ByteArray? = null
        var charArray: CharArray? = null
        return try {
            val payload = file.readBytes()
            val aad = buildAad(profileId, slotId)
            decryptedBytes = cryptoProvider.decrypt(payload, aad)
            val charBuffer: CharBuffer = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(decryptedBytes))
            charArray = CharArray(charBuffer.remaining())
            charBuffer.get(charArray)
            if (charBuffer.hasArray()) {
                Arrays.fill(charBuffer.array(), '\u0000')
            }
            block(charArray)
        } finally {
            decryptedBytes?.let { Arrays.fill(it, 0.toByte()) }
            charArray?.let { Arrays.fill(it, '\u0000') }
        }
    }

    fun deleteSecret(profileId: String, slotId: String): Boolean {
        val file = resolveSlotFile(profileId, slotId)
        if (!file.exists()) return true
        return try {
            val size = file.length().coerceIn(0L, 4096L).toInt()
            if (size > 0) {
                file.writeBytes(ByteArray(size))
            }
            file.delete()
        } catch (_: Throwable) {
            file.delete()
        }
    }

    fun deleteAllForProfile(profileId: String, knownSlots: List<String>) {
        knownSlots.forEach { slot -> deleteSecret(profileId, slot) }
    }

    private fun buildAad(profileId: String, slotId: String): ByteArray =
        "pk_jarvis:v1:$profileId:$slotId".toByteArray(StandardCharsets.UTF_8)

    private fun resolveSlotFile(profileId: String, slotId: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$profileId::$slotId".toByteArray(StandardCharsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }.take(32)
        val file = File(vaultDirectory, "$hex.enc")
        // Enforce strict path confinement within vaultDirectory
        require(file.canonicalPath.startsWith(vaultDirectory.canonicalPath + File.separator)) {
            "Vault file path escaped confinement."
        }
        return file
    }

    companion object {
        const val SLOT_ELEVENLABS = "elevenlabs_tts"
        const val SLOT_SEARCH_API = "web_search_api"
        const val SLOT_PROXY_TOKEN = "secure_proxy_token"

        fun providerSlot(provider: AiProvider): String = "ai_provider_${provider.id}"

        fun allKnownSlots(): List<String> =
            AiProvider.entries.map { providerSlot(it) } +
                listOf(SLOT_ELEVENLABS, SLOT_SEARCH_API, SLOT_PROXY_TOKEN)

        /**
         * Produces a non-reversible masked representation like "sk-••••••••••••1234" or
         * "AIza••••••••••••9x2Q". Never returns the full key.
         */
        fun maskKey(rawKey: String): String {
            val trimmed = rawKey.trim()
            if (trimmed.length <= 6) {
                return "••••••••••••"
            }
            val lastFour = trimmed.takeLast(4)
            val prefix = when {
                trimmed.startsWith("sk-ant-") -> "sk-ant-"
                trimmed.startsWith("sk-or-") -> "sk-or-"
                trimmed.startsWith("sk-") -> "sk-"
                trimmed.startsWith("AIza") -> "AIza"
                trimmed.startsWith("gsk_") -> "gsk_"
                trimmed.startsWith("xi_") -> "xi_"
                else -> ""
            }
            return "${prefix}••••••••••••$lastFour"
        }
    }
}

object SecretSanitizer {
    private val keyPatterns = listOf(
        Regex("sk-[A-Za-z0-9_\\-]{8,}"),
        Regex("AIza[A-Za-z0-9_\\-]{8,}"),
        Regex("gsk_[A-Za-z0-9_\\-]{8,}"),
        Regex("tvly-[A-Za-z0-9_\\-]{8,}"),
        Regex("Bearer\\s+[A-Za-z0-9_\\-.]{8,}", RegexOption.IGNORE_CASE),
        Regex("(x-api-key|x-goog-api-key|xi-api-key)\\s*[:=]\\s*\\S+", RegexOption.IGNORE_CASE)
    )

    fun redact(input: String): String {
        var sanitized = input
        for (pattern in keyPatterns) {
            sanitized = pattern.replace(sanitized, "[REDACTED_KEY]")
        }
        return sanitized
    }
}
