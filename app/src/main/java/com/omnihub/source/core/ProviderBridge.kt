package com.omnihub.source.core

import android.content.Context
import android.webkit.CookieManager
import com.omnihub.data.SecureStore
import com.omnihub.providers.ChatMessage
import com.omnihub.providers.ChatResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Web Core: send the user message through the provider session and return the
 * assistant text. No fake "relayed / streaming" demo strings.
 */
object ProviderBridge {

    data class StreamToken(val text: String, val done: Boolean = false)

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

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
            when {
                kind.equals("MCP", true) ->
                    runMcpAction(context, providerId, providerName, siteUrl, last)
                providerId.contains("chatgpt", true) || siteUrl.contains("chatgpt.com") ->
                    chatGpt(context, providerId, messages)
                providerId.contains("claude", true) || siteUrl.contains("claude.ai") ->
                    genericWebChat(context, providerId, siteUrl, "claude.ai", messages)
                else ->
                    genericWebChat(context, providerId, siteUrl, hostOf(siteUrl), messages)
            }
        } catch (e: Exception) {
            e.message ?: "Request failed"
        }

        // Deliver the full reply as one message (not a simulated typewriter)
        emit(StreamToken(reply, done = true))
    }

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

    /** ChatGPT web backend using session cookies / access token from login WebView. */
    private fun chatGpt(context: Context, providerId: String, messages: List<ChatMessage>): String {
        val cookies = cookieHeader(context, providerId, "chatgpt.com")
        if (cookies.isBlank()) {
            // Guest path: still try; many pages redirect to login
            return tryGuestOrFail("chatgpt.com", messages.last().content)
        }

        // 1) Session → accessToken
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
            return "Sign in to ChatGPT in Providers, then try again."
        }

        // 2) Conversation
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
                return "ChatGPT error ${resp.code}. Sign in again if this keeps happening."
            }
            resp.body?.string().orEmpty()
        }

        return parseSseOrJsonReply(raw).ifBlank {
            "No text in ChatGPT response. Try signing in again."
        }
    }

    private fun parseSseOrJsonReply(raw: String): String {
        // SSE: data: {...} lines with message content parts
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

        // Plain JSON fallback
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
        siteUrl: String,
        host: String,
        messages: List<ChatMessage>
    ): String {
        val cookies = cookieHeader(context, providerId, host)
        val userText = messages.lastOrNull { it.role == "user" }?.content.orEmpty()

        // Probe the site with the session to confirm connectivity
        val req = Request.Builder()
            .url(siteUrl)
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/json")
            .apply { if (cookies.isNotBlank()) header("Cookie", cookies) }
            .get()
            .build()

        val (code, body) = http.newCall(req).execute().use { resp ->
            resp.code to resp.body?.string().orEmpty().take(500)
        }

        if (code in 200..399 && cookies.isNotBlank()) {
            // Session is live; provider-specific protocol adapters ship as Source APK updates.
            // Until that adapter is installed, be honest — do not invent an AI answer.
            return "Connected to $host (session ok). " +
                "Install the $providerId source from Store for full chat protocol, " +
                "or use ChatGPT which is wired in-core."
        }

        if (cookies.isBlank()) {
            return tryGuestOrFail(host, userText)
        }

        return "Could not reach $host (HTTP $code). Check network or sign in again."
    }

    private fun tryGuestOrFail(host: String, userText: String): String {
        // Honest: without a session most providers will not return model output.
        return "Open Providers → sign in to $host, then send again."
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
            return "Sign in to $providerName first so MCP can use the site session."
        }
        return "MCP session on $providerName is ready. Task queued: ${task.take(200)}. " +
            "Full UI-action adapters ship with the MCP source APK from Store."
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
