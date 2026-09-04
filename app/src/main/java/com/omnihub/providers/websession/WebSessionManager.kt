package com.omnihub.providers.websession

import android.content.Context
import android.webkit.CookieManager
import com.omnihub.data.SecureStore

class WebSessionManager(private val context: Context) {

    data class Site(
        val name: String,
        val url: String,
        val keywords: List<String>,
        val cookieHints: List<String> = emptyList()
    )

    val catalog = listOf(
        Site("Google AI Studio", "https://aistudio.google.com", listOf("google", "gemini", "ai studio", "aistudio"), listOf("__Secure-1PSID", "SID")),
        Site("Gemini", "https://gemini.google.com", listOf("gemini", "google"), listOf("__Secure-1PSID")),
        Site("ChatGPT", "https://chatgpt.com", listOf("openai", "chatgpt", "gpt"), listOf("__Secure-next-auth.session-token")),
        Site("Claude", "https://claude.ai", listOf("claude", "anthropic"), listOf("sessionKey")),
        Site("Kimi", "https://kimi.moonshot.cn", listOf("kimi", "moonshot")),
        Site("Z.AI / ChatGLM", "https://chatglm.cn", listOf("z.ai", "zai", "zhipu", "glm", "chatglm")),
        Site("Perplexity", "https://www.perplexity.ai", listOf("perplexity")),
        Site("Grok", "https://grok.x.ai", listOf("grok", "xai", "x.ai")),
        Site("DeepSeek", "https://chat.deepseek.com", listOf("deepseek")),
        Site("Mistral", "https://chat.mistral.ai", listOf("mistral"))
    )

    fun search(query: String): List<Site> {
        if (query.isBlank()) return catalog
        val q = query.lowercase()
        return catalog.filter { site ->
            site.name.lowercase().contains(q) ||
            site.keywords.any { it.contains(q) || q.contains(it) } ||
            site.url.contains(q)
        }
    }

    fun harvestCookies(url: String): String {
        val cm = CookieManager.getInstance()
        return cm.getCookie(url) ?: ""
    }

    fun saveSession(providerId: String, cookieHeader: String) {
        SecureStore.setSession(context, providerId, cookieHeader)
    }

    fun getSession(providerId: String): String? =
        SecureStore.getSession(context, providerId)

    fun clearSession(providerId: String) =
        SecureStore.clearSession(context, providerId)
}
