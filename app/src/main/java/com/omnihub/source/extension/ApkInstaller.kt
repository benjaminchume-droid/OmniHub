package com.omnihub.source.extension

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit

object ApkInstaller {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    data class ReleaseItem(
        val id: String,
        val name: String,
        val tag: String,
        val kind: String,
        val apkUrl: String,
        val size: Long,
        val publishedAt: String
    )

    suspend fun fetchReleases(
        owner: String = "benjaminchume-droid",
        repo: String = "OmniHub-Sources"
    ): List<ReleaseItem> = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$owner/$repo/releases?per_page=100"
        val req = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = http.newCall(req).execute().use { it.body?.string().orEmpty() }
        val arr = JSONArray(if (body.startsWith("[")) body else "[]")
        val out = mutableListOf<ReleaseItem>()
        for (i in 0 until arr.length()) {
            val rel = arr.getJSONObject(i)
            val tag = rel.optString("tag_name")
            val assets = rel.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val a = assets.getJSONObject(j)
                val name = a.optString("name")
                if (!name.endsWith(".apk", true)) continue
                val id = name.removeSuffix(".apk").substringBefore("-v").substringBefore("_")
                val kind = if (name.contains("mcp", true)) "MCP" else "WEB"
                out.add(
                    ReleaseItem(
                        id = id,
                        name = name.removeSuffix(".apk"),
                        tag = tag,
                        kind = kind,
                        apkUrl = a.optString("browser_download_url"),
                        size = a.optLong("size"),
                        publishedAt = rel.optString("published_at")
                    )
                )
            }
        }
        out
    }

    suspend fun downloadApk(context: Context, apkUrl: String, fileName: String): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "source_apks").also { it.mkdirs() }
            val dest = File(dir, fileName)
            val req = Request.Builder().url(apkUrl).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Download failed: ${resp.code}")
                dest.outputStream().use { out -> resp.body?.byteStream()?.copyTo(out) }
            }
            dest
        }

    fun promptInstall(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
