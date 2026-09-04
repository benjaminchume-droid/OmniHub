package com.omnihub.source

import android.content.Context
import com.omnihub.data.SecureStore
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

class DescriptorSource(
    private val context: Context,
    private val descriptor: SourceDescriptor
) : AiSource {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    override val info = SourceInfo(
        id = descriptor.id,
        name = descriptor.name,
        kind = when (descriptor.kind.uppercase()) {
            "WEB_SESSION", "WEB" -> SourceKind.WEB_SESSION
            "HYBRID" -> SourceKind.HYBRID
            else -> SourceKind.API
        },
        authType = when (descriptor.authType.uppercase()) {
            "WEB_LOGIN" -> AuthType.WEB_LOGIN
            "BOTH" -> AuthType.BOTH
            "NONE" -> AuthType.NONE
            else -> AuthType.API_KEY
        },
        version = descriptor.version,
        description = descriptor.description,
        websiteUrl = descriptor.websiteUrl,
        bundled = false
    )

    override fun isConfigured(): Boolean {
        val key = SecureStore.getApiKey(context, descriptor.id)
        val session = SecureStore.getSession(context, descriptor.id)
        return !key.isNullOrBlank() || !session.isNullOrBlank()
    }

    override suspend fun configure(config: SourceConfig) {
        config.apiKey?.let { SecureStore.setApiKey(context, descriptor.id, it) }
        config.sessionCookies?.let { SecureStore.setSession(context, descriptor.id, it) }
    }

    override suspend fun chat(request: SourceChatRequest): ChatResponse = withContext(Dispatchers.IO) {
        val base = descriptor.baseUrl?.trimEnd('/') ?: error("${descriptor.name}: no baseUrl")
        val path = descriptor.chatPath ?: "/chat/completions"
        val model = request.model ?: descriptor.models.firstOrNull() ?: "default"
        val key = SecureStore.getApiKey(context, descriptor.id)
        val session = SecureStore.getSession(context, descriptor.id)

        val msgs = JSONArray()
        request.memoryContext?.takeIf { it.isNotBlank() }?.let {
            msgs.put(JSONObject().put("role", "system").put("content", it))
        }
        request.messages.forEach { m ->
            msgs.put(JSONObject().put("role", m.role).put("content", m.content))
        }

        val body = JSONObject()
            .put("model", model)
            .put("messages", msgs)
            .put("temperature", request.temperature)
        request.maxTokens?.let { body.put("max_tokens", it) }

        val builder = Request.Builder()
            .url("$base$path")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")

        if (!key.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $key")
        if (!session.isNullOrBlank()) builder.addHeader("Cookie", session)
        descriptor.headers.forEach { (k, v) -> builder.addHeader(k, v) }

        client.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string() ?: throw Exception("Empty response from ${descriptor.name}")
            if (!resp.isSuccessful) throw Exception("${descriptor.name} ${resp.code}: ${text.take(400)}")
            val json = JSONObject(text)
            val content = when {
                json.has("choices") ->
                    json.getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content")
                json.has("content") -> {
                    val c = json.get("content")
                    if (c is String) c else c.toString()
                }
                json.has("text") -> json.getString("text")
                else -> text
            }
            ChatResponse(content = content, model = model, providerId = descriptor.id, usageTokens = 0)
        }
    }
}
