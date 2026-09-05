package com.omnihub.source.builtin

import android.content.Context
import com.omnihub.providers.ChatRequest
import com.omnihub.providers.ChatResponse
import com.omnihub.providers.ModelInfo
import com.omnihub.source.AiSource
import com.omnihub.source.AuthType
import com.omnihub.source.SourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class GeminiSource(private val context: Context) : AiSource {
    override val id = "gemini"
    override val name = "Gemini"
    override val description = "Google's Gemini models via API"
    override val icon = -1
    override val type = SourceType.API
    override val authType = AuthType.API_KEY
    override val version = "1.0.0"
    override val defaultModel = "gemini-2.0-flash"
    override val isEnabled = true

    override val models = listOf(
        ModelInfo(
            id = "gemini-2.0-flash",
            name = "Gemini 2.0 Flash",
            supportsVision = true,
            supportsTools = true,
            costPer1kInput = 0.075,
            costPer1kOutput = 0.3,
            avgLatencyMs = 700,
            reliability = 0.97
        ),
        ModelInfo(
            id = "gemini-1.5-pro",
            name = "Gemini 1.5 Pro",
            supportsVision = true,
            supportsTools = true,
            costPer1kInput = 0.0035,
            costPer1kOutput = 0.014,
            avgLatencyMs = 1000,
            reliability = 0.97
        ),
        ModelInfo(
            id = "gemini-1.5-flash",
            name = "Gemini 1.5 Flash",
            supportsVision = true,
            supportsTools = true,
            costPer1kInput = 0.075,
            costPer1kOutput = 0.3,
            avgLatencyMs = 600,
            reliability = 0.97
        )
    )

    override suspend fun chat(request: ChatRequest): ChatResponse {
        // TODO: Implement Gemini API call
        return ChatResponse(
            message = "Gemini provider not yet implemented",
            sourceId = id,
            model = request.model ?: defaultModel,
            tokensUsed = 0,
            metadata = mapOf()
        )
    }

    override suspend fun stream(request: ChatRequest): Flow<String> = emptyFlow()

    override suspend fun validateCredentials(): Boolean {
        // TODO: Verify API key with Google
        return false
    }

    override fun estimateCost(inputTokens: Int, outputTokens: Int): Double {
        val model = models.find { it.id == defaultModel } ?: return 0.0
        return (inputTokens / 1000.0) * model.costPer1kInput +
               (outputTokens / 1000.0) * model.costPer1kOutput
    }

    override fun getAverageLatency(): Long = 700

    override fun getReliabilityScore(): Double = 0.97
}
