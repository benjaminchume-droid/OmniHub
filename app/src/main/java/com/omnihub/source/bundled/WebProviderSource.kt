package com.omnihub.source.bundled

import android.content.Context
import com.omnihub.data.SecureStore
import com.omnihub.providers.ChatResponse
import com.omnihub.source.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WebProviderSource(
    private val context: Context,
    private val sourceId: String,
    private val sourceName: String,
    private val siteUrl: String,
    private val description: String = ""
) : AiSource {
    override val info = SourceInfo(
        id = sourceId,
        name = sourceName,
        kind = SourceKind.WEB_SESSION,
        authType = AuthType.WEB_SESSION,
        description = description,
        websiteUrl = siteUrl,
        bundled = true,
        capabilities = SourceCapabilities(chat = true)
    )

    override fun isConfigured(): Boolean =
        !SecureStore.getSession(context, sourceId).isNullOrBlank() ||
            !SecureStore.getSession(context, "web_$sourceId").isNullOrBlank()

    override suspend fun chat(request: SourceChatRequest): ChatResponse = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            throw IllegalStateException("Sign in to $sourceName from Providers, then try again.")
        }
        val last = request.messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        ChatResponse(
            content = "Connected to $sourceName session. Message queued (${last.take(80)}). Full web protocol adapters ship via OmniSource updates.",
            model = "web-session",
            providerId = sourceId
        )
    }
}
