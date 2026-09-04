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

class GeminiProvider(private val apiKey: String) : AiProvider {
    override val id = "gemini"
    override val name = "Google Gemini"
    override val models = listOf(
        ModelInfo("gemini-2.0-flash", "Gemini 2.0 Flash", 0.0, 0.0, 400, 0.93, supportsVision = true)
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    override suspend fun chat(request: ChatRequest): ChatResponse = withContext(Dispatchers.IO) {
        val contents = JSONArray()
        request.messages.filter { it.role != "system" }.forEach { msg ->
            contents.put(
                JSONObject()
                    .put("role", if (msg.role == "assistant") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
            )
        }
        val body = JSONObject().put("contents", contents)
        val system = request.messages.filter { it.role == "system" }.joinToString("\n") { it.content }
        if (system.isNotBlank()) {
            body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        }

        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/${request.model}:generateContent?key=$apiKey"
        val httpReq = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(httpReq).execute().use { resp ->
            val text = resp.body?.string() ?: throw Exception("Empty response from Gemini")
            if (!resp.isSuccessful) throw Exception("Gemini error ${resp.code}: $text")
            val json = JSONObject(text)
            val content = json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            ChatResponse(content = content, model = request.model, providerId = id)
        }
    }
}
