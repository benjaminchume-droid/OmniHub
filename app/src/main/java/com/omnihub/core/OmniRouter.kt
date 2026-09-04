package com.omnihub.core

import com.omnihub.providers.AiProvider
import com.omnihub.providers.ChatRequest
import com.omnihub.providers.ChatResponse
import com.omnihub.providers.ProviderRegistry
import com.omnihub.providers.ModelInfo
import kotlin.math.max

/**
 * OmniRouter with automatic fallback.
 * If primary provider fails, transparently tries the next best candidate.
 */
class OmniRouter(private val registry: ProviderRegistry) {

    data class RoutingDecision(
        val provider: AiProvider,
        val model: ModelInfo,
        val score: Double,
        val estimatedCost: Double,
        val estimatedLatencyMs: Long
    )

    data class Requirements(
        val needsVision: Boolean = false,
        val needsTools: Boolean = false,
        val maxCostPer1k: Double? = null,
        val maxLatencyMs: Long? = null,
        val preferredProviders: List<String> = emptyList()
    )

    fun rank(promptTokenEstimate: Int, requirements: Requirements = Requirements()): List<RoutingDecision> {
        return registry.allProviders()
            .flatMap { provider ->
                provider.models.map { model ->
                    score(provider, model, promptTokenEstimate, requirements)
                }
            }
            .filter { it.score > 0.0 }
            .sortedByDescending { it.score }
    }

    suspend fun chatWithFallback(
        request: ChatRequest,
        requirements: Requirements = Requirements(),
        maxAttempts: Int = 4
    ): ChatResponse {
        val candidates = rank(
            promptTokenEstimate = request.messages.sumOf { it.content.length / 4 },
            requirements = requirements
        ).take(maxAttempts)

        if (candidates.isEmpty()) {
            throw IllegalStateException("No available providers. Add an API key or Web Session in Settings.")
        }

        var lastError: Exception? = null
        for (decision in candidates) {
            try {
                val req = request.copy(model = decision.model.id)
                return decision.provider.chat(req)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("All providers failed")
    }

    private fun score(
        provider: AiProvider,
        model: ModelInfo,
        promptTokens: Int,
        req: Requirements
    ): RoutingDecision {
        if (req.needsVision && !model.supportsVision) return RoutingDecision(provider, model, 0.0, 999.0, 99999)
        if (req.needsTools && !model.supportsTools) return RoutingDecision(provider, model, 0.0, 999.0, 99999)

        val costPer1k = model.costPer1kInput + model.costPer1kOutput * 0.6
        val estimatedCost = (promptTokens / 1000.0) * costPer1k

        if (req.maxCostPer1k != null && costPer1k > req.maxCostPer1k) return RoutingDecision(provider, model, 0.0, estimatedCost, model.avgLatencyMs)
        if (req.maxLatencyMs != null && model.avgLatencyMs > req.maxLatencyMs) return RoutingDecision(provider, model, 0.0, estimatedCost, model.avgLatencyMs)

        val costScore = 1.0 / max(0.0001, costPer1k)
        val latencyScore = 1000.0 / max(50.0, model.avgLatencyMs.toDouble())
        val reliabilityScore = model.reliability
        val capabilityScore = when {
            model.supportsTools && model.supportsVision -> 1.0
            model.supportsTools || model.supportsVision -> 0.8
            else -> 0.6
        }
        val preferenceBoost = if (provider.id in req.preferredProviders) 1.3 else 1.0

        val total = (costScore * 0.30 + latencyScore * 0.25 + reliabilityScore * 0.25 + capabilityScore * 0.20) * preferenceBoost

        return RoutingDecision(provider, model, total, estimatedCost, model.avgLatencyMs)
    }
}
