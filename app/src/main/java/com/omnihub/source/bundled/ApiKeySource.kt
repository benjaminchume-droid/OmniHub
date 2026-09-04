package com.omnihub.source.bundled

import android.content.Context
import com.omnihub.data.SecureStore
import com.omnihub.providers.ChatResponse
import com.omnihub.source.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiKeySource(
    private val context: Context,
    private val sourceId: String,
    private val sourceName: String,
    private val baseUrl: String,
    private val defaultModels: List<String>,
    private val websiteUrl: String = "",
    private val description: String = "",
    private val authHeader: (String) -> Pair<String, String> = { key -> "Authorization" to "Bearer $key" },
    private val extraHeaders: Map<String, String> = emptyMap(),
    private val kind: SourceKind = SourceKind.API
) : AiSource {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    override val info = SourceInfo(
        id = sourceId, name = sourceName, kind = kind, authType = AuthType.API_KEY,
        description = description, websiteUrl = websiteUrl, bundled = true
    )

    override fun isConfigured(): Boolean = !SecureStore.getApiKey(context, sourceId).isNullOrBlank()

    override suspend fun configure(config: SourceConfig) {
        config.apiKey?.let { SecureStore.setApiKey(context, sourceId, it) }
    }

    override suspend fun chat(request: SourceChatRequest): ChatResponse = withContext(Dispatchers.IO) {
        val key = SecureStore.getApiKey(context, sourceId)
            ?: throw IllegalStateException("$sourceName: not configured \u2014 add API key")
        val model = request.model ?: defaultModels.first()
        val msgs = JSONArray()
        request.memoryContext?.takeIf { it.isNotBlank() }?.let {
            msgs.put(JSONObject().put("role", "system").put("content", it))
        }
        request.messages.forEach { m ->
            msgs.put(JSONObject().put("role", m.role).put("content", m.content))
        }
        val body = JSONObject().put("model", model).put("messages", msgs).put("temperature", request.temperature)
        request.maxTokens?.let { body.put("max_tokens", it) }
        val (hName, hVal) = authHeader(key)
        val httpReq = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .addHeader(hName, hVal).addHeader("Content-Type", "application/json")
            .apply { extraHeaders.forEach { (k, v) -> addHeader(k, v) } }
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(httpReq).execute().use { resp ->
            val text = resp.body?.string() ?: throw Exception("Empty response from $sourceName")
            if (!resp.isSuccessful) throw Exception("$sourceName ${resp.code}: ${text.take(500)}")
            val json = JSONObject(text)
            val content = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            val usage = json.optJSONObject("usage")?.optInt("total_tokens") ?: 0
            ChatResponse(content, model, sourceId, usage)
        }
    }
}

class AnthropicSource(private val context: Context) : AiSource {
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build()
    override val info = SourceInfo(id = "anthropic", name = "Claude (Anthropic)", kind = SourceKind.API, authType = AuthType.API_KEY,
        description = "Claude via official Messages API", websiteUrl = "https://console.anthropic.com", bundled = true)
    override fun isConfigured() = !SecureStore.getApiKey(context, "anthropic").isNullOrBlank()
    override suspend fun configure(config: SourceConfig) { config.apiKey?.let { SecureStore.setApiKey(context, "anthropic", it) } }
    override suspend fun chat(request: SourceChatRequest): ChatResponse = withContext(Dispatchers.IO) {
        val key = SecureStore.getApiKey(context, "anthropic") ?: throw IllegalStateException("Claude: not configured")
        val model = request.model ?: "claude-sonnet-4-20250514"
        val msgs = JSONArray()
        request.messages.filter { it.role != "system" }.forEach { m ->
            msgs.put(JSONObject().put("role", if (m.role == "assistant") "assistant" else "user").put("content", m.content))
        }
        val body = JSONObject().put("model", model).put("max_tokens", request.maxTokens ?: 4096).put("messages", msgs)
        val system = buildString {
            request.memoryContext?.takeIf { it.isNotBlank() }?.let { append(it).append("\n") }
            request.messages.filter { it.role == "system" }.forEach { append(it.content).append("\n") }
        }.trim()
        if (system.isNotBlank()) body.put("system", system)
        val httpReq = Request.Builder().url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", key).addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(httpReq).execute().use { resp ->
            val text = resp.body?.string() ?: throw Exception("Empty Claude response")
            if (!resp.isSuccessful) throw Exception("Claude ${resp.code}: ${text.take(500)}")
            val content = JSONObject(text).getJSONArray("content").getJSONObject(0).getString("text")
            ChatResponse(content, model, "anthropic", 0)
        }
    }
}

