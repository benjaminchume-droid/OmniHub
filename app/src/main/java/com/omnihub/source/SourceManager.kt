package com.omnihub.source

import android.content.Context
import com.omnihub.providers.ModelInfo
import com.omnihub.source.bundled.ApiKeySource
import com.omnihub.source.core.ApiCore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SourceManager(private val context: Context) {
    private val _sources = MutableStateFlow<List<AiSource>>(emptyList())
    val sources: StateFlow<List<AiSource>> = _sources.asStateFlow()
    private val installedDescriptors = mutableListOf<AiSource>()

    init { reload() }

    fun reload() {
        _sources.value = buildBundled() + installedDescriptors.toList()
    }

    fun all(): List<AiSource> = _sources.value
    fun get(id: String): AiSource? = _sources.value.find { it.info.id == id }
    fun configured(): List<AiSource> = _sources.value.filter { it.isConfigured() }
    fun healthy(): List<AiSource> =
        _sources.value.filter { it.health() == SourceHealth.HEALTHY && it.isConfigured() }

    /** Phase-2 catalog install: register a DescriptorSource from remote index. */
    fun installDescriptor(descriptor: SourceDescriptor) {
        installedDescriptors.removeAll { it.info.id == descriptor.id }
        installedDescriptors.add(DescriptorSource(context, descriptor))
        reload()
    }

    fun uninstall(id: String) {
        installedDescriptors.removeAll { it.info.id == id }
        reload()
    }

    private fun buildBundled(): List<AiSource> = listOf(
        api("openai", "ChatGPT", "https://api.openai.com/v1",
            listOf(
                ModelInfo("gpt-4o-mini", "GPT-4o mini", 0.00015, 0.0006, 800, 0.95, supportsTools = true),
                ModelInfo("gpt-4o", "GPT-4o", 0.0025, 0.01, 900, 0.96, supportsVision = true, supportsTools = true)
            ), "https://platform.openai.com", "OpenAI GPT models", capabilities = SourceCapabilities(chat = true, stream = true, vision = true, tools = true, coding = true)),
        api("anthropic", "Claude", "https://api.anthropic.com",
            listOf(
                ModelInfo("claude-sonnet-4-20250514", "Claude Sonnet 4", 0.003, 0.015, 1000, 0.97, supportsTools = true),
                ModelInfo("claude-3-5-haiku-20241022", "Claude 3.5 Haiku", 0.0008, 0.004, 700, 0.95)
            ), "https://console.anthropic.com", "Anthropic Claude", auth = ApiCore.AuthStyle.ANTHROPIC_X_API_KEY,
            capabilities = SourceCapabilities(chat = true, coding = true, tools = true)),
        api("gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta/openai",
            listOf(
                ModelInfo("gemini-2.0-flash", "Gemini 2.0 Flash", 0.0001, 0.0004, 700, 0.94, supportsVision = true),
                ModelInfo("gemini-1.5-pro", "Gemini 1.5 Pro", 0.00125, 0.005, 900, 0.95, supportsVision = true)
            ), "https://aistudio.google.com", "Google Gemini",
            capabilities = SourceCapabilities(chat = true, vision = true, multimodal = true)),
        api("deepseek", "DeepSeek", "https://api.deepseek.com",
            listOf(
                ModelInfo("deepseek-chat", "DeepSeek Chat", 0.00014, 0.00028, 900, 0.93, supportsTools = true),
                ModelInfo("deepseek-reasoner", "DeepSeek Reasoner", 0.00055, 0.00219, 1200, 0.94)
            ), "https://platform.deepseek.com", "DeepSeek",
            capabilities = SourceCapabilities(chat = true, coding = true)),
        api("perplexity", "Perplexity", "https://api.perplexity.ai",
            listOf(ModelInfo("sonar", "Sonar", 0.001, 0.001, 1500, 0.92)),
            "https://www.perplexity.ai", "Research-grounded answers",
            capabilities = SourceCapabilities(chat = true, research = true)),
        api("groq", "Groq", "https://api.groq.com/openai/v1",
            listOf(
                ModelInfo("llama-3.3-70b-versatile", "Llama 3.3 70B", 0.00059, 0.00079, 400, 0.93),
                ModelInfo("llama-3.1-8b-instant", "Llama 3.1 8B", 0.00005, 0.00008, 250, 0.92)
            ), "https://console.groq.com", "High-throughput inference",
            capabilities = SourceCapabilities(chat = true)),
        api("kimi", "Kimi", "https://api.moonshot.cn/v1",
            listOf(ModelInfo("moonshot-v1-128k", "Moonshot 128K", 0.0, 0.0, 1100, 0.9)),
            "https://platform.moonshot.cn", "Long context coding/reasoning",
            capabilities = SourceCapabilities(chat = true, coding = true)),
        api("zai", "Z.ai", "https://api.z.ai/v1",
            listOf(ModelInfo("glm-4", "GLM-4", 0.0, 0.0, 1000, 0.9)),
            "https://z.ai", "Coding focused",
            capabilities = SourceCapabilities(chat = true, coding = true)),
        api("mistral", "Mistral", "https://api.mistral.ai/v1",
            listOf(
                ModelInfo("mistral-large-latest", "Mistral Large", 0.002, 0.006, 900, 0.94),
                ModelInfo("mistral-small-latest", "Mistral Small", 0.0002, 0.0006, 600, 0.93)
            ), "https://console.mistral.ai", "Open efficient LLMs",
            capabilities = SourceCapabilities(chat = true, coding = true)),
        api("openrouter", "OpenRouter", "https://openrouter.ai/api/v1",
            listOf(
                ModelInfo("openai/gpt-4o-mini", "OR GPT-4o mini", 0.00015, 0.0006, 900, 0.92),
                ModelInfo("anthropic/claude-3.5-sonnet", "OR Claude 3.5", 0.003, 0.015, 1000, 0.93)
            ), "https://openrouter.ai", "Multi-provider gateway",
            capabilities = SourceCapabilities(chat = true, coding = true, vision = true))
    )

    private fun api(
        id: String, name: String, base: String, models: List<ModelInfo>, website: String, desc: String,
        auth: ApiCore.AuthStyle = ApiCore.AuthStyle.BEARER,
        capabilities: SourceCapabilities = SourceCapabilities()
    ): AiSource = ApiKeySource(
        context = context, sourceId = id, sourceName = name, baseUrl = base,
        defaultModels = models, websiteUrl = website, description = desc,
        authStyle = auth, capabilities = capabilities, revision = "1.0.0"
    )
}
