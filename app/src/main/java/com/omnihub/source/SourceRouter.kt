package com.omnihub.source

import com.omnihub.OmniHubApp
import com.omnihub.providers.ChatMessage
import com.omnihub.providers.ChatResponse
import com.omnihub.soul.SoulManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

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

    data class TaskHints(
        val coding: Boolean = false,
        val research: Boolean = false,
        val vision: Boolean = false
    )

    suspend fun chat(
        messages: List<ChatMessage>,
        preferredSourceId: String? = null,
        conversationId: String? = null,
        taskHints: TaskHints = TaskHints()
    ): RouteResult = withContext(Dispatchers.IO) {
        val memory = soul.generatePromptContext(maxUnits = 8)
        val candidates = rank(preferredSourceId, taskHints)
        if (candidates.isEmpty()) {
            throw IllegalStateException(
                "No providers ready. Open Store and install a Web or MCP source."
            )
        }

        val analytics = try { OmniHubApp.instance.analytics } catch (_: Exception) { null }
        val requestId = UUID.randomUUID().toString()
        val started = System.currentTimeMillis()
        val userText = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        analytics?.recordUserMessage(conversationId, userText)

        var last: Exception? = null
        for (src in candidates) {
            analytics?.recordRequestStart(requestId, conversationId, src.info.id, null)
            try {
                val resp = src.chat(
                    SourceChatRequest(
                        messages = messages,
                        model = src.defaultModel,
                        memoryContext = memory.ifBlank { null },
                        conversationId = conversationId
                    )
                )
                val duration = System.currentTimeMillis() - started
                val tokens = resp.usageTokens
                val estimated = tokens <= 0
                val total = if (tokens > 0) tokens else estimateTokens(userText, resp.content)
                analytics?.recordRequestResult(
                    requestId = requestId,
                    conversationId = conversationId,
                    sourceId = src.info.id,
                    modelId = resp.model,
                    durationMs = duration,
                    success = true,
                    timedOut = false,
                    inputTokens = if (estimated) total / 3 else 0,
                    outputTokens = if (estimated) total - total / 3 else tokens,
                    totalTokens = total,
                    tokensEstimated = estimated
                )
                soul.learnFromExchange(src.info.id, messages, resp.content, conversationId)
                return@withContext RouteResult(resp, src.info.id, src.info.name)
            } catch (e: Exception) {
                last = e
                val duration = System.currentTimeMillis() - started
                val timedOut = e.message?.contains("timeout", ignoreCase = true) == true ||
                    e.message?.contains("timed out", ignoreCase = true) == true
                analytics?.recordRequestResult(
                    requestId = requestId,
                    conversationId = conversationId,
                    sourceId = src.info.id,
                    modelId = null,
                    durationMs = duration,
                    success = false,
                    timedOut = timedOut,
                    inputTokens = 0,
                    outputTokens = 0,
                    totalTokens = 0,
                    tokensEstimated = true
                )
                issueReporter.report(src.info.id, src.info.name, e.message ?: e.toString())
            }
        }
        throw last ?: IllegalStateException("All sources failed")
    }

    private fun rank(preferred: String?, hints: TaskHints): List<AiSource> {
        val all = sourceManager.all()
        val preferredSrc = preferred?.let { id -> all.find { it.info.id == id } }
        val rest = all.filter { it.info.id != preferred }
            .sortedByDescending { score(it, hints) }
        return listOfNotNull(preferredSrc) + rest
    }

    private fun score(src: AiSource, hints: TaskHints): Int {
        var s = 0
        if (src.isConfigured()) s += 10
        if (src.info.kind == SourceKind.WEB_SESSION || src.info.kind == SourceKind.WEB) s += 5
        if (src.info.kind == SourceKind.MCP) s += 3
        if (hints.coding && src.info.capabilities.coding) s += 4
        if (hints.research && src.info.capabilities.research) s += 4
        return s
    }

    private fun estimateTokens(user: String, reply: String): Int =
        ((user.length + reply.length) / 4).coerceAtLeast(1)
}