class GeminiSource(private val context: Context) : AiSource {
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build()
    override val info = SourceInfo(id = "gemini", name = "Gemini", kind = SourceKind.API, authType = AuthType.API_KEY,
        description = "Google Gemini generateContent", websiteUrl = "https://aistudio.google.com", bundled = true)
    override fun isConfigured() = !SecureStore.getApiKey(context, "gemini").isNullOrBlank()
    override suspend fun configure(config: SourceConfig) { config.apiKey?.let { SecureStore.setApiKey(context, "gemini", it) } }
    override suspend fun chat(request: SourceChatRequest): ChatResponse = withContext(Dispatchers.IO) {
        val key = SecureStore.getApiKey(context, "gemini") ?: throw IllegalStateException("Gemini: not configured")
        val model = request.model ?: "gemini-2.0-flash"
        val contents = JSONArray()
        request.messages.filter { it.role != "system" }.forEach { m ->
            contents.put(JSONObject().put("role", if (m.role == "assistant") "model" else "user")
                .put("parts", JSONArray().put(JSONObject().put("text", m.content))))
        }
        val body = JSONObject().put("contents", contents)
        val system = request.memoryContext.orEmpty()
        if (system.isNotBlank()) {
            body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
        val httpReq = Request.Builder().url(url).addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(httpReq).execute().use { resp ->
            val text = resp.body?.string() ?: throw Exception("Empty Gemini response")
            if (!resp.isSuccessful) throw Exception("Gemini ${resp.code}: ${text.take(500)}")
            val content = JSONObject(text).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            ChatResponse(content, model, "gemini", 0)
        }
    }
}

class WebSessionSource(
    private val context: Context,
    private val sourceId: String,
    private val sourceName: String,
    private val loginUrl: String,
    private val description: String = "Web session \u2014 sign in, cookies captured"
) : AiSource {
    override val info = SourceInfo(
        id = sourceId, name = sourceName, kind = SourceKind.WEB_SESSION, authType = AuthType.WEB_LOGIN,
        description = description, websiteUrl = loginUrl, bundled = true
    )
    override fun isConfigured(): Boolean =
        !SecureStore.getSession(context, sourceId).isNullOrBlank() ||
            !SecureStore.getSession(context, "web_$sourceId").isNullOrBlank() ||
            !SecureStore.getApiKey(context, sourceId).isNullOrBlank()
    override suspend fun configure(config: SourceConfig) {
        config.sessionCookies?.let {
            SecureStore.setSession(context, sourceId, it)
            SecureStore.setSession(context, "web_$sourceId", it)
        }
        config.apiKey?.let { SecureStore.setApiKey(context, sourceId, it) }
    }
    override suspend fun chat(request: SourceChatRequest): ChatResponse {
        val key = SecureStore.getApiKey(context, sourceId)
        if (!key.isNullOrBlank()) {
            throw IllegalStateException("$sourceName has API key \u2014 use the API source for $sourceId")
        }
        val cookies = SecureStore.getSession(context, sourceId) ?: SecureStore.getSession(context, "web_$sourceId")
        if (cookies.isNullOrBlank()) {
            throw IllegalStateException("$sourceName: not signed in \u2014 Configure \u2192 Web login")
        }
        throw IllegalStateException(
            "$sourceName web protocol needs a source update. Session saved. Use API key for reliable chat."
        )
    }
}
