package com.omnihub.providers.impl

import android.content.Context
import com.omnihub.data.SecureStore
import com.omnihub.providers.AiProvider
import com.omnihub.providers.ProviderRegistry

object ProviderBootstrap {
    private val ids = listOf(
        "openai", "anthropic", "gemini", "groq", "deepseek",
        "openrouter", "kimi", "mistral", "perplexity", "nvidia", "zai"
    )

    fun detect(raw: String): Pair<String, (String) -> AiProvider> {
        val k = raw.trim()
        return when {
            k.startsWith("sk-ant") -> "anthropic" to { AnthropicProvider(it) }
            k.startsWith("sk-or-") -> "openrouter" to { ProviderFactory.openrouter(it) }
            k.startsWith("gsk_") -> "groq" to { ProviderFactory.groq(it) }
            k.startsWith("AIza") -> "gemini" to { GeminiProvider(it) }
            k.startsWith("nvapi-") -> "nvidia" to { ProviderFactory.nvidia(it) }
            k.startsWith("pplx-") -> "perplexity" to { ProviderFactory.perplexity(it) }
            else -> "openai" to { OpenAiProvider(it) }
        }
    }

    fun detectName(raw: String): String = when (detect(raw).first) {
        "anthropic" -> "Anthropic"
        "openrouter" -> "OpenRouter"
        "groq" -> "Groq"
        "gemini" -> "Google Gemini"
        "nvidia" -> "NVIDIA NIM"
        "perplexity" -> "Perplexity"
        else -> "OpenAI-compatible"
    }

    fun saveAndRegister(context: Context, registry: ProviderRegistry, rawKey: String): String {
        val key = rawKey.trim()
        require(key.isNotBlank()) { "API key is empty" }
        val (id, factory) = detect(key)
        SecureStore.setApiKey(context, id, key)
        registry.register(factory(key))
        return id
    }

    fun reload(context: Context, registry: ProviderRegistry) {
        registry.clear()
        ids.forEach { id ->
            val key = SecureStore.getApiKey(context, id)?.trim().orEmpty()
            if (key.isBlank()) return@forEach
            val provider = when (id) {
                "anthropic" -> AnthropicProvider(key)
                "gemini" -> GeminiProvider(key)
                "groq" -> ProviderFactory.groq(key)
                "deepseek" -> ProviderFactory.deepseek(key)
                "openrouter" -> ProviderFactory.openrouter(key)
                "kimi" -> ProviderFactory.kimi(key)
                "mistral" -> ProviderFactory.mistral(key)
                "perplexity" -> ProviderFactory.perplexity(key)
                "nvidia" -> ProviderFactory.nvidia(key)
                "zai" -> ProviderFactory.zai(key)
                else -> OpenAiProvider(key)
            }
            registry.register(provider)
        }
    }
}
