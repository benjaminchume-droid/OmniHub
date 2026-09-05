package com.omnihub.source

import android.util.Log
import com.omnihub.providers.ChatRequest
import com.omnihub.providers.ChatResponse
import com.omnihub.soul.SoulManager
import kotlinx.coroutines.delay

/**
 * Intelligent routing with fallback.
 * If primary source fails, automatically tries next best candidate.
 * Integrates soul system for cross-provider memory persistence.
 */
class SourceRouter(
    private val sourceManager: SourceManager,
    private val soulManager: SoulManager
) {

    data class RoutingDecision(
        val source: AiSource,
        val model: String,
        val score: Double,
        val estimatedCost: Double,
        val estimatedLatencyMs: Long,
        val reason: String
    )

    data class SourceRequirements(
        val needsVision: Boolean = false,
        val needsTools: Boolean = false,
        val maxCostPer1k: Double? = null,
        val maxLatencyMs: Long? = null,
        val preferredSources: List<String> = emptyList(),
        val excludeSources: List<String> = emptyList()
    )

    /**
     * Rank all available sources by score (cost, latency, reliability, capabilities)
     */
    suspend fun rankSources(
        promptTokenEstimate: Int,
        requirements: SourceRequirements = SourceRequirements()
    ): List<RoutingDecision> {
        val enabledSources = sourceManager.getEnabledSources()
        Log.d(TAG, "Ranking ${enabledSources.size} sources for $promptTokenEstimate tokens")

        return enabledSources
            .filter { it.id !in requirements.excludeSources }
            .flatMap { source ->
                source.models.map { model ->
                    scoreSource(source, model.id, promptTokenEstimate, requirements)
                }
            }
            .filter { it.score > 0.0 }
            .sortedByDescending { it.score }
    }

    /**
     * Send request with automatic fallback to next best source on failure
     */
    suspend fun chatWithFallback(
        request: ChatRequest,
        requirements: SourceRequirements = SourceRequirements(),
        maxAttempts: Int = 4
    ): ChatResponse {
        val candidates = rankSources(
            promptTokenEstimate = request.messages.sumOf { it.content.length / 4 },
            requirements = requirements
        ).take(maxAttempts)

        if (candidates.isEmpty()) {
            throw IllegalStateException("No available sources. Add API key or web session in Settings.")
        }

        Log.d(TAG, "Attempting routing with ${candidates.size} candidates")

        var lastError: Exception? = null
        for ((index, decision) in candidates.withIndex()) {
            try {
                Log.d(TAG, "[${index + 1}/${candidates.size}] Trying ${decision.source.id} (score: ${decision.score})")
                
                // Load soul context
                val soulContext = soulManager.generatePromptContext(5)  // Last 5 memory units
                val enhancedRequest = request.copy(
                    model = decision.model,
                    systemPrompt = (request.systemPrompt ?: "") + "\n\n" + soulContext
                )
                
                val response = decision.source.chat(enhancedRequest)
                
                // Update soul with new conversation
                soulManager.learnFromConversation(
                    sourceId = decision.source.id,
                    request = request,
                    response = response
                )
                
                Log.d(TAG, "Successfully routed to ${decision.source.id}")
                return response
            } catch (e: Exception) {
                Log.w(TAG, "${decision.source.id} failed: ${e.message}")
                lastError = e
                if (index < candidates.size - 1) {
                    delay(500)  // Brief delay before next attempt
                }
            }
        }

        throw lastError ?: IllegalStateException("All sources failed")
    }

    private fun scoreSource(
        source: AiSource,
        modelId: String,
        promptTokens: Int,
        req: SourceRequirements
    ): RoutingDecision {
        val model = source.models.find { it.id == modelId }
            ?: return RoutingDecision(
                source = source,
                model = modelId,
                score = 0.0,
                estimatedCost = 999.0,
                estimatedLatencyMs = 99999,
                reason = "Model not found"
            )

        // Filter by requirements
        if (req.needsVision && !model.supportsVision) {
            return RoutingDecision(
                source, modelId, 0.0, 999.0, 99999,
                "Model doesn't support vision"
            )
        }
        if (req.needsTools && !model.supportsTools) {
            return RoutingDecision(
                source, modelId, 0.0, 999.0, 99999,
                "Model doesn't support tools"
            )
        }

        val estimatedCost = source.estimateCost(promptTokens, 500)  // Assume 500 output tokens
        val costPer1k = estimatedCost / (promptTokens / 1000.0)

        if (req.maxCostPer1k != null && costPer1k > req.maxCostPer1k) {
            return RoutingDecision(
                source, modelId, 0.0, estimatedCost,
                source.getAverageLatency(),
                "Cost exceeds limit: $$costPer1k > $${req.maxCostPer1k}"
            )
        }
        if (req.maxLatencyMs != null && source.getAverageLatency() > req.maxLatencyMs) {
            return RoutingDecision(
                source, modelId, 0.0, estimatedCost,
                source.getAverageLatency(),
                "Latency exceeds limit: ${source.getAverageLatency()}ms > ${req.maxLatencyMs}ms"
            )
        }

        // Calculate composite score
        val costScore = 1.0 / kotlin.math.max(0.0001, costPer1k)
        val latencyScore = 1000.0 / kotlin.math.max(50.0, source.getAverageLatency().toDouble())
        val reliabilityScore = source.getReliabilityScore()
        val capabilityScore = when {
            model.supportsTools && model.supportsVision -> 1.0
            model.supportsTools || model.supportsVision -> 0.8
            else -> 0.6
        }
        val preferenceBoost = if (source.id in req.preferredSources) 1.3 else 1.0

        val total = (costScore * 0.30 + latencyScore * 0.25 + reliabilityScore * 0.25 + capabilityScore * 0.20) * preferenceBoost

        return RoutingDecision(
            source = source,
            model = modelId,
            score = total,
            estimatedCost = estimatedCost,
            estimatedLatencyMs = source.getAverageLatency(),
            reason = "Cost: ${String.format("%.2f", costScore)} | Latency: ${String.format("%.2f", latencyScore)} | Reliability: ${String.format("%.2f", reliabilityScore)}"
        )
    }

    companion object {
        private const val TAG = "SourceRouter"
    }
}
