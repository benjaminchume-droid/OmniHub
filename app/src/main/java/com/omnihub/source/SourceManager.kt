package com.omnihub.source

import android.content.Context
import com.omnihub.data.SecureStore
import com.omnihub.source.bundled.AnthropicSource
import com.omnihub.source.bundled.ApiKeySource
import com.omnihub.source.bundled.GeminiSource
import com.omnihub.source.bundled.WebSessionSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SourceManager(private val context: Context) {

    private val installedFile: File
        get() = File(context.filesDir, "installed_sources.json")

    private val enabledPrefs
        get() = context.getSharedPreferences("omni_sources", 0)

    private val _sources = MutableStateFlow<List<AiSource>>(emptyList())
    val sources: StateFlow<List<AiSource>> = _sources.asStateFlow()

    init { reload() }

    fun reload() {
        val list = mutableListOf<AiSource>()
        list.addAll(bundledSources())
        list.addAll(loadInstalledDescriptors())
        _sources.value = list
    }

    fun all(): List<AiSource> = _sources.value
    fun configured(): List<AiSource> = all().filter { it.isConfigured() && isEnabled(it.info.id) }
    fun isEnabled(id: String): Boolean = enabledPrefs.getBoolean("en_$id", true)
    fun setEnabled(id: String, enabled: Boolean) {
        enabledPrefs.edit().putBoolean("en_$id", enabled).apply()
        reload()
    }
    fun get(id: String): AiSource? = all().find { it.info.id == id }

    fun installDescriptor(descriptor: SourceDescriptor) {
        val arr = readInstalledArray()
        val next = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.getString("id") != descriptor.id) next.put(o)
        }
        next.put(descriptorToJson(descriptor))
        installedFile.writeText(JSONObject().put("sources", next).toString())
        reload()
    }

    fun uninstall(id: String) {
        val arr = readInstalledArray()
        val next = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.getString("id") != id) next.put(o)
        }
        installedFile.writeText(JSONObject().put("sources", next).toString())
        SecureStore.removeSecret(context, "api_key_$id")
        enabledPrefs.edit().remove("en_$id").apply()
        reload()
    }

    private fun loadInstalledDescriptors(): List<AiSource> {
        val arr = readInstalledArray()
        val out = mutableListOf<AiSource>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                val d = SourceCatalog.parseIndex(JSONObject().put("sources", JSONArray().put(o)).toString()).first()
                if (bundledSources().any { it.info.id == d.id }) continue
                out.add(DescriptorSource(context, d))
            } catch (_: Exception) {}
        }
        return out
    }

    private fun readInstalledArray(): JSONArray {
        if (!installedFile.exists()) return JSONArray()
        return try {
            JSONObject(installedFile.readText()).optJSONArray("sources") ?: JSONArray()
        } catch (_: Exception) { JSONArray() }
    }

    private fun descriptorToJson(d: SourceDescriptor): JSONObject {
        val models = JSONArray(); d.models.forEach { models.put(it) }
        val headers = JSONObject(); d.headers.forEach { (k, v) -> headers.put(k, v) }
        return JSONObject()
            .put("id", d.id).put("name", d.name).put("kind", d.kind)
            .put("authType", d.authType).put("version", d.version)
            .put("description", d.description).put("websiteUrl", d.websiteUrl)
            .put("baseUrl", d.baseUrl).put("chatPath", d.chatPath)
            .put("models", models).put("headers", headers).put("apkUrl", d.apkUrl)
    }

    private fun bundledSources(): List<AiSource> = listOf(
        ApiKeySource(context, "openai", "ChatGPT (OpenAI API)", "https://api.openai.com/v1",
            listOf("gpt-4o-mini", "gpt-4o", "o4-mini"), "https://platform.openai.com", "Official OpenAI Chat Completions"),
        WebSessionSource(context, "chatgpt_web", "ChatGPT (Web)", "https://chatgpt.com",
            "Sign in on chatgpt.com \u2014 cookies stored. API key preferred for chat."),
        AnthropicSource(context),
        WebSessionSource(context, "claude_web", "Claude (Web)", "https://claude.ai",
            "Sign in on claude.ai \u2014 session stored."),
        GeminiSource(context),
        WebSessionSource(context, "gemini_web", "Gemini (Web)", "https://gemini.google.com",
            "Sign in on gemini.google.com \u2014 session stored."),
        ApiKeySource(context, "groq", "Groq", "https://api.groq.com/openai/v1",
            listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant"), "https://console.groq.com", "Fast open models"),
        ApiKeySource(context, "deepseek", "DeepSeek", "https://api.deepseek.com",
            listOf("deepseek-chat", "deepseek-reasoner"), "https://platform.deepseek.com", "DeepSeek chat/reasoner"),
        ApiKeySource(context, "openrouter", "OpenRouter", "https://openrouter.ai/api/v1",
            listOf("openai/gpt-4o-mini", "anthropic/claude-3.5-sonnet"), "https://openrouter.ai", "Multi-provider router"),
        ApiKeySource(context, "kimi", "Kimi", "https://api.moonshot.cn/v1",
            listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k"), "https://platform.moonshot.cn", "Moonshot Kimi"),
        ApiKeySource(context, "mistral", "Mistral", "https://api.mistral.ai/v1",
            listOf("mistral-small-latest", "mistral-large-latest"), "https://console.mistral.ai", "Mistral API"),
        ApiKeySource(context, "perplexity", "Perplexity", "https://api.perplexity.ai",
            listOf("sonar", "sonar-pro"), "https://www.perplexity.ai", "Perplexity Sonar"),
        ApiKeySource(context, "nvidia", "NVIDIA NIM", "https://integrate.api.nvidia.com/v1",
            listOf("meta/llama-3.1-8b-instruct", "meta/llama-3.1-70b-instruct"), "https://build.nvidia.com", "NVIDIA integrate API"),
        ApiKeySource(context, "zai", "Z.AI", "https://api.z.ai/api/paas/v4",
            listOf("glm-4.5", "glm-4.5v"), "https://z.ai", "Z.AI GLM models"),
        WebSessionSource(context, "perplexity_web", "Perplexity (Web)", "https://www.perplexity.ai", "Sign in on perplexity.ai"),
        WebSessionSource(context, "kimi_web", "Kimi (Web)", "https://kimi.moonshot.cn", "Sign in on Kimi web")
    )
}
