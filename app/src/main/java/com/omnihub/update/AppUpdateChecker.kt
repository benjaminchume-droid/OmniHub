package com.omnihub.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.omnihub.BuildConfig
import com.omnihub.source.extension.ApkInstaller
import com.omnihub.source.extension.InstalledSourceScanner
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

data class SourceUpdateInfo(
    val sourceId: String,
    val packageName: String,
    val installedVersion: String,
    val remoteVersion: String,
    val apkUrl: String,
    val tag: String
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
        val url = "https://api.github.com/repos/$owner/$repo/releases?per_page=20"
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
        val dismissed = context.getSharedPreferences(PREFS, 0)
            .getString("dismissed_release_tag", "")
            .orEmpty()

        val local = BuildConfig.VERSION_NAME

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
            if (tag == installedTag || tag == dismissed) continue

            // Compare semantic 1.x.x.x OR treat newer nightly tag as update
            val tagVersion = tag
                .removePrefix("v")
                .substringAfter("nightly-", tag)
                .let {
                    if (it.matches(Regex("\\d+\\.\\d+.*"))) it
                    else rel.optString("name").ifBlank { tag }
                }

            val newer = isNewer(tagVersion, local) || isNewer(tag, local)
            if (!newer && tag == local) continue
            if (!newer && !tag.startsWith("nightly", true) && !tag.contains(local.substringBeforeLast('.'))) {
                // still allow if remote tag literally contains higher version
                if (!isNewer(extractVersion(tag) ?: tag, local)) continue
            }
            if (!newer && !tag.startsWith("nightly", true)) continue

            // Prefer any release with APK that is newer than local versionName
            val candidateNewer = isNewer(extractVersion(tag) ?: tagVersion, local)
            val isNightlyNewer = tag.startsWith("nightly", true) && tag != installedTag
            if (!candidateNewer && !isNightlyNewer) continue

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

    /** Check OmniHub-Sources releases for newer APKs matching installed packages. */
    suspend fun checkSourceUpdates(
        context: Context,
        owner: String = "benjaminchume-droid",
        repo: String = "OmniHub-Sources"
    ): List<SourceUpdateInfo> = withContext(Dispatchers.IO) {
        val installed = InstalledSourceScanner.scan(context)
        if (installed.isEmpty()) return@withContext emptyList()

        val url = "https://api.github.com/repos/$owner/$repo/releases?per_page=10"
        val req = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = http.newCall(req).execute().use { it.body?.string().orEmpty() }
        if (!body.startsWith("[")) return@withContext emptyList()

        val out = mutableListOf<SourceUpdateInfo>()
        val arr = JSONArray(body)
        val pm = context.packageManager

        for (i in 0 until arr.length()) {
            val rel = arr.getJSONObject(i)
            val tag = rel.optString("tag_name")
            val assets = rel.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val a = assets.getJSONObject(j)
                val name = a.optString("name")
                if (!name.endsWith(".apk", true)) continue
                val apkUrl = a.optString("browser_download_url")
                // name pattern: chatgpt-1.0.0.apk or chatgpt-1.0.0.1.apk
                val base = name.removeSuffix(".apk")
                val idGuess = base.substringBeforeLast("-").ifBlank { base }
                val remoteVer = base.substringAfterLast("-", "1.0.0")
                val match = installed.find {
                    it.id.equals(idGuess, true) ||
                        name.startsWith(it.id, true) ||
                        it.packageName.endsWith(idGuess.replace('-', '_'), true)
                } ?: continue

                val localVer = try {
                    val pi = if (Build.VERSION.SDK_INT >= 33) {
                        pm.getPackageInfo(match.packageName, PackageManager.PackageInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(match.packageName, 0)
                    }
                    pi.versionName ?: "0"
                } catch (_: Exception) {
                    "0"
                }

                if (isNewer(remoteVer, localVer)) {
                    out.add(
                        SourceUpdateInfo(
                            sourceId = match.id,
                            packageName = match.packageName,
                            installedVersion = localVer,
                            remoteVersion = remoteVer,
                            apkUrl = apkUrl,
                            tag = tag
                        )
                    )
                }
            }
        }
        out.distinctBy { it.sourceId }
    }

    private fun extractVersion(s: String): String? {
        val m = Regex("(\\d+\\.\\d+(?:\\.\\d+){0,3})").find(s)
        return m?.groupValues?.getOrNull(1)
    }

    fun isNewer(remoteTag: String, localVersion: String): Boolean {
        fun parts(s: String): List<Int> {
            val core = s.removePrefix("v")
                .substringBefore("-")
                .replace("nightly", "", ignoreCase = true)
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

    fun openReleasePage(context: Context, info: AppUpdateInfo) {
        val uri = Uri.parse(info.htmlUrl.ifBlank { info.apkUrl ?: return })
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    suspend fun downloadAndInstall(context: Context, info: AppUpdateInfo): Result<Unit> =
        withContext(Dispatchers.IO) {
            val url = info.apkUrl
                ?: return@withContext Result.failure(IllegalStateException("No APK asset"))
            runCatching {
                val file = ApkInstaller.downloadApk(context, url, "OmniHub-update.apk")
                markInstalled(context, info.tag)
                withContext(Dispatchers.Main) {
                    ApkInstaller.promptInstall(context, file)
                    Toast.makeText(context, "Install the update when prompted", Toast.LENGTH_LONG).show()
                }
            }
        }

    suspend fun downloadAndInstallSource(context: Context, info: SourceUpdateInfo): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = ApkInstaller.downloadApk(
                    context, info.apkUrl, "${info.sourceId}-update.apk"
                )
                withContext(Dispatchers.Main) {
                    ApkInstaller.promptInstall(context, file)
                    Toast.makeText(
                        context,
                        "Update ${info.sourceId} ${info.remoteVersion}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
}
