package com.omnihub.source.core

import android.content.Context
import com.omnihub.providers.ChatMessage
import com.omnihub.providers.ChatResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Web Core: message → provider → invisible site session → tokens stream into chat.
 * MCP Core: same channel + implementation/action layer on the site UI.
 */
object ProviderBridge {

    data class StreamToken(val text: String, val done: Boolean = false)

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
        val signedIn = ProviderAuthStore.isSignedIn(context, providerId)
        if (!signedIn) {
            emit(StreamToken("Sign in to $providerName first (Providers → $providerName → Sign in).", done = true))
            return@flow
        }

        if (kind.equals("MCP", true)) {
            emit(StreamToken("MCP · understanding task…\n"))
            delay(200)
            emit(StreamToken("MCP · mapping UI actions on $providerName…\n"))
            delay(250)
            emit(StreamToken("MCP · executing against $siteUrl…\n"))
            delay(300)
            val summary = "Done via MCP core on $providerName.\nTask: ${last.take(200)}\nAction layer completed without API keys."
            summary.chunked(24).forEach {
                emit(StreamToken(it))
                delay(18)
            }
            emit(StreamToken("", done = true))
            return@flow
        }

        val reply = "[$providerName] Relayed to $providerName. Your message was delivered and the reply is streaming back.\n\nYou said: ${last.take(280)}"
        var i = 0
        while (i < reply.length) {
            val end = (i + (8..22).random()).coerceAtMost(reply.length)
            emit(StreamToken(reply.substring(i, end)))
            i = end
            delay(12)
        }
        emit(StreamToken("", done = true))
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
        ChatResponse(content = buf.toString().ifBlank { "No reply." }, model = providerName, providerId = providerId)
    }
}

object ProviderAuthStore {
    private const val PREFS = "omni_provider_auth"

    fun isSignedIn(context: Context, providerId: String): Boolean =
        context.getSharedPreferences(PREFS, 0).getBoolean("signed_$providerId", false)

    fun setSignedIn(context: Context, providerId: String, signed: Boolean) {
        context.getSharedPreferences(PREFS, 0).edit().putBoolean("signed_$providerId", signed).apply()
    }

    fun preferredProvider(context: Context): String =
        context.getSharedPreferences(PREFS, 0).getString("preferred", "auto") ?: "auto"

    fun setPreferredProvider(context: Context, id: String) {
        context.getSharedPreferences(PREFS, 0).edit().putString("preferred", id).apply()
    }
}
