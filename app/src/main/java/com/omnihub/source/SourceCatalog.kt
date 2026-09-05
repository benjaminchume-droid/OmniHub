package com.omnihub.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SourceCatalog {
    const val DEFAULT_INDEX =
        "https://raw.githubusercontent.com/benjaminchume-droid/OmniHub-Sources/main/catalog/index.min.json"

    private val PARTS = listOf(
        "https://raw.githubusercontent.com/benjaminchume-droid/OmniHub-Sources/main/catalog/api.min.json",
        "https://raw.githubusercontent.com/benjaminchume-droid/OmniHub-Sources/main/catalog/web.min.json",
        "https://raw.githubusercontent.com/benjaminchume-droid/OmniHub-Sources/main/catalog/mcp.min.json"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(indexUrl: String = DEFAULT_INDEX): List<SourceDescriptor> =
        withContext(Dispatchers.IO) {
            val primary = runCatching { download(indexUrl) }.getOrNull()
            val fromIndex = primary?.let { parseIndex(it) }.orEmpty()
            if (fromIndex.size >= 50) return@withContext fromIndex

            // Fallback / merge multipartite catalogs
            val merged = linkedMapOf<String, SourceDescriptor>()
            fromIndex.forEach { merged[it.id] = it }
            for (url in PARTS) {
                runCatching { parseIndex(download(url)) }.getOrNull()?.forEach { merged[it.id] = it }
            }
            if (merged.isEmpty()) error("Empty catalog from $indexUrl")
            merged.values.toList()
        }

    private fun download(url: String): String {
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: error("Empty catalog")
            if (!resp.isSuccessful) error("Catalog ${resp.code}: ${body.take(200)}")
            return body
        }
    }

    fun parseIndex(json: String): List<SourceDescriptor> {
        val root = JSONObject(json)
        val arr: JSONArray = when {
            root.has("sources") -> root.getJSONArray("sources")
            else -> JSONArray(json)
        }
        val out = mutableListOf<SourceDescriptor>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val headers = mutableMapOf<String, String>()
            o.optJSONObject("headers")?.let { h ->
                h.keys().forEach { k -> headers[k] = h.getString(k) }
            }
            val models = mutableListOf<String>()
            o.optJSONArray("models")?.let { m ->
                for (j in 0 until m.length()) models.add(m.getString(j))
            }
            out.add(
                SourceDescriptor(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    kind = o.optString("kind", "API"),
                    authType = o.optString("authType", "API_KEY"),
                    version = o.optInt("version", 1),
                    revision = o.optString("revision", "1.0.0"),
                    description = o.optString("description", ""),
                    websiteUrl = o.optString("websiteUrl", ""),
                    baseUrl = o.optString("baseUrl", "").ifBlank { null },
                    chatPath = o.optString("chatPath", "").ifBlank { null },
                    models = models,
                    headers = headers,
                    apkUrl = o.optString("apkUrl", "").ifBlank { null },
                    nsfw = o.optBoolean("nsfw", false)
                )
            )
        }
        return out
    }
}
