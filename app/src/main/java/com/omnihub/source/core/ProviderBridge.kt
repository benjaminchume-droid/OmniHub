package com.omnihub.source.core

import android.content.Context
import android.webkit.CookieManager
import com.omnihub.data.SecureStore
import com.omnihub.providers.ChatMessage
import com.omnihub.providers.ChatResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

object ProviderBridge {

    data class StreamToken(val text: String, val done: Boolean = false)

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Always runs network on Dispatchers.IO — never Main. */
    fun streamChat(
        context: Context,
        providerId: String,
        providerName: String,
        siteUrl: String,
        messages: List<ChatMessage>,
        kind: String = "WEB"
    ): Flow<StreamToken> = flow {
        val last = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        if (last.isBlank()) {
            emit(StreamToken("Empty message.", done = true))
            return@flow
        }

        val reply = try {
            withContext(Dispatchers.IO) {
                when {
                    kind.equals("MCP", true) ->
                        runMcpAction(context, providerId, providerName, siteUrl, last)
                    providerId.contains("chatgpt", true) || siteUrl.contains("chatgpt.com") ->
                        chatGpt(context, providerId, messages)
                    else ->
                        genericWebChat(context, providerId, providerName, siteUrl, hostOf(siteUrl), messages)
                }
            }
        } catch (e: Exception) {
            "Could not reach $providerName: ${e.javaClass.simpleName} ${e.message ?: ""}".trim()
        }

        emit(StreamToken(reply, done = true))
    }.flowOn(Dispatchers.IO)

    suspend fun chatOnce(
        context: Context,
        providerId: String,
        providerName: String,
        siteUrl: String,
        messages: List<ChatMessage>,
        kind: String = "WEB"
    ): ChatResponse = withContext(Dispatchers.IO) {
        val buf = StringBuilder()
        streamChat(context, providerId, providerName, siteUrl, messages, kind).collect { tok ->
            if (tok.text.isNotEmpty()) buf.append(tok.text)
        }
        ChatResponse(
            content = buf.toString().ifBlank { "No reply." },
            model = providerName,
            providerId = providerId
        )
    }

    private fun hostOf(url: String): String =
        try { java.net.URI(url).host ?: url } catch (_: Exception) { url }

    private fun cookieHeader(context: Context, providerId: String, host: String): String {
        val stored = listOf(
            SecureStore.getSession(context, providerId),
            SecureStore.getSession(context, "web_$providerId"),
            SecureStore.getSession(context, host.replace('.', '_'))
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
        val live = try {
            CookieManager.getInstance().getCookie("https://$host").orEmpty()
        } catch (_: Exception) { "" }
        return when {
            stored.isNotBlank() && live.isNotBlank() -> "$stored; $live"
            stored.isNotBlank() -> stored
            else -> live
        }
    }

    private fun chatGpt(context: Context, providerId: String, messages: List<ChatMessage>): String {
        val cookies = cookieHeader(context, providerId, "chatgpt.com")
        if (cookies.isBlank()) {
            return "Sign in to ChatGPT under Providers, then send again."
        }

        val sessionReq = Request.Builder()
            .url("https://chatgpt.com/api/auth/session")
            .header("Cookie", cookies)
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .get()
            .build()
        val sessionBody = http.newCall(sessionReq).execute().use { it.body?.string().orEmpty() }
        val access = runCatching { JSONObject(sessionBody).optString("accessToken") }.getOrNull().orEmpty()

        if (access.isBlank()) {
            return "ChatGPT session expired. Open Providers → Sign in again."
        }

        val userText = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        val payload = JSONObject()
            .put("action", "next")
            .put("parent_message_id", UUID.randomUUID().toString())
            .put("model", "auto")
            .put(
                "messages", JSONArray().put(
                    JSONObject()
                        .put("id", UUID.randomUUID().toString())
                        .put("author", JSONObject().put("role", "user"))
                        .put("content", JSONObject().put("content_type", "text").put("parts", JSONArray().put(userText)))
                )
            )

        val convReq = Request.Builder()
            .url("https://chatgpt.com/backend-api/conversation")
            .header("Cookie", cookies)
            .header("Authorization", "Bearer $access")
            .header("Content-Type", "application/json")
            .header("User-Agent", UA)
            .header("Accept", "text/event-stream")
            .header("Referer", "https://chatgpt.com/")
            .header("Origin", "https://chatgpt.com")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val raw = http.newCall(convReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                return "ChatGPT returned ${resp.code}. Sign in again if this continues."
            }
            resp.body?.string().orEmpty()
        }

        return parseSseOrJsonReply(raw).ifBlank {
            "ChatGPT returned no text. Try again or sign in again."
        }
    }

