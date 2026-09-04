package com.omnihub.source

import com.omnihub.providers.ChatMessage
import com.omnihub.providers.ChatResponse

enum class SourceKind { API, WEB_SESSION, HYBRID }

enum class AuthType { API_KEY, WEB_LOGIN, BOTH, NONE }

data class SourceInfo(
    val id: String,
    val name: String,
    val kind: SourceKind,
    val authType: AuthType,
    val version: Int = 1,
    val description: String = "",
    val websiteUrl: String = "",
    val iconUrl: String? = null,
    val bundled: Boolean = true,
    val enabled: Boolean = true
)

data class SourceConfig(
    val apiKey: String? = null,
    val sessionCookies: String? = null,
    val extra: Map<String, String> = emptyMap()
)

data class SourceChatRequest(
    val messages: List<ChatMessage>,
    val model: String? = null,
    val temperature: Double = 0.7,
    val maxTokens: Int? = null,
    val memoryContext: String? = null
)

interface AiSource {
    val info: SourceInfo
    fun isConfigured(): Boolean
    suspend fun configure(config: SourceConfig)
    suspend fun chat(request: SourceChatRequest): ChatResponse
    suspend fun testConnection(): Result<String> = runCatching {
        val r = chat(SourceChatRequest(messages = listOf(ChatMessage("user", "Reply with exactly: ok"))))
        if (r.content.isBlank()) error("Empty response")
        "ok"
    }
}

data class SourceDescriptor(
    val id: String,
    val name: String,
    val kind: String,
    val authType: String,
    val version: Int,
    val description: String = "",
    val websiteUrl: String = "",
    val baseUrl: String? = null,
    val chatPath: String? = null,
    val models: List<String> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val apkUrl: String? = null,
    val nsfw: Boolean = false
)
