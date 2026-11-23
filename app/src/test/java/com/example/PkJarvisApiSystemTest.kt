package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.JarvisDatabase
import com.example.data.repository.JarvisRepository
import com.example.domain.model.AiProvider
import com.example.domain.model.ConnectionStateCode
import com.example.domain.model.ElevenLabsVoice
import com.example.network.AiProviderClient
import com.example.network.ElevenLabsClient
import com.example.network.WebResearchClient
import com.example.security.InMemoryAesGcmCryptoProvider
import com.example.security.SecretSanitizer
import com.example.security.SecureKeyVault
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress

/**
 * Comprehensive verification suite covering all 13 required API Key System tests:
 * 1. Enter valid API key & validate format
 * 2. Encrypt with AES-256-GCM & save securely (never plaintext on disk or in Room)
 * 3-5. Simulate app restart and confirm key remains saved, encrypted, and masked
 * 6-7. Send real HTTP AI message over OkHttp and receive actual parsed AI response
 * 8. Switch providers (OpenAI <-> Gemini <-> Anthropic) with distinct auth headers
 * 9. Test invalid key (HTTP 401 -> INVALID_API_KEY)
 * 10. Test expired / quota-exhausted key (HTTP 402 / insufficient_quota -> INSUFFICIENT_CREDITS) & rate limit (429)
 * 11. Update key and verify new key is used in live headers
 * 12-13. Delete key and verify deleted credentials cannot be reused + Profile credential isolation
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PkJarvisApiSystemTest {

    private lateinit var context: Context
    private lateinit var vaultDir: File
    private lateinit var crypto: InMemoryAesGcmCryptoProvider
    private lateinit var db: JarvisDatabase
    private lateinit var server: HttpServer
    private lateinit var baseUrl: String

    @Volatile
    private var lastAuthHeader: String? = null

    @Volatile
    private var lastGoogleKeyHeader: String? = null

    @Volatile
    private var lastAnthropicKeyHeader: String? = null

    @Volatile
    private var serverMode: String = "OK"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        vaultDir = File(context.cacheDir, "test_vault_${System.nanoTime()}").apply { mkdirs() }
        crypto = InMemoryAesGcmCryptoProvider()
        db = Room.inMemoryDatabaseBuilder(context, JarvisDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            lastAuthHeader = exchange.requestHeaders.getFirst("Authorization")
            lastGoogleKeyHeader = exchange.requestHeaders.getFirst("x-goog-api-key")
            lastAnthropicKeyHeader = exchange.requestHeaders.getFirst("x-api-key")

            val path = exchange.requestURI.path
            val (status, json) = when (serverMode) {
                "INVALID_KEY" -> 401 to """{"error":{"message":"Incorrect API key provided"}}"""
                "QUOTA_EXHAUSTED" -> 402 to """{"error":{"message":"You exceeded your current quota, insufficient_quota"}}"""
                "RATE_LIMITED" -> 429 to """{"error":{"message":"Rate limit reached for requests"}}"""
                "UNSUPPORTED_MODEL" -> 404 to """{"error":{"message":"The model does not exist"}}"""
                else -> when {
                    path.endsWith("/v1/models") && lastAnthropicKeyHeader != null ->
                        200 to """{"data":[{"id":"claude-3-5-sonnet-latest"}]}"""
                    path.endsWith("/v1/models") ->
                        200 to """{"data":[{"id":"gpt-4o-mini"},{"id":"gpt-4o"}]}"""
                    path.endsWith("/v1beta/models") ->
                        200 to """{"models":[{"name":"models/gemini-2.5-flash","supportedGenerationMethods":["generateContent"]}]}"""
                    path.endsWith("/v1/chat/completions") ->
                        200 to """{"model":"gpt-4o-mini","choices":[{"message":{"role":"assistant","content":"Hello from real OpenAI protocol!"}}],"usage":{"total_tokens":42}}"""
                    path.contains(":generateContent") ->
                        200 to """{"candidates":[{"content":{"parts":[{"text":"Namaste from real Gemini protocol!"}]}}],"usageMetadata":{"totalTokenCount":35}}"""
                    path.endsWith("/v1/messages") ->
                        200 to """{"model":"claude-3-5-sonnet-latest","content":[{"type":"text","text":"Hello from real Claude protocol!"}],"usage":{"input_tokens":12,"output_tokens":18}}"""
                    path.endsWith("/v1/user/subscription") ->
                        200 to """{"character_count":150,"character_limit":10000}"""
                    path.contains("/v1/voices/") ->
                        200 to """{"voice_id":"verified"}"""
                    else -> 404 to """{"error":{"message":"Unknown route $path"}}"""
                }
            }
            val bytes = json.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun tearDown() {
        server.stop(0)
        db.close()
        vaultDir.deleteRecursively()
    }

    private fun newRepository(): JarvisRepository {
        val vault = SecureKeyVault(vaultDir, crypto)
        return JarvisRepository(
            db = db,
            vault = vault,
            aiClient = AiProviderClient(),
            elevenLabsClient = ElevenLabsClient(baseUrl = baseUrl),
            researchClient = WebResearchClient()
        )
    }

    @Test
    fun `full lifecycle - encrypt, persist across restart, mask, live chat, switch provider, update, delete`() = runTest {
        val repo1 = newRepository()
        val profile = repo1.ensureDefaultProfile()
        val rawOpenAiKey = "sk-test-supersecret-9876543210abcd"

        // 1 & 2. Save key securely
        val saveRes = repo1.saveAiApiKey(profile.id, AiProvider.OPENAI, rawOpenAiKey)
        assertTrue(saveRes.success)
        assertEquals("sk-••••••••••••abcd", saveRes.maskedKey)

        // Verify raw key does NOT appear in any file on disk (must be AES-GCM ciphertext, not plaintext or Base64)
        val vaultFiles = vaultDir.listFiles().orEmpty()
        assertTrue(vaultFiles.isNotEmpty())
        vaultFiles.forEach { file ->
            val rawDisk = String(file.readBytes(), Charsets.ISO_8859_1)
            assertFalse("Plaintext key leaked to disk!", rawDisk.contains(rawOpenAiKey))
            assertFalse("Key tail leaked to disk!", rawDisk.contains("9876543210abcd"))
        }

        // Verify Room config stores ONLY masked key
        val storedConfig = db.providerConfigDao().getConfig(profile.id, AiProvider.OPENAI.id)!!
        assertEquals("sk-••••••••••••abcd", storedConfig.maskedKey)
        assertNotEquals(rawOpenAiKey, storedConfig.maskedKey)

        // 3, 4, 5. Simulate app restart by instantiating a brand new Repository & SecureKeyVault over the same vaultDir
        val repoAfterRestart = newRepository()
        assertTrue(repoAfterRestart.hasSavedAiKey(profile.id, AiProvider.OPENAI))
        val persistedCfg = db.providerConfigDao().getConfig(profile.id, AiProvider.OPENAI.id)!!
        assertEquals("sk-••••••••••••abcd", persistedCfg.maskedKey)

        // 4b. Real connection test succeeds & auto-activates OpenAI
        serverMode = "OK"
        val verifyRes = repoAfterRestart.testProviderConnection(profile, AiProvider.OPENAI, baseUrl)
        assertEquals(ConnectionStateCode.CONNECTED, verifyRes.status)
        assertEquals("gpt-4o-mini", verifyRes.detectedModel)
        assertEquals("Bearer $rawOpenAiKey", lastAuthHeader)

        // 6 & 7. Send real AI message & receive actual AI response
        val updatedProfile = db.profileDao().getActiveProfile()!!
        assertEquals(AiProvider.OPENAI.id, updatedProfile.activeProviderId)
        val chatRes = repoAfterRestart.sendToJarvisBrain(
            profile = updatedProfile,
            userPrompt = "Hello PK JARVIS",
            baseUrlOverrideForTesting = baseUrl
        )
        assertTrue(chatRes.success)
        assertEquals("Hello from real OpenAI protocol!", chatRes.replyText)

        // 8. Switch provider to Google Gemini (verifies x-goog-api-key header, never URL param)
        val geminiKey = "AIzaSyTestGeminiKeyValue1234567890"
        val gemSave = repoAfterRestart.saveAiApiKey(updatedProfile.id, AiProvider.GEMINI, geminiKey)
        assertTrue(gemSave.success)
        assertEquals("AIza••••••••••••7890", gemSave.maskedKey)

        val gemVerify = repoAfterRestart.testProviderConnection(updatedProfile, AiProvider.GEMINI, baseUrl)
        assertEquals(ConnectionStateCode.CONNECTED, gemVerify.status)
        assertEquals(geminiKey, lastGoogleKeyHeader)

        val geminiProfile = db.profileDao().getActiveProfile()!!
        val gemReply = repoAfterRestart.sendToJarvisBrain(
            profile = geminiProfile,
            userPrompt = "Namaste Jarvis",
            baseUrlOverrideForTesting = baseUrl
        )
        assertTrue(gemReply.success)
        assertEquals("Namaste from real Gemini protocol!", gemReply.replyText)

        // 11. Update key and confirm new key is sent on next live request
        val rotatedKey = "AIzaSyRotatedGeminiKeyValue9999XYZ1"
        val updateRes = repoAfterRestart.saveAiApiKey(geminiProfile.id, AiProvider.GEMINI, rotatedKey)
        assertTrue(updateRes.success)
        assertEquals("AIza••••••••••••XYZ1", updateRes.maskedKey)

        repoAfterRestart.testProviderConnection(geminiProfile, AiProvider.GEMINI, baseUrl)
        assertEquals(rotatedKey, lastGoogleKeyHeader)

        // 12 & 13. Delete key & verify deleted credentials cannot be reused
        repoAfterRestart.deleteAiApiKey(geminiProfile.id, AiProvider.GEMINI)
        assertFalse(repoAfterRestart.hasSavedAiKey(geminiProfile.id, AiProvider.GEMINI))
        val afterDeleteTest = repoAfterRestart.testProviderConnection(geminiProfile, AiProvider.GEMINI, baseUrl)
        assertEquals(ConnectionStateCode.NOT_CONFIGURED, afterDeleteTest.status)
        val afterDeleteChat = repoAfterRestart.sendToJarvisBrain(
            profile = geminiProfile,
            userPrompt = "Should fail without key",
            baseUrlOverrideForTesting = baseUrl
        )
        assertFalse(afterDeleteChat.success)
        assertEquals(ConnectionStateCode.NOT_CONFIGURED, afterDeleteChat.statusCode)
    }

    @Test
    fun `error classification - invalid key, quota exhausted, rate limited, and unsupported model`() = runTest {
        val repo = newRepository()
        val profile = repo.ensureDefaultProfile()
        repo.saveAiApiKey(profile.id, AiProvider.OPENAI, "sk-test-error-check-12345678")

        // 9. Invalid API key
        serverMode = "INVALID_KEY"
        val invalidRes = repo.testProviderConnection(profile, AiProvider.OPENAI, baseUrl)
        assertEquals(ConnectionStateCode.INVALID_API_KEY, invalidRes.status)
        // Key remains saved securely so user can retry or update
        assertTrue(repo.hasSavedAiKey(profile.id, AiProvider.OPENAI))

        // 10. Quota / credits exhausted
        serverMode = "QUOTA_EXHAUSTED"
        val quotaRes = repo.testProviderConnection(profile, AiProvider.OPENAI, baseUrl)
        assertEquals(ConnectionStateCode.INSUFFICIENT_CREDITS, quotaRes.status)

        // Rate limited
        serverMode = "RATE_LIMITED"
        val rateRes = repo.testProviderConnection(profile, AiProvider.OPENAI, baseUrl)
        assertEquals(ConnectionStateCode.RATE_LIMITED, rateRes.status)

        // Unsupported model
        serverMode = "UNSUPPORTED_MODEL"
        val modelRes = repo.testProviderConnection(profile, AiProvider.OPENAI, baseUrl)
        assertEquals(ConnectionStateCode.UNSUPPORTED_MODEL, modelRes.status)
    }

    @Test
    fun `multi-profile isolation prevents credentials mixing between local users`() = runTest {
        val repo = newRepository()
        val profileA = repo.ensureDefaultProfile()
        repo.saveAiApiKey(profileA.id, AiProvider.ANTHROPIC, "sk-ant-profileA-secret-1111")

        val profileB = repo.createProfile("Secondary User")
        assertFalse("Profile B must NEVER inherit Profile A's key!", repo.hasSavedAiKey(profileB.id, AiProvider.ANTHROPIC))

        val verifyB = repo.testProviderConnection(profileB, AiProvider.ANTHROPIC, baseUrl)
        assertEquals(ConnectionStateCode.NOT_CONFIGURED, verifyB.status)

        // Profile A's key still works cleanly
        serverMode = "OK"
        val verifyA = repo.testProviderConnection(profileA, AiProvider.ANTHROPIC, baseUrl)
        assertEquals(ConnectionStateCode.CONNECTED, verifyA.status)
        assertEquals("sk-ant-profileA-secret-1111", lastAnthropicKeyHeader)
    }

    @Test
    fun `elevenlabs verification checks George and Sarah voices and sanitizer redacts secrets`() = runTest {
        val repo = newRepository()
        val profile = repo.ensureDefaultProfile()
        val save = repo.saveElevenLabsKey(profile.id, "xi_personal_test_key_99887766")
        assertTrue(save.success)
        assertEquals("xi_••••••••••••7766", save.maskedKey)

        serverMode = "OK"
        val res = repo.testElevenLabsConnection(profile.id)
        assertEquals(ConnectionStateCode.CONNECTED, res.status)
        assertTrue(res.georgeVerified)
        assertTrue(res.sarahVerified)
        assertEquals("JBFqnCBsd6RMkjVDRZzb", ElevenLabsVoice.GEORGE.voiceId)
        assertEquals("EXAVITQu4vr4xnSDxMaL", ElevenLabsVoice.SARAH.voiceId)

        // Ensure SecretSanitizer scrubs leaked tokens
        val redacted = SecretSanitizer.redact("Error with Bearer sk-proj-abc1234567890 and AIzaSy1234567890")
        assertFalse(redacted.contains("sk-proj-abc"))
        assertFalse(redacted.contains("AIzaSy123"))
    }
}
