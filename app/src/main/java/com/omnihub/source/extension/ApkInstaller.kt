package com.omnihub.source.extension

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

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

    data class DownloadProgress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val percent: Int,
        val bytesPerSec: Long,
        val etaSeconds: Long?
    )

    private fun idFromApkName(name: String): String {
        val base = name.removeSuffix(".apk").removeSuffix(".APK")
        val m = Regex("^(.+)-\\d+(?:\\.\\d+){1,3}$").find(base)
        return m?.groupValues?.get(1) ?: base.substringBefore("-v").ifBlank { base }
    }

    private fun displayName(id: String): String =
        id.replace('_', ' ').split(' ').joinToString(" ") { part ->
            part.replaceFirstChar { c -> c.titlecase() }
        }

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
        val seen = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            val rel = arr.getJSONObject(i)
            val tag = rel.optString("tag_name")
            val assets = rel.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val a = assets.getJSONObject(j)
                val name = a.optString("name")
                if (!name.endsWith(".apk", true)) continue
                val id = idFromApkName(name)
                if (id in seen) continue
                seen.add(id)
                val kind = if (id.startsWith("mcp") || name.contains("mcp", true)) "MCP" else "WEB"
                out.add(
                    ReleaseItem(
                        id = id,
                        name = displayName(id),
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

    /**
     * Resumable download with progress.
     * Uses HTTP Range when a partial file already exists (idempotent across reconnects).
     */
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        fileName: String,
        onProgress: ((DownloadProgress) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "source_apks").also { it.mkdirs() }
        val dest = File(dir, fileName)
        val part = File(dir, "$fileName.part")

        var existing = if (part.exists()) part.length() else 0L

        val reqBuilder = Request.Builder().url(apkUrl)
        if (existing > 0) {
            reqBuilder.header("Range", "bytes=$existing-")
        }
        val req = reqBuilder.build()

        http.newCall(req).execute().use { resp ->
            when (resp.code) {
                200 -> {
                    // Server ignored range — start over
                    existing = 0L
                    if (part.exists()) part.delete()
                }
                206 -> { /* resume */ }
                else -> error("Download failed: HTTP ${resp.code}")
            }

            val body = resp.body ?: error("Empty body")
            val contentLength = body.contentLength()
            val total = when {
                resp.code == 206 && contentLength >= 0 -> existing + contentLength
                contentLength >= 0 -> contentLength
                else -> -1L
            }

            RandomAccessFile(part, "rw").use { raf ->
                if (existing > 0 && resp.code == 206) raf.seek(existing)
                else {
                    raf.setLength(0)
                    existing = 0L
                }
                val input = body.byteStream()
                val buf = ByteArray(64 * 1024)
                var downloaded = existing
                var lastTick = System.currentTimeMillis()
                var windowBytes = 0L
                var speed = 0L

                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buf)
                    if (read < 0) break
                    raf.write(buf, 0, read)
                    downloaded += read
                    windowBytes += read
                    val now = System.currentTimeMillis()
                    if (now - lastTick >= 400) {
                        speed = (windowBytes * 1000L) / (now - lastTick).coerceAtLeast(1)
                        windowBytes = 0
                        lastTick = now
                        val pct = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else -1
                        val eta = if (speed > 0 && total > 0) ((total - downloaded) / speed) else null
                        onProgress?.invoke(
                            DownloadProgress(downloaded, total, pct, speed, eta)
                        )
                    }
                }
                onProgress?.invoke(
                    DownloadProgress(
                        downloaded,
                        if (total > 0) total else downloaded,
                        100,
                        speed,
                        0
                    )
                )
            }
        }

        if (dest.exists()) dest.delete()
        if (!part.renameTo(dest)) {
            part.copyTo(dest, overwrite = true)
            part.delete()
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
