package com.omnihub.soul

import android.content.Context
import java.io.File

class SoulManager(private val context: Context) {
    private val file: File
        get() = File(context.filesDir, "soul.md")

    fun read(): String = if (file.exists()) file.readText() else ""

    fun append(knowledge: String) {
        val existing = read()
        val combined = (existing + "\n" + knowledge).trim()
        val compressed = if (combined.length > 12000) combined.takeLast(12000) else combined
        file.writeText(compressed)
    }

    fun clear() {
        if (file.exists()) file.delete()
    }
}
