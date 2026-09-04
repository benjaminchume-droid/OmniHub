package com.omnihub.providers.impl

import com.omnihub.providers.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Real Anthropic Messages API provider. */
class AnthropicProvider(private val apiKey: String) : AiProvider {
    override val id = "anthropic"
    override val name = "Anthropic"
    override val models = listOf(
        ModelInfo("claude-sonnet-4-20250514", "Claude Sonnet 4", 0.003, 0.015, 700, 0.96, supportsTools = true),
        ModelInfo("claude-3-5-haiku-20241022", "Claude 3.5 Haiku", 0.0008, 0.004, 400, 0.94)
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    override suspend fun chat(request: ChatRequest): ChatResponse = withContext(Dispatchers.IO) {
        val model = request.model.ifBlank { models.first().id }
        val system = request.messages.filter { it.role == "system" }.joinToString("\n") { it.content }
        val msgs = JSONArray()
        request.messages.filter { it.role != "system" }.forEach { m ->
            msgs.put(JSONObject().put("role", m.role).put("content", m.content))
        }
        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", request.maxTokens ?: 4096)
            put("messages", msgs)
            if (system.isNotBlank()) put("system", system)
        }
        val httpReq = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(httpReq).execute().use { resp ->
            val text = resp.body?.string() ?: throw Exception("Empty response from Anthropic")
            if (!resp.isSuccessful) throw Exception("Anthropic error ${resp.code}: $text")
            val json = JSONObject(text)
            val content = json.getJSONArray("content").getJSONObject(0).getString("text")
            ChatResponse(content = content, model = model, providerId = id)
        }
    }
}
