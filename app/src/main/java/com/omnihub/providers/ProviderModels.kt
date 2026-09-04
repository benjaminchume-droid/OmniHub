package com.omnihub.providers

data class ModelInfo(
    val id: String,
    val name: String,
    val costPer1kInput: Double = 0.0,
    val costPer1kOutput: Double = 0.0,
    val avgLatencyMs: Long = 800,
    val reliability: Double = 0.9,
    val supportsVision: Boolean = false,
    val supportsTools: Boolean = false
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val maxTokens: Int? = null
)

data class ChatResponse(
    val content: String,
    val model: String,
    val providerId: String,
    val usageTokens: Int = 0
)

interface AiProvider {
    val id: String
    val name: String
    val models: List<ModelInfo>
    suspend fun chat(request: ChatRequest): ChatResponse
}
