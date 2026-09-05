package com.omnihub.source.bundled

import android.content.Context
import com.omnihub.data.SecureStore
import com.omnihub.providers.ChatMessage
import com.omnihub.providers.ChatResponse
import com.omnihub.providers.ModelInfo
import com.omnihub.source.*
import com.omnihub.source.core.ApiCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ApiKeySource(
    private val context: Context,
    private val sourceId: String,
    private val sourceName: String,
    private val baseUrl: String,
    private val defaultModels: List<ModelInfo>,
    private val websiteUrl: String = "",
    private val description: String = "",
    private val authStyle: ApiCore.AuthStyle = ApiCore.AuthStyle.BEARER,
    private val chatPath: String = "/chat/completions",
    private val capabilities: SourceCapabilities = SourceCapabilities(),
    private val kind: SourceKind = SourceKind.API,
    private val revision: String = "1.0.0"
) : AiSource {

    override val info = SourceInfo(
        id = sourceId,
        name = sourceName,
        kind = kind,
        authType = AuthType.API_KEY,
        description = description,
        websiteUrl = websiteUrl,
        revision = revision,
        bundled = true,
        capabilities = capabilities
    )

    override val models: List<ModelInfo> = defaultModels
    override val defaultModel: String = defaultModels.firstOrNull()?.id ?: ""

    override fun isConfigured(): Boolean =
        !SecureStore.getApiKey(context, sourceId).isNullOrBlank()

    override suspend fun configure(config: SourceConfig) {
        config.apiKey?.let { SecureStore.setApiKey(context, sourceId, it) }
    }

    override suspend fun chat(request: SourceChatRequest): ChatResponse = withContext(Dispatchers.IO) {
        val key = SecureStore.getApiKey(context, sourceId)
            ?: throw IllegalStateException("$sourceName requires an API key \u2014 open Sources and configure.")
        val model = request.model?.ifBlank { null } ?: defaultModel
        val msgs = mutableListOf<ChatMessage>()
        request.memoryContext?.takeIf { it.isNotBlank() }?.let { msgs.add(ChatMessage("system", it)) }
        msgs.addAll(request.messages)
        ApiCore.chatCompletions(
            ApiCore.ApiCall(
                baseUrl = baseUrl,
                path = if (authStyle == ApiCore.AuthStyle.ANTHROPIC_X_API_KEY) "/v1/messages" else chatPath,
                apiKey = key,
                model = model,
                messages = msgs,
                temperature = request.temperature,
                maxTokens = request.maxTokens,
                authStyle = authStyle,
                providerId = sourceId
            )
        )
    }
}
