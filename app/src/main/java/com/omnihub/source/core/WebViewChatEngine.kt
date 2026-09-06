package com.omnihub.source.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Real WebCore send path: load the provider site in a WebView (with existing cookies),
 * inject the user message into the page UI, wait for an assistant bubble, return text.
 *
 * This is intentionally not cookie→OkHttp. The page talks to the provider itself.
 */
object WebViewChatEngine {

    data class Result(val text: String, val ok: Boolean)

    suspend fun send(
        context: Context,
        siteUrl: String,
        providerId: String,
        userMessage: String,
        timeoutMs: Long = 90_000L
    ): Result = suspendCancellableCoroutine { cont ->
        val main = Handler(Looper.getMainLooper())
        main.post {
            @SuppressLint("SetJavaScriptEnabled")
            val web = WebView(context.applicationContext)
            var finished = false
            fun complete(text: String, ok: Boolean) {
                if (finished) return
                finished = true
                try { web.stopLoading(); web.destroy() } catch (_: Exception) {}
                if (cont.isActive) cont.resume(Result(text, ok))
            }

            val bridge = object {
                @JavascriptInterface
                fun onReply(text: String) {
                    main.post {
                        val t = text.trim()
                        when {
                            t.startsWith("ERR:") -> complete(t.removePrefix("ERR:"), false)
                            t.isBlank() -> complete("No reply from page.", false)
                            else -> complete(t, true)
                        }
                    }
                }
            }

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

            web.settings.javaScriptEnabled = true
            web.settings.domStorageEnabled = true
            web.settings.databaseEnabled = true
            web.settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
            web.addJavascriptInterface(bridge, "OmniBridge")

            web.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Give the SPA a moment, then inject
                    main.postDelayed({
                        val js = buildInjectScript(providerId, userMessage)
                        view?.evaluateJavascript(js, null)
                    }, 2500)
                }
            }

            cont.invokeOnCancellation {
                main.post {
                    try { web.stopLoading(); web.destroy() } catch (_: Exception) {}
                }
            }

            main.postDelayed({
                complete("Timed out waiting for $providerId page reply.", false)
            }, timeoutMs)

            val target = when {
                providerId.contains("chatgpt", true) -> "https://chatgpt.com/"
                siteUrl.isNotBlank() -> siteUrl
                else -> "https://chatgpt.com/"
            }
            web.loadUrl(target)
        }
    }

    private fun buildInjectScript(providerId: String, message: String): String {
        val escaped = message
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")

        // ChatGPT-focused selectors; generic textarea fallback for other sites
        return """
            (function() {
              var msg = '$escaped';
              function fail(t) { OmniBridge.onReply('ERR:' + t); }
              function ok(t) { OmniBridge.onReply(t); }

              function findInput() {
                return document.querySelector('#prompt-textarea')
                  || document.querySelector('[data-testid="prompt-textarea"]')
                  || document.querySelector('div[contenteditable="true"]')
                  || document.querySelector('textarea');
              }

              function findSend() {
                return document.querySelector('[data-testid="send-button"]')
                  || document.querySelector('button[aria-label*="Send"]')
                  || document.querySelector('button[data-testid="fruitjuice-send-button"]');
              }

              function lastAssistant() {
                var nodes = document.querySelectorAll('[data-message-author-role="assistant"]');
                if (nodes && nodes.length) {
                  var n = nodes[nodes.length - 1];
                  return (n.innerText || n.textContent || '').trim();
                }
                // generic: last large prose block after user
                var arts = document.querySelectorAll('article, .markdown, .prose');
                if (arts && arts.length) {
                  return (arts[arts.length-1].innerText || '').trim();
                }
                return '';
              }

              var input = findInput();
              if (!input) { fail('Chat input not found — sign in and open a chat first.'); return; }

              var before = lastAssistant();

              try {
                input.focus();
                if (input.tagName === 'TEXTAREA' || input.tagName === 'INPUT') {
                  var nativeSet = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value')
                    || Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
                  if (nativeSet && nativeSet.set) nativeSet.set.call(input, msg);
                  else input.value = msg;
                  input.dispatchEvent(new Event('input', { bubbles: true }));
                } else {
                  input.textContent = msg;
                  input.dispatchEvent(new InputEvent('input', { bubbles: true, data: msg }));
                }
              } catch (e) { fail('Could not fill input: ' + e); return; }

              setTimeout(function() {
                var btn = findSend();
                if (btn && !btn.disabled) {
                  btn.click();
                } else {
                  // Enter key fallback
                  input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', bubbles: true }));
                }

                var tries = 0;
                var timer = setInterval(function() {
                  tries++;
                  var text = lastAssistant();
                  if (text && text !== before && text.length > 1) {
                    clearInterval(timer);
                    ok(text);
                  } else if (tries > 60) {
                    clearInterval(timer);
                    fail('No assistant reply detected on page.');
                  }
                }, 1000);
              }, 400);
            })();
        """.trimIndent()
    }
}
