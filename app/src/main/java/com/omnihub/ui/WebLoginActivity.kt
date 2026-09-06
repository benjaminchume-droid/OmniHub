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
 * Sign-in only. User completes login in the WebView.
 * On back: save cookies and mark provider signed-in if cookies exist.
 */
class WebLoginActivity : ComponentActivity() {
    private lateinit var web: WebView
    private var providerId: String = "session"
    private var providerName: String = "Provider"
    private var lastCookies: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL) ?: intent.getStringExtra("url") ?: run { finish(); return }
        providerName = intent.getStringExtra(EXTRA_TITLE)
            ?: intent.getStringExtra("provider_name")
            ?: "Provider"
        providerId = intent.getStringExtra("provider_id")
            ?: providerName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

        CookieManager.getInstance().setAcceptCookie(true)

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, loaded: String?) {
                    harvest(loaded ?: url)
                }
            }
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        setContentView(web)
        web.loadUrl(url)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                harvest(web.url ?: url)
                val ok = lastCookies.isNotBlank()
                ProviderAuthStore.setSignedIn(this@WebLoginActivity, providerId, ok)
                Toast.makeText(
                    this@WebLoginActivity,
                    if (ok) "$providerName ready" else "No session yet",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        })
    }

    private fun harvest(url: String) {
        val cookies = CookieManager.getInstance().getCookie(url).orEmpty()
        if (cookies.isNotBlank()) {
            lastCookies = cookies
            SecureStore.setSession(this, providerId, cookies)
            SecureStore.setSession(this, "web_$providerId", cookies)
        }
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
    }
}