    private fun parseSseOrJsonReply(raw: String): String {
        val parts = mutableListOf<String>()
        raw.lineSequence().forEach { line ->
            val t = line.trim()
            if (!t.startsWith("data:")) return@forEach
            val json = t.removePrefix("data:").trim()
            if (json == "[DONE]" || json.isBlank()) return@forEach
            runCatching {
                val o = JSONObject(json)
                val msg = o.optJSONObject("message") ?: return@runCatching
                val content = msg.optJSONObject("content") ?: return@runCatching
                val arr = content.optJSONArray("parts") ?: return@runCatching
                val text = (0 until arr.length()).joinToString("") { arr.optString(it) }
                if (text.isNotBlank()) parts.add(text)
            }
        }
        if (parts.isNotEmpty()) return parts.last()
        runCatching {
            val o = JSONObject(raw)
            val msg = o.optJSONObject("message")
            val content = msg?.optJSONObject("content")
            val arr = content?.optJSONArray("parts")
            if (arr != null && arr.length() > 0) return arr.optString(0)
        }
        return ""
    }

    private fun genericWebChat(
        context: Context,
        providerId: String,
        providerName: String,
        siteUrl: String,
        host: String,
        messages: List<ChatMessage>
    ): String {
        val cookies = cookieHeader(context, providerId, host)
        if (cookies.isBlank()) {
            return "Sign in to $providerName under Providers, then send again."
        }

        val req = Request.Builder()
            .url(siteUrl.ifBlank { "https://$host" })
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/json")
            .header("Cookie", cookies)
            .get()
            .build()

        val code = try {
            http.newCall(req).execute().use { it.code }
        } catch (e: Exception) {
            return "Network error talking to $providerName: ${e.message}"
        }

        if (code in 200..399) {
            return "$providerName session is active, but full chat protocol for this provider is not wired yet. " +
                "Use ChatGPT for real replies, or wait for a source update."
        }
        return "$providerName returned HTTP $code. Sign in again."
    }

    private fun runMcpAction(
        context: Context,
        providerId: String,
        providerName: String,
        siteUrl: String,
        task: String
    ): String {
        val cookies = cookieHeader(context, providerId, hostOf(siteUrl))
        if (cookies.isBlank()) {
            return "Sign in to $providerName first."
        }
        return "MCP session ready on $providerName. Task: ${task.take(200)}"
    }

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
}

object ProviderAuthStore {
    private const val PREFS = "omni_provider_auth"

    fun isSignedIn(context: Context, providerId: String): Boolean {
        if (context.getSharedPreferences(PREFS, 0).getBoolean("signed_$providerId", false)) return true
        val s = SecureStore.getSession(context, providerId)
            ?: SecureStore.getSession(context, "web_$providerId")
        return !s.isNullOrBlank()
    }

    fun setSignedIn(context: Context, providerId: String, signed: Boolean) {
        context.getSharedPreferences(PREFS, 0).edit().putBoolean("signed_$providerId", signed).apply()
    }

    fun preferredProvider(context: Context): String =
        context.getSharedPreferences(PREFS, 0).getString("preferred", "auto") ?: "auto"

    fun setPreferredProvider(context: Context, id: String) {
        context.getSharedPreferences(PREFS, 0).edit().putString("preferred", id).apply()
    }
}
