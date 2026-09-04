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

abstract class BaseApiProvider(
    override val id: String,
    override val name: String,
    private val baseUrl: String,
    private val apiKey: String,
    override val models: List<ModelInfo>
) : AiProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    override suspend fun chat(request: ChatRequest): ChatResponse = withContext(Dispatchers.IO) {
        val modelId = request.model.ifBlank { models.firstOrNull()?.id ?: "gpt-4o-mini" }
        val body = JSONObject().apply {
            put("model", modelId)
            put("messages", JSONArray().apply {
                request.messages.forEach { msg ->
                    put(JSONObject().put("role", msg.role).put("content", msg.content))
                }
            })
            put("temperature", request.temperature)
            request.maxTokens?.let { put("max_tokens", it) }
        }

        val httpReq = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(httpReq).execute().use { resp ->
            val text = resp.body?.string() ?: throw Exception("Empty response from $name")
            if (!resp.isSuccessful) throw Exception("$name error ${resp.code}: $text")
            val json = JSONObject(text)
            val content = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            val usage = json.optJSONObject("usage")?.optInt("total_tokens") ?: 0
            ChatResponse(content = content, model = modelId, providerId = id, usageTokens = usage)
        }
    }
}
