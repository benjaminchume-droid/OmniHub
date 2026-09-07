package com.omnihub.source.core

/**
 * Strip model "thinking / thought process / reasoning" blocks from live and final replies.
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

    fun strip(raw: String): String {
        var t = raw.trim()
        if (t.isEmpty()) return t
        t = BLOCK_TAGS.replace(t, "")
        t = FENCE.replace(t, "")
        t = HEADER_BLOCK.replace(t, "")
        t = t.lines()
            .filterNot { HEADER_LINE.matches(it.trim()) }
            .joinToString("\n")
        t = INLINE_LEAD.replace(t, "")
        // collapse excess blank lines
        t = t.replace(Regex("\n{3,}"), "\n\n").trim()
        return t
    }
}
