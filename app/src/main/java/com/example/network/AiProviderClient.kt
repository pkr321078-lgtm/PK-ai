package com.example.network

import com.example.domain.model.AiProvider
import com.example.domain.model.ConnectionStateCode
import com.example.domain.model.ConnectionVerificationResult
import com.example.security.SecretSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class AiConversationTurn(
    val role: String, // "user" or "assistant"
    val content: String
)

data class AiVisionAttachment(
    val mimeType: String,
    val base64Data: String
)

sealed class AiChatResult {
    data class Success(
        val replyText: String,
        val modelUsed: String,
        val latencyMs: Long,
        val usageNote: String? = null
    ) : AiChatResult()

    data class Failure(
        val statusCode: ConnectionStateCode,
        val message: String
    ) : AiChatResult()
}

/**
 * Production HTTPS client supporting Google Gemini, OpenAI, Anthropic Claude, Groq, OpenRouter,
 * OpenAI-compatible providers, and optional user-configured Secure Backend Proxy endpoints.
 *
 * Security rules:
 * - Never attaches HTTP body/header logging interceptors.
 * - Never places API keys in URL query strings (Gemini uses `x-goog-api-key` header).
 * - Rejects cleartext `http://` URLs except loopback (`127.0.0.1`/`localhost`) for local testing.
 */
