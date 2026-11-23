package com.example.network

import com.example.security.SecretSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ResearchSource(
    val title: String,
    val snippet: String,
    val url: String,
    val providerName: String
)

data class WebResearchResult(
    val sources: List<ResearchSource>,
    val note: String
)

/**
 * Live Internet Research client.
 * - If the user configures their personal Tavily Search API key, queries Tavily live web search.
 * - Otherwise queries public Wikipedia OpenSearch/Summary + DuckDuckGo Instant Answer APIs over HTTPS
 *   and honestly reports the exact sources returned without inventing citations.
 */
class WebResearchClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    suspend fun search(query: String, optionalTavilyKey: CharArray? = null): WebResearchResult =
        withContext(Dispatchers.IO) {
            val cleanQuery = query.trim()
            if (cleanQuery.isEmpty()) {
                return@withContext WebResearchResult(emptyList(), "Empty research query.")
            }

            if (optionalTavilyKey != null && optionalTavilyKey.isNotEmpty()) {
                val tavilyResults = runCatching { queryTavily(cleanQuery, String(optionalTavilyKey)) }.getOrNull()
                if (tavilyResults != null && tavilyResults.isNotEmpty()) {
                    return@withContext WebResearchResult(
                        sources = tavilyResults,
                        note = "Live web results fetched via your personal Tavily Search API."
                    )
                }
            }

            val combined = mutableListOf<ResearchSource>()
            runCatching { queryWikipedia(cleanQuery) }.getOrNull()?.let { combined.addAll(it) }
            runCatching { queryDuckDuckGoInstant(cleanQuery) }.getOrNull()?.let { combined.addAll(it) }

            if (combined.isEmpty()) {
                WebResearchResult(
                    sources = emptyList(),
                    note = "No live web results returned from public Wikipedia/DuckDuckGo endpoints for this query. For broader real-time web indexing, add a personal Tavily Search API key in API Vault."
                )
            } else {
                WebResearchResult(
                    sources = combined.distinctBy { it.url }.take(5),
                    note = "Live public references fetched via Wikipedia & DuckDuckGo Instant Answer API."
                )
            }
        }

    private fun queryTavily(query: String, apiKey: String): List<ResearchSource> {
        val payload = JSONObject().apply {
            put("query", query)
            put("max_results", 5)
            put("search_depth", "basic")
        }
        val req = Request.Builder()
            .url("https://api.tavily.com/search")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string().orEmpty()
            val json = JSONObject(body)
            val arr = json.optJSONArray("results") ?: return emptyList()
            val list = mutableListOf<ResearchSource>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val url = obj.optString("url")
                val title = obj.optString("title")
                val content = obj.optString("content").take(350)
                if (url.startsWith("https://") && title.isNotBlank()) {
                    list.add(ResearchSource(title, content, url, "Tavily Web"))
                }
            }
            return list
        }
    }

    private fun queryWikipedia(query: String): List<ResearchSource> {
        val url = "https://en.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("list", "search")
            .addQueryParameter("srsearch", query)
            .addQueryParameter("srlimit", "3")
            .addQueryParameter("format", "json")
            .build()

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "PKJarvisAndroid/1.0")
            .get()
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val json = JSONObject(resp.body?.string().orEmpty())
            val results = json.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
            val list = mutableListOf<ResearchSource>()
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val title = item.optString("title")
                val snippet = item.optString("snippet")
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&quot;", "\"")
                    .replace("&amp;", "&")
                val pageUrl = "https://en.wikipedia.org/wiki/${title.replace(' ', '_')}"
                if (title.isNotBlank()) {
                    list.add(ResearchSource(title, snippet, pageUrl, "Wikipedia"))
                }
            }
            return list
        }
    }

    private fun queryDuckDuckGoInstant(query: String): List<ResearchSource> {
        val url = "https://api.duckduckgo.com/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("format", "json")
            .addQueryParameter("no_html", "1")
            .addQueryParameter("skip_disambig", "1")
            .build()

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "PKJarvisAndroid/1.0")
            .get()
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = SecretSanitizer.redact(resp.body?.string().orEmpty())
            val json = JSONObject(body)
            val list = mutableListOf<ResearchSource>()
            val abstractText = json.optString("AbstractText")
            val abstractUrl = json.optString("AbstractURL")
            val heading = json.optString("Heading").ifBlank { query }
            if (abstractText.isNotBlank() && abstractUrl.startsWith("https://")) {
                list.add(ResearchSource(heading, abstractText.take(350), abstractUrl, "DuckDuckGo"))
            }
            return list
        }
    }
}
