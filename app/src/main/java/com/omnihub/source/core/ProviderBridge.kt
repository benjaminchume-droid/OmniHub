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
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.TimeUnit

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

        if (ProviderCooldown.isCooling(context, providerId)) {
            emit(StreamToken(ProviderCooldown.message(context, providerId, providerName), done = true))
            return@flow
        }

        val reply = try {
            when {
                kind.equals("MCP", true) ->
                    withContext(Dispatchers.IO) {
                        runMcpAction(context, providerId, providerName, siteUrl, last)
                    }
                else ->
                    // Primary path: real page in WebView (not cookie → OkHttp)
                    webViewSend(context, providerId, providerName, siteUrl, last)
            }
        } catch (e: Exception) {
            "Could not reach $providerName: ${e.message ?: e.javaClass.simpleName}"
        }

        if (looksBlocked(reply)) {
            ProviderCooldown.markBlocked(context, providerId, 15)
        }

        emit(StreamToken(reply, done = true))
    }.flowOn(Dispatchers.IO)

    /**
     * Try preferred provider, then other installed sources (skip cooling).
     * Used when preferred == auto or preferred fails with a hard block.
     */
    fun streamChatWithFallback(
        context: Context,
        candidates: List<Triple<String, String, String>>, // id, name, url
        messages: List<ChatMessage>,
        kind: String = "WEB"
    ): Flow<StreamToken> = flow {
        if (candidates.isEmpty()) {
            emit(StreamToken("No source installed. Open Store → install → Sign in.", done = true))
            return@flow
        }
        val errors = mutableListOf<String>()
        for ((id, name, url) in candidates) {
            if (ProviderCooldown.isCooling(context, id)) {
                errors += ProviderCooldown.message(context, id, name)
                continue
            }
            val last = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
            val reply = try {
                webViewSend(context, id, name, url, last)
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
            if (looksBlocked(reply)) {
                ProviderCooldown.markBlocked(context, id, 15)
                errors += reply
                continue
            }
            if (reply.startsWith("Not signed in") || reply.contains("input not found", true)) {
                errors += "$name: $reply"
                continue
            }
            // Success or normal soft error — return to user
            emit(StreamToken(reply, done = true))
            return@flow
        }
        emit(
            StreamToken(
                "All providers failed or are cooling down.\n" +
                    errors.distinct().take(3).joinToString("\n"),
                done = true
            )
        )
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

    private suspend fun webViewSend(
        context: Context,
        providerId: String,
        providerName: String,
        siteUrl: String,
        userText: String
    ): String {
        // Require a session so the WebView is actually logged in
        val host = hostOf(siteUrl.ifBlank { "https://chatgpt.com" })
        val cookies = cookieHeader(context, providerId, host, host.removePrefix("www."),
            "chatgpt.com", "auth.openai.com")
        if (cookies.isBlank() && !ProviderAuthStore.isSignedIn(context, providerId)) {
            return "Not signed in to $providerName. Providers → Sign in → stay on chat → Back."
        }

        val result = WebViewChatEngine.send(
            context = context,
            siteUrl = siteUrl.ifBlank { "https://$host" },
            providerId = providerId,
            userMessage = userText
        )
        return if (result.ok) result.text
        else {
            val err = result.text
            if (looksBlocked(err)) {
                ProviderCooldown.markBlocked(context, providerId, 15)
                "$providerName blocked this session. Cooling down 15m. " +
                    "Switch provider or long-press → Add account."
            } else err
        }
    }

    private fun looksBlocked(text: String): Boolean {
        val t = text.lowercase()
        return t.contains("unusual activity") ||
            t.contains("session expired or blocked") ||
            t.contains("cooling down") && t.contains("blocked")
    }

    private fun hostOf(url: String): String =
        try { java.net.URI(url).host ?: url } catch (_: Exception) { url }

    private fun cookieHeader(context: Context, providerId: String, vararg hosts: String): String {
        val active = AccountStore.activeAccountId(context, providerId)
        val sessionKeys = listOf(
            AccountStore.sessionKey(providerId, active),
            providerId,
            "web_$providerId"
        )
        val stored = sessionKeys
            .mapNotNull { SecureStore.getSession(context, it) }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

        val liveParts = linkedSetOf<String>()
        val cm = try { CookieManager.getInstance() } catch (_: Exception) { null }
        if (cm != null) {
            for (h in hosts) {
                for (u in listOf("https://$h", "https://www.$h")) {
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

    private fun runMcpAction(
        context: Context,
        providerId: String,
        providerName: String,
        siteUrl: String,
        task: String
    ): String {
        val cookies = cookieHeader(context, providerId, hostOf(siteUrl))
        if (cookies.isBlank()) return "Sign in to $providerName first."
        return "MCP session ready on $providerName. Task: ${task.take(200)}"
    }
}

object ProviderAuthStore {
    private const val PREFS = "omni_provider_auth"

    fun isSignedIn(context: Context, providerId: String): Boolean {
        val active = AccountStore.activeAccountId(context, providerId)
        val keys = listOf(
            AccountStore.sessionKey(providerId, active),
            providerId,
            "web_$providerId"
        )
        if (keys.any { !SecureStore.getSession(context, it).isNullOrBlank() }) return true
        return context.getSharedPreferences(PREFS, 0).getBoolean("signed_$providerId", false)
    }

    fun setSignedIn(context: Context, providerId: String, signed: Boolean) {
        context.getSharedPreferences(PREFS, 0).edit().putBoolean("signed_$providerId", signed).apply()
        if (!signed) {
            SecureStore.clearSession(context, providerId)
            SecureStore.clearSession(context, "web_$providerId")
            val active = AccountStore.activeAccountId(context, providerId)
            if (!active.isNullOrBlank()) {
                SecureStore.clearSession(context, AccountStore.sessionKey(providerId, active))
            }
            ProviderCooldown.clear(context, providerId)
        }
    }

    fun preferredProvider(context: Context): String =
        context.getSharedPreferences(PREFS, 0).getString("preferred", "auto") ?: "auto"

    fun setPreferredProvider(context: Context, id: String) {
        context.getSharedPreferences(PREFS, 0).edit().putString("preferred", id).apply()
    }
}
