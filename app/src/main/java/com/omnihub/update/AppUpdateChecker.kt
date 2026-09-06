package com.omnihub.update

import android.content.Context
import android.widget.Toast
import com.omnihub.BuildConfig
import com.omnihub.source.extension.ApkInstaller
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
    private const val PREFS = "omni_updates"
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun checkOmniHub(
        context: Context,
        owner: String = "benjaminchume-droid",
        repo: String = "OmniHub"
    ): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$owner/$repo/releases?per_page=10"
        val req = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = http.newCall(req).execute().use { it.body?.string().orEmpty() }
        if (!body.startsWith("[")) return@withContext null
        val arr = JSONArray(body)
        if (arr.length() == 0) return@withContext null

        val installedTag = context.getSharedPreferences(PREFS, 0)
            .getString("installed_release_tag", "")
            .orEmpty()

        // Prefer newest release that has an APK (nightly or stable)
        for (i in 0 until arr.length()) {
            val rel = arr.getJSONObject(i)
            val tag = rel.optString("tag_name")
            var apkUrl: String? = null
            val assets = rel.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val a = assets.getJSONObject(j)
                val n = a.optString("name")
                if (n.endsWith(".apk", true)) {
                    apkUrl = a.optString("browser_download_url")
                    break
                }
            }
            if (apkUrl.isNullOrBlank()) continue
            if (tag == installedTag) return@withContext null // already on this build
            // Skip if same as what we already marked dismissed
            val dismissed = context.getSharedPreferences(PREFS, 0)
                .getString("dismissed_release_tag", "")
            if (tag == dismissed) continue

            val isNightly = tag.startsWith("nightly", true)
            val newerStable = !isNightly && isNewer(tag, BuildConfig.VERSION_NAME)
            val newerNightly = isNightly && tag != installedTag
            if (!newerStable && !newerNightly && installedTag.isNotBlank()) continue
            if (!newerStable && !newerNightly && installedTag.isBlank()) {
                // first run: only prompt if remote tag clearly different channel
                if (!isNightly && !isNewer(tag, BuildConfig.VERSION_NAME)) continue
            }

            return@withContext AppUpdateInfo(
                tag = tag,
                name = rel.optString("name", tag),
                body = rel.optString("body", ""),
                apkUrl = apkUrl,
                htmlUrl = rel.optString("html_url"),
                publishedAt = rel.optString("published_at")
            )
        }
        null
    }

    fun isNewer(remoteTag: String, localVersion: String): Boolean {
        fun parts(s: String): List<Int> {
            val core = s.removePrefix("v")
                .substringBefore("-")
                .replace("nightly", "")
                .split(".")
            return core.mapNotNull { it.toIntOrNull() }
        }
        val r = parts(remoteTag)
        val l = parts(localVersion)
        if (r.isEmpty()) return remoteTag != localVersion
        val n = maxOf(r.size, l.size)
        for (i in 0 until n) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a > b) return true
            if (a < b) return false
        }
        return false
    }

    fun dismiss(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, 0).edit()
            .putString("dismissed_release_tag", tag)
            .apply()
    }

    fun markInstalled(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, 0).edit()
            .putString("installed_release_tag", tag)
            .apply()
    }

    /** Download APK and open system installer — no browser. */
    suspend fun downloadAndInstall(context: Context, info: AppUpdateInfo): Result<Unit> =
        withContext(Dispatchers.IO) {
            val url = info.apkUrl ?: return@withContext Result.failure(IllegalStateException("No APK asset"))
            runCatching {
                val file = ApkInstaller.downloadApk(context, url, "OmniHub-update.apk")
                markInstalled(context, info.tag)
                withContext(Dispatchers.Main) {
                    ApkInstaller.promptInstall(context, file)
                    Toast.makeText(context, "Install the update when prompted", Toast.LENGTH_LONG).show()
                }
            }
        }
}
