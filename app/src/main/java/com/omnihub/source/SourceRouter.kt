package com.omnihub.source

import com.omnihub.providers.ChatMessage
import com.omnihub.providers.ChatResponse
import com.omnihub.soul.SoulManager

class SourceRouter(
    private val sourceManager: SourceManager,
    private val soul: SoulManager,
    private val issueReporter: AutoIssueReporter
) {
    data class RouteResult(
        val response: ChatResponse,
        val sourceId: String,
        val sourceName: String
    )

    suspend fun chat(
        messages: List<ChatMessage>,
        preferredSourceId: String? = null,
        queryForMemory: String? = null
    ): RouteResult {
        val memory = soul.retrieveForQuery(queryForMemory ?: messages.lastOrNull()?.content.orEmpty())
        val req = SourceChatRequest(
            messages = messages,
            memoryContext = memory.takeIf { it.isNotBlank() }
        )

        val candidates = buildList {
            preferredSourceId?.let { id ->
                sourceManager.get(id)?.takeIf { it.isConfigured() && sourceManager.isEnabled(id) }?.let { add(it) }
            }
            addAll(sourceManager.configured().filter { preferredSourceId == null || it.info.id != preferredSourceId })
        }

        if (candidates.isEmpty()) {
            throw IllegalStateException(
                "No configured sources. Open Sources \u2192 configure ChatGPT / Claude / Gemini / \u2026"
            )
        }

        var lastError: Exception? = null
        for (src in candidates) {
            try {
                val resp = src.chat(req)
                val lastUser = messages.lastOrNull { it.role == "user" }?.content
                if (!lastUser.isNullOrBlank()) {
                    soul.ingestExchange(lastUser, resp.content)
                }
                return RouteResult(resp, src.info.id, src.info.name)
            } catch (e: Exception) {
                lastError = e
                issueReporter.reportFailure(src.info.id, src.info.name, e.message ?: "unknown")
            }
        }
        throw lastError ?: IllegalStateException("All sources failed")
    }
}
