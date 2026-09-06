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
 * Universal WebCore send path.
 * Does not hardcode button positions or site-specific selectors as requirements.
 * PageSensor scores visible inputs/buttons; PageActor types+sends; ReplyObserver waits for stable new text.
 */
object WebViewChatEngine {

    data class Result(val text: String, val ok: Boolean)

    suspend fun send(
        context: Context,
        siteUrl: String,
        providerId: String,
        userMessage: String,
        timeoutMs: Long = 120_000L
    ): Result = suspendCancellableCoroutine { cont ->
        val main = Handler(Looper.getMainLooper())
        main.post {
            @SuppressLint("SetJavaScriptEnabled")
            val web = WebView(context.applicationContext)
            var finished = false
            fun complete(text: String, ok: Boolean) {
                if (finished) return
                finished = true
                try {
                    web.stopLoading()
                    web.destroy()
                } catch (_: Exception) {
                }
                if (cont.isActive) cont.resume(Result(text, ok))
            }

            val bridge = object {
                @JavascriptInterface
                fun onReply(text: String) {
                    main.post {
                        val t = text.trim()
                        when {
                            t.startsWith("ERR:") -> complete(t.removePrefix("ERR:").ifBlank { "Page error" }, false)
                            t.isBlank() -> complete("Empty reply from page.", false)
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
                    main.postDelayed({
                        view?.evaluateJavascript(buildUniversalScript(userMessage), null)
                    }, 2800)
                }
            }

            cont.invokeOnCancellation {
                main.post {
                    try {
                        web.stopLoading()
                        web.destroy()
                    } catch (_: Exception) {
                    }
                }
            }

            main.postDelayed({
                complete("Timed out waiting for reply on page.", false)
            }, timeoutMs)

            val target = siteUrl.ifBlank {
                ProviderCatalog.urlFor(providerId) ?: "https://chatgpt.com/"
            }
            web.loadUrl(target)
        }
    }

    /**
     * Universal sensor + actor + observer. No required fixed selectors.
     * Optional soft text hints only boost scores.
     */
    private fun buildUniversalScript(message: String): String {
        val escaped = message
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")

        return """
(function(){
  var MSG = '$escaped';
  function fail(t){ try{ OmniBridge.onReply('ERR:'+t); }catch(e){} }
  function ok(t){ try{ OmniBridge.onReply(t); }catch(e){} }

  function visible(el){
    if(!el) return false;
    var r = el.getBoundingClientRect();
    if(r.width < 8 || r.height < 8) return false;
    var st = window.getComputedStyle(el);
    if(st.display==='none' || st.visibility==='hidden' || st.opacity==='0') return false;
    return r.bottom > 0 && r.top < (window.innerHeight||800);
  }

  function scoreInput(el){
    var s = 0;
    var r = el.getBoundingClientRect();
    var tag = (el.tagName||'').toLowerCase();
    var ph = ((el.getAttribute('placeholder')||'') + ' ' + (el.getAttribute('aria-label')||'') + ' ' + (el.getAttribute('data-testid')||'') + ' ' + (el.id||'')).toLowerCase();
    if(tag==='textarea') s += 30;
    if(el.isContentEditable || el.getAttribute('contenteditable')==='true') s += 28;
    if(tag==='input' && (el.type==='text'||el.type==='search'||!el.type)) s += 12;
    if(r.width > 180) s += 15;
    if(r.height > 24) s += 8;
    if(r.top > (window.innerHeight||800)*0.45) s += 20; // lower half
    if(/message|ask|prompt|chat|send a|type|write|talk/.test(ph)) s += 25;
    if(/search|email|password|user/.test(ph)) s -= 40;
    if(!visible(el)) s -= 100;
    if(el.disabled || el.readOnly) s -= 50;
    return s;
  }

  function findInput(){
    var nodes = Array.prototype.slice.call(document.querySelectorAll('textarea, [contenteditable="true"], input[type="text"], input:not([type]), input[type="search"]'));
    var best = null, bestScore = -999;
    nodes.forEach(function(el){
      var sc = scoreInput(el);
      if(sc > bestScore){ bestScore = sc; best = el; }
    });
    if(best && bestScore >= 20) return best;
    return null;
  }

  function scoreSend(btn, inputRect){
    var s = 0;
    var r = btn.getBoundingClientRect();
    var label = ((btn.getAttribute('aria-label')||'') + ' ' + (btn.innerText||'') + ' ' + (btn.getAttribute('data-testid')||'') + ' ' + (btn.getAttribute('title')||'')).toLowerCase();
    if(/send|submit|arrow|reply/.test(label)) s += 35;
    if(btn.tagName==='BUTTON') s += 10;
    if(inputRect){
      var dx = Math.abs((r.left+r.right)/2 - (inputRect.left+inputRect.right)/2);
      var dy = Math.abs((r.top+r.bottom)/2 - (inputRect.top+inputRect.bottom)/2);
      if(dy < 80 && dx < 420) s += 25;
      if(r.left >= inputRect.right - 20) s += 10;
    }
    if(r.top > (window.innerHeight||800)*0.5) s += 10;
    if(!visible(btn)) s -= 100;
    if(btn.disabled) s -= 30;
    return s;
  }

  function findSend(input){
    var inputRect = input ? input.getBoundingClientRect() : null;
    var nodes = Array.prototype.slice.call(document.querySelectorAll('button, [role="button"], input[type="submit"]'));
    var best = null, bestScore = -999;
    nodes.forEach(function(el){
      var sc = scoreSend(el, inputRect);
      if(sc > bestScore){ bestScore = sc; best = el; }
    });
    if(best && bestScore >= 25) return best;
    return null;
  }

  function chatRoot(){
    return document.querySelector('main') || document.querySelector('[role="main"]') || document.body;
  }

  function snapshotText(){
    var root = chatRoot();
    return (root && (root.innerText || root.textContent) || '').trim();
  }

  function setNativeValue(el, value){
    try {
      var proto = el.tagName==='TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
      var desc = Object.getOwnPropertyDescriptor(proto, 'value');
      if(desc && desc.set) desc.set.call(el, value); else el.value = value;
    } catch(e){ el.value = value; }
    el.dispatchEvent(new Event('input', {bubbles:true}));
    el.dispatchEvent(new Event('change', {bubbles:true}));
  }

  function fill(el, text){
    el.focus();
    if(el.isContentEditable || el.getAttribute('contenteditable')==='true'){
      el.textContent = text;
      try {
        el.dispatchEvent(new InputEvent('input', {bubbles:true, data:text, inputType:'insertText'}));
      } catch(e){
        el.dispatchEvent(new Event('input', {bubbles:true}));
      }
    } else {
      setNativeValue(el, text);
    }
  }

  function pressEnter(el){
    ['keydown','keypress','keyup'].forEach(function(type){
      el.dispatchEvent(new KeyboardEvent(type, {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true}));
    });
  }

  var input = findInput();
  if(!input){ fail('No chat input found on page. Sign in and open the chat screen first.'); return; }

  var before = snapshotText();
  fill(input, MSG);

  setTimeout(function(){
    var btn = findSend(input);
    if(btn){ try { btn.click(); } catch(e) { pressEnter(input); } }
    else { pressEnter(input); }

    var stable = 0;
    var last = '';
    var tries = 0;
    var timer = setInterval(function(){
      tries++;
      var now = snapshotText();
      // Prefer growth after our send
      if(now && now.length > before.length + Math.min(8, MSG.length)){
        // extract tail delta roughly
        var delta = now;
        if(before && now.indexOf(before) === 0) delta = now.slice(before.length).trim();
        // ignore pure echo of user message
        if(delta && delta.indexOf(MSG) === 0 && delta.length < MSG.length + 8) {
          // still waiting
        } else if(delta && delta.length > 2){
          if(delta === last){
            stable++;
            if(stable >= 3){
              clearInterval(timer);
              ok(delta);
            }
          } else {
            stable = 0;
            last = delta;
          }
        }
      }
      if(tries > 90){
        clearInterval(timer);
        if(last && last.length > 2) ok(last);
        else fail('No assistant reply detected.');
      }
    }, 800);
  }, 350);
})();
        """.trimIndent()
    }
}
