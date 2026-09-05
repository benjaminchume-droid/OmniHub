package com.omnihub.source.core

import com.omnihub.providers.ChatMessage
import com.omnihub.providers.ChatResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiCore {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    data class ApiCall(
        val baseUrl: String,
        val path: String = "/chat/completions",
        val apiKey: String,
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double = 0.7,
        val maxTokens: Int? = null,
        val authStyle: AuthStyle = AuthStyle.BEARER,
        val extraHeaders: Map<String, String> = emptyMap(),
        val providerId: String
    )

    enum class AuthStyle { BEARER, ANTHROPIC_X_API_KEY, QUERY }

    suspend fun chatCompletions(call: ApiCall): ChatResponse = withContext(Dispatchers.IO) {
        when (call.authStyle) {
            AuthStyle.ANTHROPIC_X_API_KEY -> anthropic(call)
            else -> openAiCompatible(call)
        }
    }

    private fun openAiCompatible(call: ApiCall): ChatResponse {
        val body = JSONObject().apply {
            put("model", call.model)
            put("messages", JSONArray().apply {
                call.messages.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) }
            })
            put("temperature", call.temperature)
            call.maxTokens?.let { put("max_tokens", it) }
        }
        val url = call.baseUrl.trimEnd('/') + call.path
        val builder = Request.Builder().url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer ${call.apiKey}")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        call.extraHeaders.forEach { (k, v) -> builder.addHeader(k, v) }
        client.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string() ?: throw Exception("Empty response")
            if (!resp.isSuccessful) throw Exception("${call.providerId} ${resp.code}: ${text.take(400)}")
            val json = JSONObject(text)
            val content = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            val usage = json.optJSONObject("usage")?.optInt("total_tokens") ?: 0
            return ChatResponse(content = content, model = call.model, providerId = call.providerId, usageTokens = usage)
        }
    }

    private fun anthropic(call: ApiCall): ChatResponse {
        var system: String? = null
        val msgs = JSONArray()
        call.messages.forEach { m ->
            if (m.role == "system") system = (system?.let { "$it\n${m.content}" } ?: m.content)
            else msgs.put(JSONObject().put("role", m.role).put("content", m.content))
        }
        val body = JSONObject().apply {
            put("model", call.model)
            put("max_tokens", call.maxTokens ?: 4096)
            put("messages", msgs)
            system?.let { put("system", it) }
        }
        val url = call.baseUrl.trimEnd('/') + "/v1/messages"
        val req = Request.Builder().url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-api-key", call.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: throw Exception("Empty Anthropic response")
            if (!resp.isSuccessful) throw Exception("anthropic ${resp.code}: ${text.take(400)}")
            val content = JSONObject(text).getJSONArray("content").getJSONObject(0).getString("text")
            return ChatResponse(content = content, model = call.model, providerId = call.providerId)
        }
    }
}
