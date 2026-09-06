package com.omnihub.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.omnihub.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val tag: String,
    val name: String,
    val body: String,
    val apkUrl: String?,
    val htmlUrl: String,
    val publishedAt: String
)

object AppUpdateChecker {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun checkOmniHub(
        owner: String = "benjaminchume-droid",
        repo: String = "OmniHub"
    ): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$owner/$repo/releases?per_page=5"
        val req = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = http.newCall(req).execute().use { it.body?.string().orEmpty() }
        if (!body.startsWith("[")) return@withContext null
        val arr = JSONArray(body)
        if (arr.length() == 0) return@withContext null
        val latest = arr.getJSONObject(0)
        val tag = latest.optString("tag_name")
        if (!isNewer(tag, BuildConfig.VERSION_NAME)) return@withContext null
        var apkUrl: String? = null
        val assets = latest.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val n = a.optString("name")
                if (n.endsWith(".apk", true)) {
                    apkUrl = a.optString("browser_download_url")
                    break
                }
            }
        }
        AppUpdateInfo(
            tag = tag,
            name = latest.optString("name", tag),
            body = latest.optString("body", ""),
            apkUrl = apkUrl,
            htmlUrl = latest.optString("html_url"),
            publishedAt = latest.optString("published_at")
        )
    }

    /** Compare semver-ish tags like v1.0.6-123 vs 1.0.5 */
    fun isNewer(remoteTag: String, localVersion: String): Boolean {
        fun parts(s: String): List<Int> {
            val core = s.removePrefix("v").substringBefore("-").split(".")
            return core.mapNotNull { it.toIntOrNull() }
        }
        val r = parts(remoteTag)
        val l = parts(localVersion)
        val n = maxOf(r.size, l.size)
        for (i in 0 until n) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a > b) return true
            if (a < b) return false
        }
        return false
    }

    fun openReleasePage(context: Context, info: AppUpdateInfo) {
        val uri = Uri.parse(info.htmlUrl.ifBlank { info.apkUrl ?: return })
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
