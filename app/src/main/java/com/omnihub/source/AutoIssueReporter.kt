package com.omnihub.source

import android.content.Context
import android.util.Log

class AutoIssueReporter(private val context: Context) {

    private val prefs = context.getSharedPreferences("omni_source_failures", 0)

    fun reportFailure(sourceId: String, sourceName: String, error: String) {
        val key = "fail_$sourceId"
        val count = prefs.getInt(key, 0) + 1
        prefs.edit()
            .putInt(key, count)
            .putString("last_$sourceId", error.take(500))
            .putString("name_$sourceId", sourceName)
            .putLong("ts_$sourceId", System.currentTimeMillis())
            .apply()
        Log.w("OmniSource", "Source failure [$sourceName]: $error (count=$count)")
    }

    fun failureCount(sourceId: String): Int = prefs.getInt("fail_$sourceId", 0)

    fun lastError(sourceId: String): String? = prefs.getString("last_$sourceId", null)

    fun allFailures(): List<Triple<String, Int, String>> =
        prefs.all.keys
            .filter { it.startsWith("fail_") }
            .map { key ->
                val id = key.removePrefix("fail_")
                Triple(id, prefs.getInt(key, 0), prefs.getString("last_$id", "") ?: "")
            }
            .sortedByDescending { it.second }

    fun clear(sourceId: String) {
        prefs.edit()
            .remove("fail_$sourceId")
            .remove("last_$sourceId")
            .remove("ts_$sourceId")
            .remove("name_$sourceId")
            .apply()
    }

    fun githubIssueUrl(sourceId: String, sourceName: String): String {
        val err = lastError(sourceId) ?: "unknown"
        val title = java.net.URLEncoder.encode("[source] $sourceName failure", "UTF-8")
        val body = java.net.URLEncoder.encode(
            """
            ## Source failure auto-report
            - **Source:** $sourceName (`$sourceId`)
            - **Error:** `$err`
            - **Count:** ${failureCount(sourceId)}
            - **App:** OmniHub

            Auto-generated when routing failed on this source.
            """.trimIndent(),
            "UTF-8"
        )
        return "https://github.com/benjaminchume-droid/OmniHub/issues/new?title=$title&body=$body"
    }
}
