package com.omnihub.history

import android.content.Context
import java.io.File

object IncognitoRetention {
    private const val DIR = "incognito_trash"
    private const val MAX_AGE_MS = 20L * 24 * 60 * 60 * 1000

    fun trashDir(context: Context): File =
        File(context.filesDir, DIR).also { it.mkdirs() }

    fun stash(context: Context, conversationId: String, payload: String) {
        val f = File(trashDir(context), "${System.currentTimeMillis()}_$conversationId.json")
        f.writeText(payload)
    }

    fun purgeExpired(context: Context) {
        val now = System.currentTimeMillis()
        trashDir(context).listFiles()?.forEach { f ->
            if (now - f.lastModified() > MAX_AGE_MS) f.delete()
        }
    }
}
