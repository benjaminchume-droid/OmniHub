package com.omnihub.ui

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.omnihub.OmniHubApp
import com.omnihub.data.SecureStore
import com.omnihub.providers.impl.ProviderBootstrap

class WebLoginActivity : ComponentActivity() {
    private lateinit var web: WebView
    private var providerKey: String = "session"
    private var lastCookies: String = ""
    private var autoKeyRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Provider"
        providerKey = title.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(WebView(this), true)

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadsImagesAutomatically = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, loaded: String?) {
                    harvest(loaded ?: url)
                    view?.evaluateJavascript(
                        """
                        (function(){
                          try {
                            var out = {};
                            for (var i=0;i<localStorage.length;i++){
                              var k=localStorage.key(i);
                              var v=localStorage.getItem(k);
                              if (/token|session|auth|access|refresh|key|api/i.test(k) || /sk-|gsk_|AIza|sk-ant|sk-or-|nvapi-|pplx-/i.test(v||'')) {
                                out[k]=v;
                              }
                            }
                            return JSON.stringify(out);
                          } catch(e){ return '{}'; }
                        })();
                        """.trimIndent()
                    ) { json ->
                        if (json == null || json == "null" || json.length < 5) return@evaluateJavascript
                        val cleaned = json.trim().trim('"').replace("\\\"", "\"")
                        SecureStore.putSecret(this@WebLoginActivity, "web_local_$providerKey", cleaned)
                        tryExtractApiKey(cleaned)
                    }
                }
            }
        }
        setContentView(web)
        web.loadUrl(url)

        Toast.makeText(
            this,
            "Sign in. Sessions are captured. If an API key appears in page storage, chat can use it.",
            Toast.LENGTH_LONG
        ).show()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                harvest(web.url ?: url)
                val msg = when {
                    autoKeyRegistered -> "API key found and registered \u2014 you can chat"
                    lastCookies.isNotBlank() -> "Session captured for $title"
                    else -> "Closed"
                }
                Toast.makeText(this@WebLoginActivity, msg, Toast.LENGTH_SHORT).show()
                finish()
            }
        })
    }

    private fun tryExtractApiKey(jsonish: String) {
        if (autoKeyRegistered) return
        val pattern = Regex("(sk-ant-[A-Za-z0-9_\\-]{10,}|sk-or-[A-Za-z0-9_\\-]{10,}|sk-[A-Za-z0-9]{20,}|gsk_[A-Za-z0-9]{20,}|AIza[A-Za-z0-9_\\-]{20,}|nvapi-[A-Za-z0-9_\\-]{10,}|pplx-[A-Za-z0-9_\\-]{10,})")
        val match = pattern.find(jsonish) ?: return
        val key = match.value
        try {
            val app = applicationContext as OmniHubApp
            ProviderBootstrap.saveAndRegister(this, app.registry, key)
            autoKeyRegistered = true
        } catch (_: Exception) {
            SecureStore.setApiKey(this, providerKey, key)
        }
    }

    private fun harvest(url: String) {
        val cookies = CookieManager.getInstance().getCookie(url).orEmpty()
        if (cookies.isNotBlank()) {
            lastCookies = cookies
            SecureStore.setSession(this, providerKey, cookies)
            SecureStore.setSession(this, "web_$providerKey", cookies)
        }
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
    }
}
