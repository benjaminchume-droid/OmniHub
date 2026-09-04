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

/**
 * Primary web-session path.
 * User signs in inside the WebView. We continuously harvest cookies/tokens
 * and store them encrypted under the provider key. No need to copy anything.
 */
class WebLoginActivity : ComponentActivity() {
    private lateinit var web: WebView
    private var providerKey: String = "session"
    private var lastCookies: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Provider"
        providerKey = title.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')

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
                              if (/token|session|auth|access|refresh|key/i.test(k)) {
                                out[k]=localStorage.getItem(k);
                              }
                            }
                            return JSON.stringify(out);
                          } catch(e){ return '{}'; }
                        })();
                        """.trimIndent()
                    ) { json ->
                        if (json != null && json != "null" && json != "\"{}\"" && json.length > 5) {
                            SecureStore.putSecret(
                                this@WebLoginActivity,
                                "web_local_$providerKey",
                                json.trim('"').replace("\\\"", "\"")
                            )
                        }
                    }
                }
            }
        }
        setContentView(web)
        web.loadUrl(url)

        Toast.makeText(
            this,
            "Sign in. OmniHub captures the session automatically. Press back when finished.",
            Toast.LENGTH_LONG
        ).show()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                harvest(web.url ?: url)
                if (lastCookies.isNotBlank()) {
                    Toast.makeText(
                        this@WebLoginActivity,
                        "Session captured for $title",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                finish()
            }
        })
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
