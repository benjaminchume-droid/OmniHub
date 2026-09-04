package com.omnihub.providers.impl

import com.omnihub.providers.ModelInfo

class OpenAiProvider(apiKey: String) : BaseApiProvider(
    id = "openai",
    name = "OpenAI",
    baseUrl = "https://api.openai.com/v1",
    apiKey = apiKey,
    models = listOf(
        ModelInfo("gpt-4o", "GPT-4o", 0.005, 0.015, 600, 0.95, supportsVision = true, supportsTools = true),
        ModelInfo("gpt-4o-mini", "GPT-4o mini", 0.00015, 0.0006, 400, 0.95, supportsTools = true),
        ModelInfo("gpt-3.5-turbo", "GPT-3.5 Turbo", 0.0005, 0.0015, 350, 0.9)
    )
)
