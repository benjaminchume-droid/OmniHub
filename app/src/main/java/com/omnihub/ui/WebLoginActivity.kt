package com.omnihub.ui

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.omnihub.data.SecureStore
import com.omnihub.source.core.ProviderAuthStore

/**
 * Sign-in WebView. Harvests cookies from the provider host + related auth hosts.
 */
class WebLoginActivity : ComponentActivity() {
    private lateinit var web: WebView
    private var providerId: String = "session"
    private var providerName: String = "Provider"
    private var siteUrl: String = ""
    private var lastCookies: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        siteUrl = intent.getStringExtra(EXTRA_URL) ?: intent.getStringExtra("url") ?: run { finish(); return }
        providerName = intent.getStringExtra(EXTRA_TITLE)
            ?: intent.getStringExtra("provider_name")
            ?: "Provider"
        providerId = intent.getStringExtra("provider_id")
            ?: providerName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, loaded: String?) {
                    harvestAll(loaded ?: siteUrl)
                }
            }
        }
        cm.setAcceptThirdPartyCookies(web, true)
        setContentView(web)
        web.loadUrl(siteUrl)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                harvestAll(web.url ?: siteUrl)
                try { cm.flush() } catch (_: Exception) {}
                val ok = lastCookies.isNotBlank()
                ProviderAuthStore.setSignedIn(this@WebLoginActivity, providerId, ok)
                Toast.makeText(
                    this@WebLoginActivity,
                    if (ok) "$providerName ready" else "Sign in did not finish — stay until logged in, then go back",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        })
    }

    private fun harvestAll(currentUrl: String) {
        val cm = CookieManager.getInstance()
        val hosts = linkedSetOf<String>()
        hosts.add(currentUrl)
        hosts.add(siteUrl)
        // related auth domains
        when {
            providerId.contains("chatgpt", true) || siteUrl.contains("chatgpt", true) -> {
                hosts += listOf(
                    "https://chatgpt.com",
                    "https://chat.openai.com",
                    "https://auth.openai.com",
                    "https://openai.com",
                    "https://www.openai.com"
                )
            }
            providerId.contains("claude", true) -> hosts += listOf("https://claude.ai", "https://www.claude.ai")
            providerId.contains("gemini", true) -> hosts += listOf("https://gemini.google.com", "https://accounts.google.com")
            providerId.contains("grok", true) -> hosts += listOf("https://x.com", "https://twitter.com")
            providerId.contains("groq", true) -> hosts += listOf("https://chat.groq.com", "https://groq.com")
            providerId.contains("perplexity", true) -> hosts += listOf("https://www.perplexity.ai", "https://perplexity.ai")
            providerId.contains("deepseek", true) -> hosts += listOf("https://chat.deepseek.com")
            providerId.contains("kimi", true) -> hosts += listOf("https://kimi.moonshot.cn")
            providerId.contains("zai", true) || providerId.contains("z.ai", true) -> hosts += listOf("https://chat.z.ai")
        }

        val parts = linkedSetOf<String>()
        for (h in hosts) {
            try {
                val c = cm.getCookie(h).orEmpty()
                if (c.isNotBlank()) {
                    c.split(";").map { it.trim() }.filter { it.contains("=") }.forEach { parts.add(it) }
                }
            } catch (_: Exception) {}
        }
        if (parts.isNotEmpty()) {
            lastCookies = parts.joinToString("; ")
            SecureStore.setSession(this, providerId, lastCookies)
            SecureStore.setSession(this, "web_$providerId", lastCookies)
            // also store under host key
            val host = try { java.net.URI(siteUrl).host ?: providerId } catch (_: Exception) { providerId }
            SecureStore.setSession(this, host.replace('.', '_'), lastCookies)
        }
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
    }
}
