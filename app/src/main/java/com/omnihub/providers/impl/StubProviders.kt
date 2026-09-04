package com.omnihub.providers.impl

import com.omnihub.providers.ModelInfo

class CompatibleProvider(
    id: String,
    name: String,
    baseUrl: String,
    apiKey: String,
    models: List<ModelInfo>
) : BaseApiProvider(id, name, baseUrl, apiKey, models)

object ProviderFactory {
    fun deepseek(apiKey: String) = CompatibleProvider(
        "deepseek", "DeepSeek", "https://api.deepseek.com/v1", apiKey,
        listOf(ModelInfo("deepseek-chat", "DeepSeek Chat", 0.00014, 0.00028, 500, 0.92))
    )
    fun groq(apiKey: String) = CompatibleProvider(
        "groq", "Groq", "https://api.groq.com/openai/v1", apiKey,
        listOf(ModelInfo("llama-3.3-70b-versatile", "Llama 3.3 70B", 0.0, 0.0, 200, 0.9))
    )
    fun kimi(apiKey: String) = CompatibleProvider(
        "kimi", "Kimi", "https://api.moonshot.cn/v1", apiKey,
        listOf(ModelInfo("moonshot-v1-8k", "Moonshot v1 8k", 0.0, 0.0, 600, 0.88))
    )
    fun mistral(apiKey: String) = CompatibleProvider(
        "mistral", "Mistral", "https://api.mistral.ai/v1", apiKey,
        listOf(ModelInfo("mistral-large-latest", "Mistral Large", 0.002, 0.006, 500, 0.93, supportsTools = true))
    )
}