class AiProviderClient(
    private val httpClient: OkHttpClient = defaultClient()
) {
    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        fun validateEndpointUrl(rawUrl: String): String? {
            val trimmed = rawUrl.trim().trimEnd('/')
            if (trimmed.isEmpty()) return "Endpoint URL cannot be empty."
            val uri = try {
                URI(trimmed)
            } catch (_: Exception) {
                return "Invalid URL format."
            }
            val scheme = uri.scheme?.lowercase() ?: ""
            val host = uri.host?.lowercase() ?: ""
            if (host.isEmpty()) return "URL must include a valid hostname."
            val isLoopback = host == "127.0.0.1" || host == "localhost"
            if (scheme != "https" && !(scheme == "http" && isLoopback)) {
                return "Only HTTPS endpoints are allowed for API key transmission."
            }
            return null
        }
    }

    suspend fun verifyConnection(
        provider: AiProvider,
        apiKeyChars: CharArray,
        selectedModel: String,
        baseUrlOverride: String? = null
    ): ConnectionVerificationResult = withContext(Dispatchers.IO) {
        val baseUrl = resolveBaseUrl(provider, baseUrlOverride)
        val urlError = validateEndpointUrl(baseUrl)
        if (urlError != null) {
            return@withContext ConnectionVerificationResult(
                status = ConnectionStateCode.PROVIDER_UNAVAILABLE,
                message = urlError
            )
        }

        val apiKey = String(apiKeyChars)
        val startMs = System.currentTimeMillis()
        try {
            // Step 1: Real authenticated model discovery request
            val modelsRequest = buildModelsRequest(provider, baseUrl, apiKey)
            httpClient.newCall(modelsRequest).execute().use { response ->
                val latency = System.currentTimeMillis() - startMs
                val bodyString = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val mapped = classifyHttpError(provider, response.code, bodyString)
                    return@withContext ConnectionVerificationResult(
                        status = mapped.first,
                        message = mapped.second,
                        latencyMs = latency
                    )
                }

                val discovered = parseDiscoveredModels(provider, bodyString)
                val targetModel = selectedModel.trim().ifEmpty {
                    discovered.firstOrNull() ?: provider.defaultModels.first()
                }

                // If provider returned a concrete list and user explicitly picked a model not in it,
                // run a lightweight verification call to see if the model is actually supported
                if (discovered.isNotEmpty() && selectedModel.isNotBlank() && !discovered.contains(targetModel)) {
                    val probeResult = sendChatMessage(
                        provider = provider,
                        apiKeyChars = apiKeyChars,
                        model = targetModel,
                        systemPrompt = "Reply with OK.",
                        history = emptyList(),
                        userMessage = "Reply OK",
                        visionAttachment = null,
                        baseUrlOverride = baseUrlOverride,
                        maxTokens = 8
                    )
                    return@withContext when (probeResult) {
                        is AiChatResult.Success -> ConnectionVerificationResult(
                            status = ConnectionStateCode.CONNECTED,
                            message = "Verified live connection with ${provider.displayName} (${probeResult.modelUsed}).",
                            detectedModel = probeResult.modelUsed,
                            discoveredModels = discovered,
                            latencyMs = probeResult.latencyMs,
                            usageSummary = probeResult.usageNote
                        )
                        is AiChatResult.Failure -> ConnectionVerificationResult(
                            status = probeResult.statusCode,
                            message = probeResult.message,
                            discoveredModels = discovered,
                            latencyMs = System.currentTimeMillis() - startMs
                        )
                    }
                }

                val detected = if (discovered.contains(targetModel)) {
                    targetModel
                } else {
                    discovered.firstOrNull() ?: targetModel
                }

                ConnectionVerificationResult(
                    status = ConnectionStateCode.CONNECTED,
                    message = "Authenticated with ${provider.displayName}. ${discovered.size} models discovered.",
                    detectedModel = detected,
                    discoveredModels = if (discovered.isNotEmpty()) discovered else provider.defaultModels,
                    latencyMs = latency,
                    usageSummary = "HTTP ${response.code} • ${latency}ms"
                )
            }
        } catch (e: IOException) {
            classifyNetworkException(e)
        } catch (t: Throwable) {
            ConnectionVerificationResult(
                status = ConnectionStateCode.PROVIDER_UNAVAILABLE,
                message = SecretSanitizer.redact(t.message ?: "Unexpected verification error.")
            )
        }
    }

    suspend fun sendChatMessage(
        provider: AiProvider,
        apiKeyChars: CharArray,
        model: String,
        systemPrompt: String,
        history: List<AiConversationTurn>,
        userMessage: String,
        visionAttachment: AiVisionAttachment? = null,
        baseUrlOverride: String? = null,
        maxTokens: Int = 1024
    ): AiChatResult = withContext(Dispatchers.IO) {
        val baseUrl = resolveBaseUrl(provider, baseUrlOverride)
        val urlError = validateEndpointUrl(baseUrl)
        if (urlError != null) {
            return@withContext AiChatResult.Failure(ConnectionStateCode.PROVIDER_UNAVAILABLE, urlError)
        }

        val apiKey = String(apiKeyChars)
        val resolvedModel = model.trim().ifEmpty { provider.defaultModels.first() }
        val startMs = System.currentTimeMillis()

        try {
            val request = when (provider) {
                AiProvider.GEMINI -> buildGeminiChatRequest(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = resolvedModel,
                    systemPrompt = systemPrompt,
                    history = history,
                    userMessage = userMessage,
                    visionAttachment = visionAttachment,
                    maxTokens = maxTokens
                )
                AiProvider.ANTHROPIC -> buildAnthropicChatRequest(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = resolvedModel,
                    systemPrompt = systemPrompt,
                    history = history,
                    userMessage = userMessage,
                    visionAttachment = visionAttachment,
                    maxTokens = maxTokens
                )
                AiProvider.OPENAI,
                AiProvider.GROQ,
                AiProvider.OPENROUTER,
                AiProvider.CUSTOM_COMPATIBLE -> buildOpenAiCompatibleChatRequest(
                    provider = provider,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = resolvedModel,
                    systemPrompt = systemPrompt,
                    history = history,
                    userMessage = userMessage,
                    visionAttachment = visionAttachment,
                    maxTokens = maxTokens
                )
            }

            httpClient.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startMs
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val mapped = classifyHttpError(provider, response.code, body)
                    return@withContext AiChatResult.Failure(mapped.first, mapped.second)
                }

                parseChatResponse(provider, resolvedModel, body, latency)
            }
        } catch (e: IOException) {
            val err = classifyNetworkException(e)
            AiChatResult.Failure(err.status, err.message)
        } catch (t: Throwable) {
            AiChatResult.Failure(
                ConnectionStateCode.PROVIDER_UNAVAILABLE,
                SecretSanitizer.redact(t.message ?: "Provider request failed.")
            )
        }
    }

    private fun resolveBaseUrl(provider: AiProvider, override: String?): String {
        val candidate = override?.trim().orEmpty()
        return if (candidate.isNotEmpty()) candidate.trimEnd('/') else provider.defaultBaseUrl.trimEnd('/')
    }

    private fun buildModelsRequest(provider: AiProvider, baseUrl: String, apiKey: String): Request {
        return when (provider) {
            AiProvider.GEMINI -> Request.Builder()
                .url("$baseUrl/v1beta/models")
                .header("x-goog-api-key", apiKey)
                .header("Accept", "application/json")
                .get()
                .build()
            AiProvider.OPENAI -> Request.Builder()
                .url("$baseUrl/v1/models")
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .get()
                .build()
            AiProvider.ANTHROPIC -> Request.Builder()
                .url("$baseUrl/v1/models")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Accept", "application/json")
                .get()
                .build()
            AiProvider.GROQ -> Request.Builder()
                .url("$baseUrl/openai/v1/models")
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .get()
                .build()
            AiProvider.OPENROUTER -> Request.Builder()
                .url("$baseUrl/api/v1/models")
                .header("Authorization", "Bearer $apiKey")
                .header("HTTP-Referer", "https://pkjarvis.local")
                .header("X-Title", "PK JARVIS")
                .header("Accept", "application/json")
                .get()
                .build()
            AiProvider.CUSTOM_COMPATIBLE -> {
                val url = if (baseUrl.endsWith("/models")) baseUrl else "$baseUrl/models"
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $apiKey")
                    .header("Accept", "application/json")
                    .get()
                    .build()
            }
        }
    }

    private fun buildGeminiChatRequest(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<AiConversationTurn>,
        userMessage: String,
        visionAttachment: AiVisionAttachment?,
        maxTokens: Int
    ): Request {
        val cleanModel = model.removePrefix("models/")
        val contents = JSONArray()
        history.takeLast(10).forEach { turn ->
            val role = if (turn.role == "assistant") "model" else "user"
            contents.put(
                JSONObject().apply {
                    put("role", role)
                    put("parts", JSONArray().put(JSONObject().put("text", turn.content)))
                }
            )
        }

        val currentParts = JSONArray().apply {
            put(JSONObject().put("text", userMessage))
            if (visionAttachment != null) {
                put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject().apply {
                            put("mimeType", visionAttachment.mimeType)
                            put("data", visionAttachment.base64Data)
                        }
                    )
                )
            }
        }
        contents.put(
            JSONObject().apply {
                put("role", "user")
                put("parts", currentParts)
            }
        )

        val payload = JSONObject().apply {
            if (systemPrompt.isNotBlank()) {
                put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", systemPrompt))
                    )
                )
            }
            put("contents", contents)
            put(
                "generationConfig",
                JSONObject().apply {
                    put("maxOutputTokens", maxTokens)
                    put("temperature", 0.7)
                }
            )
        }

        return Request.Builder()
            .url("$baseUrl/v1beta/models/$cleanModel:generateContent")
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun buildAnthropicChatRequest(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<AiConversationTurn>,
        userMessage: String,
        visionAttachment: AiVisionAttachment?,
        maxTokens: Int
    ): Request {
        val messages = JSONArray()
        history.takeLast(10).forEach { turn ->
            messages.put(
                JSONObject().apply {
                    put("role", if (turn.role == "assistant") "assistant" else "user")
                    put("content", turn.content)
                }
            )
        }

        val currentMsg = JSONObject().apply {
            put("role", "user")
            if (visionAttachment != null) {
                val blocks = JSONArray()
                blocks.put(
                    JSONObject().apply {
                        put("type", "image")
                        put(
                            "source",
                            JSONObject().apply {
                                put("type", "base64")
                                put("media_type", visionAttachment.mimeType)
                                put("data", visionAttachment.base64Data)
                            }
                        )
                    }
                )
                blocks.put(
                    JSONObject().apply {
                        put("type", "text")
                        put("text", userMessage)
                    }
                )
                put("content", blocks)
            } else {
                put("content", userMessage)
            }
        }
        messages.put(currentMsg)

        val payload = JSONObject().apply {
            put("model", model)
            put("max_tokens", maxTokens)
            if (systemPrompt.isNotBlank()) {
                put("system", systemPrompt)
            }
            put("messages", messages)
        }

        return Request.Builder()
            .url("$baseUrl/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun buildOpenAiCompatibleChatRequest(
        provider: AiProvider,
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<AiConversationTurn>,
        userMessage: String,
        visionAttachment: AiVisionAttachment?,
        maxTokens: Int
    ): Request {
        val messages = JSONArray()
        if (systemPrompt.isNotBlank()) {
            messages.put(
                JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                }
            )
        }
        history.takeLast(10).forEach { turn ->
            messages.put(
                JSONObject().apply {
                    put("role", if (turn.role == "assistant") "assistant" else "user")
                    put("content", turn.content)
                }
            )
        }

        if (visionAttachment != null && provider.supportsVision) {
            val parts = JSONArray()
            parts.put(JSONObject().apply {
                put("type", "text")
                put("text", userMessage)
            })
            parts.put(JSONObject().apply {
                put("type", "image_url")
                put(
                    "image_url",
                    JSONObject().put(
                        "url",
                        "data:${visionAttachment.mimeType};base64,${visionAttachment.base64Data}"
                    )
                )
            })
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", parts)
            })
        } else {
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })
        }

        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("max_tokens", maxTokens)
            put("temperature", 0.7)
        }

        val endpointUrl = when (provider) {
            AiProvider.OPENAI -> "$baseUrl/v1/chat/completions"
            AiProvider.GROQ -> "$baseUrl/openai/v1/chat/completions"
            AiProvider.OPENROUTER -> "$baseUrl/api/v1/chat/completions"
            AiProvider.CUSTOM_COMPATIBLE ->
                if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"
            else -> "$baseUrl/v1/chat/completions"
        }

        val builder = Request.Builder()
            .url(endpointUrl)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))

        if (provider == AiProvider.OPENROUTER) {
            builder.header("HTTP-Referer", "https://pkjarvis.local")
            builder.header("X-Title", "PK JARVIS")
        }

        return builder.build()
    }

    private fun parseDiscoveredModels(provider: AiProvider, body: String): List<String> {
        return try {
            val json = JSONObject(body)
            val result = mutableListOf<String>()
            when (provider) {
                AiProvider.GEMINI -> {
                    val models = json.optJSONArray("models") ?: JSONArray()
                    for (i in 0 until models.length()) {
                        val item = models.optJSONObject(i) ?: continue
                        val methods = item.optJSONArray("supportedGenerationMethods")
                        var supportsGenerate = methods == null
                        if (methods != null) {
                            for (j in 0 until methods.length()) {
                                if (methods.optString(j) == "generateContent") {
                                    supportsGenerate = true
                                    break
                                }
                            }
                        }
                        if (supportsGenerate) {
                            val name = item.optString("name").removePrefix("models/")
                            if (name.isNotBlank()) result.add(name)
                        }
                    }
                }
                else -> {
                    val data = json.optJSONArray("data") ?: JSONArray()
                    for (i in 0 until data.length()) {
                        val item = data.optJSONObject(i) ?: continue
                        val id = item.optString("id")
                        if (id.isNotBlank()) result.add(id)
                    }
                }
            }
            // Prioritize preferred known chat models first, then remaining discovered models
            val preferred = result.filter { id ->
                provider.defaultModels.any { id.equals(it, ignoreCase = true) }
            }
            val rest = result.filterNot { it in preferred }.take(30)
            (preferred + rest).distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseChatResponse(
        provider: AiProvider,
        requestedModel: String,
        body: String,
        latencyMs: Long
    ): AiChatResult {
        return try {
            val json = JSONObject(body)
            when (provider) {
                AiProvider.GEMINI -> {
                    val candidates = json.optJSONArray("candidates")
                    val first = candidates?.optJSONObject(0)
                    val parts = first?.optJSONObject("content")?.optJSONArray("parts")
                    val textBuilder = StringBuilder()
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val partText = parts.optJSONObject(i)?.optString("text").orEmpty()
                            if (partText.isNotEmpty()) textBuilder.append(partText)
                        }
                    }
                    val reply = textBuilder.toString().trim()
                    if (reply.isEmpty()) {
                        AiChatResult.Failure(
                            ConnectionStateCode.PROVIDER_UNAVAILABLE,
                            "Gemini returned an empty response."
                        )
                    } else {
                        val usage = json.optJSONObject("usageMetadata")
                        val totalTokens = usage?.optInt("totalTokenCount", 0) ?: 0
                        val usageNote = if (totalTokens > 0) "$totalTokens tokens • ${latencyMs}ms" else "${latencyMs}ms"
                        AiChatResult.Success(reply, requestedModel, latencyMs, usageNote)
                    }
                }
                AiProvider.ANTHROPIC -> {
                    val content = json.optJSONArray("content")
                    val textBuilder = StringBuilder()
                    if (content != null) {
                        for (i in 0 until content.length()) {
                            val block = content.optJSONObject(i)
                            if (block?.optString("type") == "text") {
                                textBuilder.append(block.optString("text"))
                            }
                        }
                    }
                    val reply = textBuilder.toString().trim()
                    val modelUsed = json.optString("model").ifBlank { requestedModel }
                    if (reply.isEmpty()) {
                        AiChatResult.Failure(
                            ConnectionStateCode.PROVIDER_UNAVAILABLE,
                            "Claude returned an empty response."
                        )
                    } else {
                        val usage = json.optJSONObject("usage")
                        val inTok = usage?.optInt("input_tokens", 0) ?: 0
                        val outTok = usage?.optInt("output_tokens", 0) ?: 0
                        val usageNote = if (inTok + outTok > 0) "${inTok + outTok} tokens • ${latencyMs}ms" else "${latencyMs}ms"
                        AiChatResult.Success(reply, modelUsed, latencyMs, usageNote)
                    }
                }
                else -> {
                    val choices = json.optJSONArray("choices")
                    val message = choices?.optJSONObject(0)?.optJSONObject("message")
                    val reply = message?.optString("content").orEmpty().trim()
                    val modelUsed = json.optString("model").ifBlank { requestedModel }
                    if (reply.isEmpty()) {
                        AiChatResult.Failure(
                            ConnectionStateCode.PROVIDER_UNAVAILABLE,
                            "${provider.displayName} returned an empty response."
                        )
                    } else {
                        val usage = json.optJSONObject("usage")
                        val totalTokens = usage?.optInt("total_tokens", 0) ?: 0
                        val usageNote = if (totalTokens > 0) "$totalTokens tokens • ${latencyMs}ms" else "${latencyMs}ms"
                        AiChatResult.Success(reply, modelUsed, latencyMs, usageNote)
                    }
                }
            }
        } catch (t: Throwable) {
            AiChatResult.Failure(
                ConnectionStateCode.PROVIDER_UNAVAILABLE,
                "Could not parse ${provider.displayName} response: ${SecretSanitizer.redact(t.message ?: "invalid JSON")}"
            )
        }
    }

    private fun extractErrorMessage(body: String): String {
        return try {
            val json = JSONObject(body)
            val errObj = json.optJSONObject("error")
            val message = errObj?.optString("message").orEmpty()
                .ifBlank { json.optString("message") }
                .ifBlank { json.optString("detail") }
                .ifBlank { body.take(220) }
            SecretSanitizer.redact(message)
        } catch (_: Exception) {
            SecretSanitizer.redact(body.take(220))
        }
    }

    private fun classifyHttpError(
        provider: AiProvider,
        code: Int,
        body: String
    ): Pair<ConnectionStateCode, String> {
        val detail = extractErrorMessage(body)
        val lower = (detail + " " + body).lowercase()

        return when {
            code == 401 -> ConnectionStateCode.INVALID_API_KEY to
                "Invalid API key rejected by ${provider.displayName} (HTTP 401). $detail"
            code == 402 ||
                lower.contains("insufficient_quota") ||
                lower.contains("insufficient credits") ||
                lower.contains("credit balance") ||
                lower.contains("billing") ||
                lower.contains("quota exceeded") ||
                lower.contains("resource_exhausted") && lower.contains("quota") ->
                ConnectionStateCode.INSUFFICIENT_CREDITS to
                    "Insufficient credits or quota on your ${provider.displayName} account (HTTP $code). $detail"
            code == 403 -> {
                if (lower.contains("api key") || lower.contains("invalid") || lower.contains("unauthorized") || lower.contains("permission")) {
                    ConnectionStateCode.INVALID_API_KEY to
                        "Access denied for this API key (HTTP 403). Check key permissions or restrictions. $detail"
                } else {
                    ConnectionStateCode.PROVIDER_UNAVAILABLE to
                        "${provider.displayName} refused the request (HTTP 403). $detail"
                }
            }
            code == 429 -> ConnectionStateCode.RATE_LIMITED to
                "Rate limited by ${provider.displayName} (HTTP 429). Please wait and retry. $detail"
            code == 404 ||
                (code == 400 && (lower.contains("model") && (lower.contains("not found") || lower.contains("unsupported") || lower.contains("does not exist")))) ->
                ConnectionStateCode.UNSUPPORTED_MODEL to
                    "Selected model is unsupported or unavailable on ${provider.displayName} (HTTP $code). $detail"
            code == 400 && (lower.contains("api key not valid") || lower.contains("api_key_invalid")) ->
                ConnectionStateCode.INVALID_API_KEY to
                    "Invalid API key reported by ${provider.displayName} (HTTP 400). $detail"
            code in 500..599 -> ConnectionStateCode.PROVIDER_UNAVAILABLE to
                "${provider.displayName} service is currently unavailable (HTTP $code). Retry shortly."
            else -> ConnectionStateCode.PROVIDER_UNAVAILABLE to
                "${provider.displayName} returned HTTP $code: $detail"
        }
    }

    private fun classifyNetworkException(e: IOException): ConnectionVerificationResult {
        val reason = when (e) {
            is UnknownHostException -> "No internet connection or DNS lookup failed."
            is ConnectException -> "Could not connect to provider server."
            is SocketTimeoutException -> "Connection timed out while contacting provider."
            else -> SecretSanitizer.redact(e.message ?: "Network transport error.")
        }
        return ConnectionVerificationResult(
            status = ConnectionStateCode.NETWORK_UNAVAILABLE,
            message = reason
        )
    }
}
