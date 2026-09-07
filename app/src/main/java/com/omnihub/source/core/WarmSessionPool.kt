package com.omnihub.source.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.ConcurrentHashMap

/**
 * One long-lived WebView per provider so multi-turn stays on the same page.
 * Main-thread only for WebView access.
 */
object WarmSessionPool {

    data class Session(
        val web: WebView,
        var ready: Boolean = false,
        var lastUrl: String = "",
        var busy: Boolean = false
    )

    private val main = Handler(Looper.getMainLooper())
    private val sessions = ConcurrentHashMap<String, Session>()

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreate(context: Context, providerId: String): Session {
        sessions[providerId]?.let { return it }
        val app = context.applicationContext
        val web = WebView(app)
        try {
            web.layoutParams = ViewGroup.LayoutParams(1080, 1920)
            web.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY)
            )
            web.layout(0, 0, 1080, 1920)
        } catch (_: Exception) {
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString =
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
        }
        val session = Session(web)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                session.lastUrl = url.orEmpty()
                session.ready = true
            }
        }
        sessions[providerId] = session
        return session
    }

    fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else main.post(block)
    }

    fun release(providerId: String) {
        runOnMain {
            sessions.remove(providerId)?.let { s ->
                try {
                    s.web.stopLoading()
                    s.web.destroy()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun releaseAll() {
        sessions.keys.toList().forEach { release(it) }
    }
}
