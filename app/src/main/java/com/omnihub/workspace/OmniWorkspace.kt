package com.omnihub.workspace

import android.content.Context
import com.omnihub.data.UserPrefs
import java.io.File

/** Carved-out storage for AI file work. */
class OmniWorkspace(private val context: Context) {

    fun root(): File {
        val custom = UserPrefs.getOmniFolder(context)
        val dir = if (!custom.isNullOrBlank()) File(custom) else File(context.filesDir, "Omni")
        if (!dir.exists()) dir.mkdirs()
        File(dir, "projects").mkdirs()
        File(dir, "downloads").mkdirs()
        File(dir, "temp").mkdirs()
        File(dir, ".trash").mkdirs()
        return dir
    }

    fun resolve(relative: String): File {
        val clean = relative.trim().removePrefix("/").replace("..", "")
        return File(root(), clean)
    }

    fun list(relative: String = ""): List<File> {
        val dir = if (relative.isBlank()) root() else resolve(relative)
        return dir.listFiles()?.sortedBy { it.name.lowercase() }?.toList() ?: emptyList()
    }

    fun readText(relative: String): String = resolve(relative).readText()

    fun writeText(relative: String, content: String): File {
        val f = resolve(relative)
        f.parentFile?.mkdirs()
        f.writeText(content)
        return f
    }

    fun deleteToTrash(relative: String): Boolean {
        val f = resolve(relative)
        if (!f.exists()) return false
        val trash = File(root(), ".trash/${System.currentTimeMillis()}_${f.name}")
        trash.parentFile?.mkdirs()
        return f.renameTo(trash)
    }

    fun exists(relative: String): Boolean = resolve(relative).exists()
}
