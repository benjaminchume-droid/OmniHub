package com.omnihub.providers.websession

import android.content.Context
import android.webkit.CookieManager

/**
 * Universal Web Session manager.
 * Search any AI site, open WebView, user signs in, we harvest cookies.
 */
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
        context.getSharedPreferences("omnihub_sessions", Context.MODE_PRIVATE)
            .edit().putString("session_$providerId", cookieHeader).apply()
    }

    fun getSession(providerId: String): String? {
        return context.getSharedPreferences("omnihub_sessions", Context.MODE_PRIVATE)
            .getString("session_$providerId", null)
    }

    fun clearSession(providerId: String) {
        context.getSharedPreferences("omnihub_sessions", Context.MODE_PRIVATE)
            .edit().remove("session_$providerId").apply()
    }
}
