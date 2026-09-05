package com.omnihub.source

import com.omnihub.providers.ChatMessage
import com.omnihub.providers.ChatResponse
import com.omnihub.soul.SoulManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                "No configured Sources. Open Sources and add an API key for ChatGPT / Claude / Gemini / DeepSeek / \u2026"
            )
        }
        var last: Exception? = null
        for (src in candidates) {
            try {
                val resp = src.chat(
                    SourceChatRequest(
                        messages = messages,
                        model = src.defaultModel,
                        memoryContext = memory.ifBlank { null },
                        conversationId = conversationId
                    )
                )
                soul.learnFromExchange(src.info.id, messages, resp.content, conversationId)
                return@withContext RouteResult(resp, src.info.id, src.info.name)
            } catch (e: Exception) {
                last = e
                issueReporter.report(src.info.id, e.message ?: "route failure")
            }
        }
        throw last ?: IllegalStateException("All Sources failed")
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
