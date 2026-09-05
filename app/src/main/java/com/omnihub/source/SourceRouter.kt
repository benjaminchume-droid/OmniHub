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
                "No configured Sources. Open Sources and add an API key for ChatGPT / Claude / Gemini / DeepSeek / …"
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
                val cat = when {
                    timedOut -> "timeout"
                    e.message?.contains("401") == true || e.message?.contains("auth", true) == true -> "authentication"
                    e.message?.contains("429") == true -> "rate_limit"
                    e.message?.contains("network", true) == true || e is java.io.IOException -> "network"
                    else -> "provider"
                }
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
                    tokensEstimated = false,
                    errorCategory = cat
                )
                issueReporter.report(src.info.id, e.message ?: "route failure")
            }
        }
        throw last ?: IllegalStateException("All Sources failed")
    }

    private fun estimateTokens(user: String, assistant: String): Int {
        val chars = user.length + assistant.length
        return (chars / 4).coerceAtLeast(1)
    }

    private fun rank(preferred: String?, hints: TaskHints): List<AiSource> {
        val configured = sourceManager.configured()
        if (configured.isEmpty()) return emptyList()
        return configured.map { src ->
            var score = 1.0
            when (src.health()) {
                SourceHealth.HEALTHY -> score += 2.0
                SourceHealth.DEGRADED -> score += 0.5
                SourceHealth.AUTH_REQUIRED -> score -= 10.0
                else -> score -= 5.0
            }
            if (preferred != null && src.info.id == preferred) score += 5.0
            val caps = src.info.capabilities
            if (hints.needsResearch && caps.research) score += 3.0
            if (hints.needsCoding && caps.coding) score += 2.5
            if (hints.needsVision && caps.vision) score += 2.0
            score += when (src.info.id) {
                "anthropic" -> if (hints.needsCoding) 1.5 else 0.8
                "gemini" -> if (hints.needsVision) 1.5 else 0.5
                "perplexity" -> if (hints.needsResearch) 2.0 else 0.2
                "deepseek", "zai", "kimi" -> if (hints.needsCoding) 1.2 else 0.4
                "groq" -> if (hints.preferFast) 1.5 else 0.3
                "openai" -> 1.0
                else -> 0.5
            }
            src to score
        }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
    }

    data class TaskHints(
        val needsResearch: Boolean = false,
        val needsCoding: Boolean = false,
        val needsVision: Boolean = false,
        val preferFast: Boolean = false
    ) {
        companion object {
            fun fromPrompt(prompt: String): TaskHints {
                val p = prompt.lowercase()
                return TaskHints(
                    needsResearch = listOf("research", "search", "news", "cite", "source").any { it in p },
                    needsCoding = listOf("code", "kotlin", "android", "bug", "compile", "function", "class").any { it in p },
                    needsVision = listOf("image", "screenshot", "photo", "picture").any { it in p },
                    preferFast = listOf("quick", "fast", "brief").any { it in p }
                )
            }
        }
    }
}
