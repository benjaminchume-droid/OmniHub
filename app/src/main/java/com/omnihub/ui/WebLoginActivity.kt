package com.omnihub.ui

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.omnihub.data.SecureStore

class WebLoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Provider"

        CookieManager.getInstance().setAcceptCookie(true)
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, loaded: String?) {
                val cookies = CookieManager.getInstance().getCookie(loaded ?: url).orEmpty()
                if (cookies.isNotBlank()) {
                    SecureStore.setSession(this@WebLoginActivity, title.lowercase(), cookies)
                }
            }
        }
        setContentView(web)
        web.loadUrl(url)
        Toast.makeText(
            this,
            "Sign in, then copy an API key from the dashboard and paste it in OmniHub.",
            Toast.LENGTH_LONG
        ).show()
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
    }
}
