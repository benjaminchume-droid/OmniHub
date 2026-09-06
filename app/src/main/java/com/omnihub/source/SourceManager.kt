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

    init { reload() }

    fun reload() {
        _sources.value = buildBundled() + installedDescriptors.toList()
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

    fun uninstall(id: String) {
        installedDescriptors.removeAll { it.info.id == id }
        reload()
    }

    private fun buildBundled(): List<AiSource> = listOf(
        web("chatgpt_web", "ChatGPT", "https://chatgpt.com", "ChatGPT web session"),
        web("claude_web", "Claude", "https://claude.ai", "Claude web session"),
        web("gemini_web", "Gemini", "https://gemini.google.com", "Gemini web session"),
        web("perplexity_web", "Perplexity", "https://www.perplexity.ai", "Perplexity web session"),
        web("deepseek_web", "DeepSeek", "https://chat.deepseek.com", "DeepSeek web session"),
        web("grok_web", "Grok", "https://x.com/i/grok", "Grok web session"),
        web("kimi_web", "Kimi", "https://kimi.moonshot.cn", "Kimi web session")
    )

    private fun web(id: String, name: String, url: String, desc: String): AiSource =
        WebProviderSource(context, id, name, url, desc)
}
