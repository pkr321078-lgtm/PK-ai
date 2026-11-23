package com.example.network

import com.example.domain.model.ConnectionStateCode
import com.example.domain.model.ElevenLabsVoice
import com.example.security.SecretSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ElevenLabsVerificationResult(
    val status: ConnectionStateCode,
    val message: String,
    val georgeVerified: Boolean = false,
    val sarahVerified: Boolean = false,
    val characterQuotaSummary: String = ""
)

sealed class ElevenLabsAudioResult {
    data class Success(val audioFile: File) : ElevenLabsAudioResult()
    data class Failure(val status: ConnectionStateCode, val message: String) : ElevenLabsAudioResult()
}

/**
 * Real ElevenLabs REST API v1 integration for personal BYOK voice generation.
 * Uses `eleven_multilingual_v2` for English, Hindi, and Hinglish output.
 */
class ElevenLabsClient(
    private val baseUrl: String = "https://api.elevenlabs.io",
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MULTILINGUAL_MODEL_ID = "eleven_multilingual_v2"
    }

    suspend fun verifyKeyAndVoices(apiKeyChars: CharArray): ElevenLabsVerificationResult =
        withContext(Dispatchers.IO) {
            val apiKey = String(apiKeyChars)
            try {
                val userReq = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/v1/user/subscription")
                    .header("xi-api-key", apiKey)
                    .header("Accept", "application/json")
                    .get()
                    .build()

                val quotaSummary: String
                httpClient.newCall(userReq).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        val mapped = mapError(resp.code, body)
                        return@withContext ElevenLabsVerificationResult(mapped.first, mapped.second)
                    }
                    val json = runCatching { JSONObject(body) }.getOrNull()
                    val used = json?.optInt("character_count", -1) ?: -1
                    val limit = json?.optInt("character_limit", -1) ?: -1
                    quotaSummary = if (used >= 0 && limit > 0) {
                        "Characters used: $used / $limit"
                    } else {
                        "ElevenLabs account authenticated"
                    }
                }

                val georgeOk = checkVoiceAccessible(apiKey, ElevenLabsVoice.GEORGE.voiceId)
                val sarahOk = checkVoiceAccessible(apiKey, ElevenLabsVoice.SARAH.voiceId)

                val summaryMsg = buildString {
                    append("Connected to ElevenLabs. ")
                    append("George: ${if (georgeOk) "verified" else "unavailable"}, ")
                    append("Sarah: ${if (sarahOk) "verified" else "unavailable"}.")
                }

                ElevenLabsVerificationResult(
                    status = ConnectionStateCode.CONNECTED,
                    message = summaryMsg,
                    georgeVerified = georgeOk,
                    sarahVerified = sarahOk,
                    characterQuotaSummary = quotaSummary
                )
            } catch (e: IOException) {
                ElevenLabsVerificationResult(
                    status = ConnectionStateCode.NETWORK_UNAVAILABLE,
                    message = "Network error contacting ElevenLabs: ${SecretSanitizer.redact(e.message ?: "offline")}"
                )
            } catch (t: Throwable) {
                ElevenLabsVerificationResult(
                    status = ConnectionStateCode.PROVIDER_UNAVAILABLE,
                    message = SecretSanitizer.redact(t.message ?: "ElevenLabs verification error.")
                )
            }
        }

    private fun checkVoiceAccessible(apiKey: String, voiceId: String): Boolean {
        return try {
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/v1/voices/$voiceId")
                .header("xi-api-key", apiKey)
                .header("Accept", "application/json")
                .get()
                .build()
            httpClient.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun synthesizeSpeech(
        apiKeyChars: CharArray,
        voiceId: String,
        text: String,
        speed: Float,
        stability: Float,
        cacheDir: File
    ): ElevenLabsAudioResult = withContext(Dispatchers.IO) {
        val apiKey = String(apiKeyChars)
        val clampedSpeed = speed.coerceIn(0.7f, 1.2f)
        val clampedStability = stability.coerceIn(0.0f, 1.0f)
        val cleanText = text.trim().take(1800)

        if (cleanText.isEmpty()) {
            return@withContext ElevenLabsAudioResult.Failure(
                ConnectionStateCode.PROVIDER_UNAVAILABLE,
                "Nothing to speak."
            )
        }

        val payload = JSONObject().apply {
            put("text", cleanText)
            put("model_id", MULTILINGUAL_MODEL_ID)
            put(
                "voice_settings",
                JSONObject().apply {
                    put("stability", clampedStability.toDouble())
                    put("similarity_boost", 0.75)
                    put("speed", clampedSpeed.toDouble())
                }
            )
        }

        val req = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v1/text-to-speech/$voiceId")
            .header("xi-api-key", apiKey)
            .header("Accept", "audio/mpeg")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val errBody = resp.body?.string().orEmpty()
                    val mapped = mapError(resp.code, errBody)
                    return@withContext ElevenLabsAudioResult.Failure(mapped.first, mapped.second)
                }
                val bytes = resp.body?.bytes()
                if (bytes == null || bytes.isEmpty()) {
                    return@withContext ElevenLabsAudioResult.Failure(
                        ConnectionStateCode.PROVIDER_UNAVAILABLE,
                        "ElevenLabs returned an empty audio stream."
                    )
                }
                val outFile = File(cacheDir, "jarvis_tts_${System.currentTimeMillis()}.mp3")
                outFile.writeBytes(bytes)
                ElevenLabsAudioResult.Success(outFile)
            }
        } catch (e: IOException) {
            ElevenLabsAudioResult.Failure(
                ConnectionStateCode.NETWORK_UNAVAILABLE,
                "Network error during voice synthesis: ${SecretSanitizer.redact(e.message ?: "offline")}"
            )
        }
    }

    private fun mapError(code: Int, body: String): Pair<ConnectionStateCode, String> {
        val clean = SecretSanitizer.redact(body.take(200))
        val lower = clean.lowercase()
        return when {
            code == 401 || code == 403 ->
                ConnectionStateCode.INVALID_API_KEY to "Invalid ElevenLabs API key (HTTP $code)."
            code == 402 || lower.contains("quota_exceeded") || lower.contains("credit") ->
                ConnectionStateCode.INSUFFICIENT_CREDITS to "ElevenLabs character quota exhausted (HTTP $code)."
            code == 429 ->
                ConnectionStateCode.RATE_LIMITED to "ElevenLabs rate limit reached (HTTP 429)."
            code in 500..599 ->
                ConnectionStateCode.PROVIDER_UNAVAILABLE to "ElevenLabs service temporarily unavailable (HTTP $code)."
            else ->
                ConnectionStateCode.PROVIDER_UNAVAILABLE to "ElevenLabs error HTTP $code: $clean"
        }
    }
}
