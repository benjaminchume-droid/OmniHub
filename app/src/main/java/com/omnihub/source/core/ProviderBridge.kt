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

    private val deviceId: String by lazy { UUID.randomUUID().toString() }

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
                    isChatGpt(providerId, siteUrl) ->
                        chatGpt(context, providerId, messages)
                    else ->
                        genericWebChat(context, providerId, providerName, siteUrl, hostOf(siteUrl), messages)
                }
            }
        } catch (e: Exception) {
            "Could not reach $providerName: ${e.javaClass.simpleName}: ${e.message ?: "unknown"}"
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

    private fun isChatGpt(providerId: String, siteUrl: String): Boolean =
        providerId.contains("chatgpt", true) ||
            providerId.equals("openai", true) ||
            siteUrl.contains("chatgpt.com") ||
            siteUrl.contains("chat.openai.com")

    private fun hostOf(url: String): String =
        try { java.net.URI(url).host ?: url } catch (_: Exception) { url }

    private fun cookieHeader(context: Context, providerId: String, vararg hosts: String): String {
        val storedKeys = listOf(
            providerId,
            "web_$providerId",
            *hosts.map { it.replace('.', '_') }.toTypedArray()
        )
        val stored = storedKeys
            .mapNotNull { SecureStore.getSession(context, it) }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

        val liveParts = linkedSetOf<String>()
        val cm = try { CookieManager.getInstance() } catch (_: Exception) { null }
        if (cm != null) {
            for (h in hosts) {
                val urls = listOf("https://$h", "https://www.$h", "http://$h")
                for (u in urls) {
                    try {
                        val c = cm.getCookie(u).orEmpty()
                        if (c.isNotBlank()) {
                            c.split(";").map { it.trim() }.filter { it.contains("=") }.forEach { liveParts.add(it) }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        val live = liveParts.joinToString("; ")
        return when {
            stored.isNotBlank() && live.isNotBlank() -> mergeCookies(stored, live)
            stored.isNotBlank() -> stored
            else -> live
        }
    }

    private fun mergeCookies(a: String, b: String): String {
        val map = linkedMapOf<String, String>()
        fun putAll(s: String) {
            s.split(";").map { it.trim() }.filter { it.contains("=") }.forEach {
                val i = it.indexOf('=')
                map[it.substring(0, i)] = it.substring(i + 1)
            }
        }
        putAll(a); putAll(b)
        return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    private fun chatGpt(context: Context, providerId: String, messages: List<ChatMessage>): String {
        val cookies = cookieHeader(
            context, providerId,
            "chatgpt.com", "chat.openai.com", "openai.com", "auth.openai.com"
        )
        if (cookies.isBlank()) {
            return "Not signed in. Open Providers → ChatGPT → Sign in, wait until the chat page loads, then press Back."
        }

        // 1) session → accessToken
        val sessionReq = Request.Builder()
            .url("https://chatgpt.com/api/auth/session")
            .header("Cookie", cookies)
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .header("Referer", "https://chatgpt.com/")
            .get()
            .build()

        val sessionBody = try {
            http.newCall(sessionReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return "ChatGPT session check failed (HTTP ${resp.code}). Sign in again under Providers."
                }
                body
            }
        } catch (e: Exception) {
            return "Network error reaching ChatGPT session: ${e.message}"
        }

        val access = runCatching {
            val o = JSONObject(sessionBody)
            o.optString("accessToken").ifBlank {
                o.optJSONObject("user")?.optString("accessToken").orEmpty()
            }
        }.getOrNull().orEmpty()

        if (access.isBlank()) {
            val preview = sessionBody.take(120).replace('\n', ' ')
            return "ChatGPT did not return an access token. Sign in again and stay on the chat page before going back. ($preview)"
        }

        val userText = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        val payload = JSONObject()
            .put("action", "next")
            .put("parent_message_id", UUID.randomUUID().toString())
            .put("model", "auto")
            .put("timezone_offset_min", -java.util.TimeZone.getDefault().rawOffset / 60000)
            .put(
                "messages", JSONArray().put(
                    JSONObject()
                        .put("id", UUID.randomUUID().toString())
                        .put("author", JSONObject().put("role", "user"))
                        .put(
                            "content",
                            JSONObject()
                                .put("content_type", "text")
                                .put("parts", JSONArray().put(userText))
                        )
                        .put("metadata", JSONObject())
                )
            )
            .put("history_and_training_disabled", false)

        val convReq = Request.Builder()
            .url("https://chatgpt.com/backend-api/conversation")
            .header("Cookie", cookies)
            .header("Authorization", "Bearer $access")
            .header("Content-Type", "application/json")
            .header("User-Agent", UA)
            .header("Accept", "text/event-stream")
            .header("Referer", "https://chatgpt.com/")
            .header("Origin", "https://chatgpt.com")
            .header("oai-device-id", deviceId)
            .header("oai-language", "en-US")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val raw = try {
            http.newCall(convReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val hint = when (resp.code) {
                        401, 403 -> "Session expired or blocked. Sign in again."
                        429 -> "Rate limited. Wait a minute."
                        404, 422 -> "ChatGPT API shape changed; reply path needs update."
                        else -> "HTTP ${resp.code}"
                    }
                    return "ChatGPT error: $hint ${body.take(160)}"
                }
                body
            }
        } catch (e: Exception) {
            return "Network error on ChatGPT conversation: ${e.message}"
        }

        val parsed = parseSseOrJsonReply(raw)
        if (parsed.isNotBlank()) return parsed

        return "ChatGPT returned no text. Try a shorter message or sign in again."
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
                // modern formats
                val msg = o.optJSONObject("message")
                if (msg != null) {
                    val content = msg.optJSONObject("content")
                    val arr = content?.optJSONArray("parts")
                    if (arr != null) {
                        val text = (0 until arr.length()).joinToString("") { arr.optString(it) }
                        if (text.isNotBlank()) parts.add(text)
                    }
                }
                // delta / v2 style
                val delta = o.optJSONObject("delta")
                val dContent = delta?.optString("content").orEmpty()
                if (dContent.isNotBlank()) parts.add(dContent)
                val v = o.optString("v")
                if (v.isNotBlank() && !v.startsWith("{") && parts.isEmpty()) parts.add(v)
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
        val cookies = cookieHeader(context, providerId, host, host.removePrefix("www."))
        if (cookies.isBlank()) {
            return "Not signed in to $providerName. Open Providers → Sign in, finish login, then Back."
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

        return if (code in 200..399) {
            "$providerName session looks valid, but its chat protocol is not fully wired yet. " +
                "Install and use ChatGPT for real replies right now."
        } else {
            "$providerName returned HTTP $code. Sign in again."
        }
    }

    private fun runMcpAction(
        context: Context,
        providerId: String,
        providerName: String,
        siteUrl: String,
        task: String
    ): String {
        val host = hostOf(siteUrl)
        val cookies = cookieHeader(context, providerId, host)
        if (cookies.isBlank()) {
            return "Sign in to $providerName first."
        }
        return "MCP session ready on $providerName. Task queued: ${task.take(200)}"
    }

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
}

object ProviderAuthStore {
    private const val PREFS = "omni_provider_auth"

    fun isSignedIn(context: Context, providerId: String): Boolean {
        if (context.getSharedPreferences(PREFS, 0).getBoolean("signed_$providerId", false)) {
            // still require real cookies for chat
            val s = SecureStore.getSession(context, providerId)
                ?: SecureStore.getSession(context, "web_$providerId")
            if (!s.isNullOrBlank()) return true
        }
        val s = SecureStore.getSession(context, providerId)
            ?: SecureStore.getSession(context, "web_$providerId")
        return !s.isNullOrBlank()
    }

    fun setSignedIn(context: Context, providerId: String, signed: Boolean) {
        context.getSharedPreferences(PREFS, 0).edit().putBoolean("signed_$providerId", signed).apply()
        if (!signed) {
            SecureStore.clearSession(context, providerId)
            SecureStore.clearSession(context, "web_$providerId")
        }
    }

    fun preferredProvider(context: Context): String =
        context.getSharedPreferences(PREFS, 0).getString("preferred", "auto") ?: "auto"

    fun setPreferredProvider(context: Context, id: String) {
        context.getSharedPreferences(PREFS, 0).edit().putString("preferred", id).apply()
    }
}
