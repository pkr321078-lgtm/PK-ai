package com.example.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * Abstraction for authenticated AES-256-GCM encryption backed by Android Keystore.
 * Production uses [AndroidKeystoreCryptoProvider]; JVM unit tests can supply a deterministic
 * AES-GCM software key provider without altering any vault encryption/decryption logic.
 */
interface KeyCryptoProvider {
    val isAndroidKeystoreBacked: Boolean
    fun isHardwareBacked(): Boolean
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray
    fun decrypt(payload: ByteArray, associatedData: ByteArray): ByteArray
}

class AndroidKeystoreCryptoProvider(
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) : KeyCryptoProvider {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_KEY_ALIAS = "pk_jarvis_vault_aes256_gcm_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val FORMAT_VERSION: Byte = 1
    }

    override val isAndroidKeystoreBacked: Boolean = true

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    override fun isHardwareBacked(): Boolean {
        return try {
            val key = getOrCreateKey()
            val factory = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
            val keyInfo = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ||
                    keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
            } else {
                @Suppress("DEPRECATION")
                keyInfo.isInsideSecureHardware
            }
        } catch (_: Throwable) {
            false
        }
    }

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        cipher.updateAAD(associatedData)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)

        val out = ByteArrayOutputStream(2 + iv.size + ciphertext.size)
        out.write(FORMAT_VERSION.toInt())
        out.write(iv.size)
        out.write(iv)
        out.write(ciphertext)
        return out.toByteArray()
    }

    override fun decrypt(payload: ByteArray, associatedData: ByteArray): ByteArray {
        require(payload.size > 16) { "Encrypted payload is malformed or truncated." }
        val version = payload[0]
        require(version == FORMAT_VERSION) { "Unsupported vault format version." }
        val ivLength = payload[1].toInt() and 0xFF
        require(ivLength in 12..16 && payload.size > 2 + ivLength) { "Invalid GCM IV header." }

        val iv = payload.copyOfRange(2, 2 + ivLength)
        val ciphertext = payload.copyOfRange(2 + ivLength, payload.size)

        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(ciphertext)
    }
}

/**
 * Software AES-256-GCM implementation strictly for JVM/Robolectric unit tests where
 * the OS AndroidKeyStore daemon is unavailable. Never used in production builds.
 */
class InMemoryAesGcmCryptoProvider : KeyCryptoProvider {
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply {
        init(256, SecureRandom())
    }.generateKey()

    override val isAndroidKeystoreBacked: Boolean = false
    override fun isHardwareBacked(): Boolean = false

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(associatedData)
        val ciphertext = cipher.doFinal(plaintext)

        val out = ByteArrayOutputStream(2 + iv.size + ciphertext.size)
        out.write(1)
        out.write(iv.size)
        out.write(iv)
        out.write(ciphertext)
        return out.toByteArray()
    }

    override fun decrypt(payload: ByteArray, associatedData: ByteArray): ByteArray {
        require(payload.size > 16) { "Encrypted payload is malformed." }
        val ivLength = payload[1].toInt() and 0xFF
        val iv = payload.copyOfRange(2, 2 + ivLength)
        val ciphertext = payload.copyOfRange(2 + ivLength, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(ciphertext)
    }
}
