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

class GroqSource(private val context: Context) : AiSource {
    override val id = "groq"
    override val name = "Groq"
    override val description = "Lightning-fast LPU inference"
    override val icon = -1
    override val type = SourceType.API
    override val authType = AuthType.API_KEY
    override val version = "1.0.0"
    override val defaultModel = "llama-3.3-70b-versatile"
    override val isEnabled = true

    override val models = listOf(
        ModelInfo("llama-3.3-70b-versatile", "Llama 3.3 70B", true, true, 0.00059, 0.00079, 400, 0.95),
        ModelInfo("llama-3.1-70b-versatile", "Llama 3.1 70B", true, true, 0.00059, 0.00079, 450, 0.95),
        ModelInfo("mixtral-8x7b-32768", "Mixtral 8x7B", false, false, 0.00024, 0.00024, 350, 0.95)
    )

    override suspend fun chat(request: ChatRequest): ChatResponse =
        ChatResponse("Groq not implemented", id, defaultModel, 0, mapOf())

    override suspend fun stream(request: ChatRequest): Flow<String> = emptyFlow()
    override suspend fun validateCredentials(): Boolean = false
    override fun estimateCost(inputTokens: Int, outputTokens: Int): Double = 0.0
    override fun getAverageLatency(): Long = 400
    override fun getReliabilityScore(): Double = 0.95
}

class DeepSeekSource(private val context: Context) : AiSource {
    override val id = "deepseek"
    override val name = "DeepSeek"
    override val description = "Cost-effective reasoning models"
    override val icon = -1
    override val type = SourceType.API
    override val authType = AuthType.API_KEY
    override val version = "1.0.0"
    override val defaultModel = "deepseek-chat"
    override val isEnabled = true

    override val models = listOf(
        ModelInfo("deepseek-chat", "DeepSeek Chat", false, true, 0.0014, 0.0042, 1200, 0.96)
    )

    override suspend fun chat(request: ChatRequest): ChatResponse =
        ChatResponse("DeepSeek not implemented", id, defaultModel, 0, mapOf())

    override suspend fun stream(request: ChatRequest): Flow<String> = emptyFlow()
    override suspend fun validateCredentials(): Boolean = false
    override fun estimateCost(inputTokens: Int, outputTokens: Int): Double = 0.0
    override fun getAverageLatency(): Long = 1200
    override fun getReliabilityScore(): Double = 0.96
}

class MistralSource(private val context: Context) : AiSource {
    override val id = "mistral"
    override val name = "Mistral"
    override val description = "Open and optimal LLMs"
    override val icon = -1
    override val type = SourceType.API
    override val authType = AuthType.API_KEY
    override val version = "1.0.0"
    override val defaultModel = "mistral-large-latest"
    override val isEnabled = true

    override val models = listOf(
        ModelInfo("mistral-large-latest", "Mistral Large", false, true, 0.002, 0.006, 1000, 0.97),
        ModelInfo("mistral-medium-latest", "Mistral Medium", false, true, 0.00027, 0.00081, 800, 0.96)
    )

    override suspend fun chat(request: ChatRequest): ChatResponse =
        ChatResponse("Mistral not implemented", id, defaultModel, 0, mapOf())

    override suspend fun stream(request: ChatRequest): Flow<String> = emptyFlow()
    override suspend fun validateCredentials(): Boolean = false
    override fun estimateCost(inputTokens: Int, outputTokens: Int): Double = 0.0
    override fun getAverageLatency(): Long = 1000
    override fun getReliabilityScore(): Double = 0.97
}

class PerplexitySource(private val context: Context) : AiSource {
    override val id = "perplexity"
    override val name = "Perplexity"
    override val description = "AI-powered search and reasoning"
    override val icon = -1
    override val type = SourceType.API
    override val authType = AuthType.API_KEY
    override val version = "1.0.0"
    override val defaultModel = "sonar"
    override val isEnabled = true

    override val models = listOf(
        ModelInfo("sonar", "Sonar", true, true, 0.002, 0.002, 2000, 0.93)
    )

    override suspend fun chat(request: ChatRequest): ChatResponse =
        ChatResponse("Perplexity not implemented", id, defaultModel, 0, mapOf())

    override suspend fun stream(request: ChatRequest): Flow<String> = emptyFlow()
    override suspend fun validateCredentials(): Boolean = false
    override fun estimateCost(inputTokens: Int, outputTokens: Int): Double = 0.0
    override fun getAverageLatency(): Long = 2000
    override fun getReliabilityScore(): Double = 0.93
}
