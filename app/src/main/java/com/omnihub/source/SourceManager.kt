package com.omnihub.source

import android.content.Context
import com.omnihub.source.bundled.WebProviderSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SourceManager(private val context: Context) {
    private val _sources = MutableStateFlow<List<AiSource>>(emptyList())
    val sources: StateFlow<List<AiSource>> = _sources.asStateFlow()
    private val installedDescriptors = mutableListOf<AiSource>()
    private val installedExtensions = mutableListOf<AiSource>()

    init { reload() }

    fun reload() {
        _sources.value = buildBundled() + installedDescriptors + installedExtensions
    }

    fun all(): List<AiSource> = _sources.value
    fun get(id: String): AiSource? = _sources.value.find { it.info.id == id }
    fun configured(): List<AiSource> = _sources.value.filter { it.isConfigured() }
    fun healthy(): List<AiSource> =
        _sources.value.filter { it.health() == SourceHealth.HEALTHY && it.isConfigured() }

    fun installDescriptor(descriptor: SourceDescriptor) {
        installedDescriptors.removeAll { it.info.id == descriptor.id }
        installedDescriptors.add(DescriptorSource(context, descriptor))
        reload()
    }

    fun registerExtension(source: AiSource) {
        installedExtensions.removeAll { it.info.id == source.info.id }
        installedExtensions.add(source)
        reload()
    }

    fun uninstall(id: String) {
        installedDescriptors.removeAll { it.info.id == id }
        installedExtensions.removeAll { it.info.id == id }
        reload()
    }

    private fun buildBundled(): List<AiSource> = listOf(
        web("chatgpt", "ChatGPT", "https://chatgpt.com"),
        web("claude", "Claude", "https://claude.ai"),
        web("gemini", "Gemini", "https://gemini.google.com"),
        web("perplexity", "Perplexity", "https://www.perplexity.ai"),
        web("deepseek", "DeepSeek", "https://chat.deepseek.com"),
        web("grok", "Grok", "https://x.com/i/grok"),
        web("groq", "Groq", "https://chat.groq.com"),
        web("hy3", "Hy3", "https://hy3.ai"),
        web("kimi", "Kimi", "https://kimi.moonshot.cn"),
        web("zai", "Z.AI", "https://chat.z.ai")
    )

    private fun web(id: String, name: String, url: String): AiSource =
        WebProviderSource(context, id, name, url, "Built-in Web Core")
}
