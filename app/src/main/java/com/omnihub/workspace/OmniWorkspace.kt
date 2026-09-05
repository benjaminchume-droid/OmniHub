package com.omnihub.workspace

import android.content.Context
import java.io.File

/**
 * Semantic workspace roots under app-controlled storage.
 * User-selected external trees use SAF + persisted permissions.
 */
class OmniWorkspace(context: Context) {
    private val root = File(context.filesDir, "Omni").also { it.mkdirs() }

    val projects: File get() = dir("projects")
    val documents: File get() = dir("documents")
    val downloads: File get() = dir("downloads")
    val generated: File get() = dir("generated")
    val scripts: File get() = dir("scripts")
    val agents: File get() = dir("agents")
    val tasks: File get() = dir("tasks")
    val memory: File get() = dir("memory")
    val temp: File get() = dir("temp")
    val logs: File get() = dir("logs")

    private fun dir(name: String): File = File(root, name).also { it.mkdirs() }

    fun resolveProject(name: String): File? {
        val p = File(projects, name)
        return if (p.exists()) p else projects.listFiles()?.firstOrNull {
            it.name.contains(name, ignoreCase = true)
        }
    }

    fun writeGenerated(fileName: String, content: String): File {
        val f = File(generated, fileName)
        f.writeText(content)
        return f
    }
}
