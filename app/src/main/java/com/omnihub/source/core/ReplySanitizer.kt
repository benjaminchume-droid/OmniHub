package com.omnihub.source.core

/**
 * Strip thinking blocks + provider chrome/disclaimers so the bubble is the answer only.
 * Universal — not tied to one site’s DOM.
 */
object ReplySanitizer {

    private val BLOCK_TAGS = Regex(
        "(?is)<\\s*(?:think(?:ing)?|reasoning|thought|analysis|scratchpad)\\b[^>]*>.*?</\\s*(?:think(?:ing)?|reasoning|thought|analysis|scratchpad)\\s*>"
    )
    private val FENCE = Regex(
        "(?is)```(?:thinking|reasoning|thought|analysis|internal)[\\s\\S]*?```"
    )
    private val HEADER_BLOCK = Regex(
        "(?im)^(?:thought process|thinking|reasoning|chain of thought|internal monologue|analysis|scratchpad)\\s*[:\\-]?\\s*\\n(?:.*\\n)*?(?=\\n\\n|\\z)"
    )
    private val HEADER_LINE = Regex(
        "(?im)^(?:thought process|thinking|reasoning|chain of thought|internal monologue|analysis)\\s*[:\\-]?\\s*$"
    )
    private val INLINE_LEAD = Regex(
        "(?is)^\\s*(?:thought process|thinking|reasoning)\\s*[:\\-]\\s*"
    )

    /** Single-line chrome: model badges, nav, disclaimers */
    private val CHROME_LINE = Regex(
        """(?ix)^
        (
          chat|agent|new\s+chat|sign\s+in|sign\s+up|log\s+in|api|
          terms\s+of\s+service|privacy\s+policy|menu|settings|home|
          upgrade|subscribe|download\s+app|get\s+the\s+app|share|copy|
          regenerate|continue|stop\s+generating|deep\s+think|max|flash|
          conversation\s+with\s+\w+|gemini\s+flash|chatgpt|claude|perplexity|
          you\s+said\b.*|\w+\s+said\b.*|
          .*can\s+make\s+mistakes.*|.*check\s+important\s+info.*|
          generated\s+by\s+ai\.?|for\s+reference\s+only\.?
        )
        $"""
    )

    private val DISCLAIMER = Regex(
        "(?is)\\s*(?:ChatGPT|Gemini|Claude|Perplexity|Grok|the AI|This model)\\s+(?:can|may)\\s+make\\s+mistakes[^.]*\\.?\\s*(?:Check important info\\.?)?"
    )

    private val YOU_SAID_BLOCK = Regex(
        "(?im)^(?:You said|Gemini said|ChatGPT said|Claude said|Assistant said)\\s*:?\\s*.*$"
    )

    fun strip(raw: String): String {
        var t = raw.trim()
        if (t.isEmpty()) return t
        t = BLOCK_TAGS.replace(t, "")
        t = FENCE.replace(t, "")
        t = HEADER_BLOCK.replace(t, "")
        t = DISCLAIMER.replace(t, "")
        t = t.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { HEADER_LINE.matches(it) }
            .filterNot { CHROME_LINE.matches(it) }
            .filterNot { YOU_SAID_BLOCK.matches(it) }
            .filterNot { it.length <= 2 && it.all { c -> c.isLetter() } } // stray "ey"
            .joinToString("\n")
        t = INLINE_LEAD.replace(t, "")
        t = t.replace(Regex("\n{3,}"), "\n\n").trim()
        return t
    }

    /**
     * Keep only the last assistant turn after the user's message, drop prior turns/chrome.
     */
    fun lastTurnOnly(raw: String, userMessage: String): String {
        var t = strip(raw)
        val um = userMessage.trim()
        if (um.isNotEmpty()) {
            val idx = t.lastIndexOf(um)
            if (idx >= 0) {
                t = t.substring(idx + um.length).trim()
                // drop a second echo of the user line if present
                if (t.startsWith(um)) t = t.removePrefix(um).trim()
            }
        }
        // If multiple greeting-like paragraphs, prefer the last substantial one
        val paras = t.split(Regex("\n{2,}")).map { it.trim() }.filter { it.length > 8 }
        if (paras.size > 1) {
            // Prefer last that doesn't look like chrome leftover
            t = paras.lastOrNull { !CHROME_LINE.matches(it.lineSequence().firstOrNull().orEmpty()) }
                ?: paras.last()
        }
        return strip(t)
    }
}
