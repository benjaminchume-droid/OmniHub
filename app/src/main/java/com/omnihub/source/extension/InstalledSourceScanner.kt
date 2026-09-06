package com.omnihub.source.extension

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.omnihub.source.AiSource
import com.omnihub.source.SourceKind
import com.omnihub.source.bundled.WebProviderSource

object InstalledSourceScanner {
    private const val TAG = "InstalledSourceScanner"
    private const val META_ID = "omnihub.source.id"
    private const val META_NAME = "omnihub.source.name"
    private const val META_KIND = "omnihub.source.kind"
    private const val META_URL = "omnihub.source.url"

    private val KNOWN_URLS = mapOf(
        "chatgpt" to "https://chatgpt.com",
        "claude" to "https://claude.ai",
        "gemini" to "https://gemini.google.com",
        "perplexity" to "https://www.perplexity.ai",
        "deepseek" to "https://chat.deepseek.com",
        "grok" to "https://x.com/i/grok",
        "groq" to "https://chat.groq.com",
        "kimi" to "https://kimi.moonshot.cn",
        "zai" to "https://chat.z.ai",
        "hy3" to "https://hy3.ai"
    )

    data class Installed(
        val packageName: String,
        val id: String,
        val name: String,
        val kind: String,
        val websiteUrl: String
    )

    fun scan(context: Context): List<Installed> {
        val pm = context.packageManager
        val out = mutableListOf<Installed>()
        val packages = try {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "scan failed: ${e.message}")
            return emptyList()
        }

        for (appInfo in packages) {
            val pkg = appInfo.packageName
            if (!pkg.startsWith("com.omnisource.")) continue
            try {
                val ai = if (Build.VERSION.SDK_INT >= 33) {
                    pm.getApplicationInfo(
                        pkg,
                        PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)
                }
                val meta = ai.metaData
                val id = meta?.getString(META_ID)
                    ?: pkg.removePrefix("com.omnisource.").replace('_', '-').substringBefore('-')
                    .ifBlank { pkg.removePrefix("com.omnisource.") }
                // normalize common ids
                val normId = when {
                    id.contains("chatgpt", true) || pkg.contains("chatgpt") -> "chatgpt"
                    id.contains("claude", true) -> "claude"
                    id.contains("gemini", true) -> "gemini"
                    id.contains("perplexity", true) -> "perplexity"
                    id.contains("deepseek", true) -> "deepseek"
                    id.contains("grok", true) -> "grok"
                    id.contains("groq", true) -> "groq"
                    id.contains("kimi", true) -> "kimi"
                    id.contains("zai", true) || id == "z_ai" -> "zai"
                    else -> id
                }
                val name = meta?.getString(META_NAME)
                    ?: KNOWN_URLS.keys.find { it == normId }?.replaceFirstChar { it.titlecase() }
                    ?: normId
                val kind = meta?.getString(META_KIND) ?: if (normId.startsWith("mcp")) "MCP" else "WEB"
                val url = meta?.getString(META_URL)
                    ?.takeIf { it.startsWith("http") }
                    ?: KNOWN_URLS[normId]
                    ?: "https://chatgpt.com"
                out.add(Installed(pkg, normId, name, kind, url))
            } catch (e: Exception) {
                Log.w(TAG, "skip $pkg: ${e.message}")
            }
        }
        return out.distinctBy { it.id }
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean =
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: Exception) {
            false
        }

    fun packageForId(id: String): String {
        val suffix = id.lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
        return "com.omnisource.$suffix"
    }

    fun isSourceInstalled(context: Context, sourceId: String): Boolean {
        val pkg = packageForId(sourceId)
        if (isPackageInstalled(context, pkg)) return true
        return scan(context).any {
            it.id.equals(sourceId, true) ||
                it.id.contains(sourceId, true) ||
                sourceId.contains(it.id, true)
        }
    }

    fun toSources(context: Context): List<AiSource> =
        scan(context).map { i ->
            val kind = if (i.kind.contains("MCP", true)) SourceKind.MCP else SourceKind.WEB
            WebProviderSource(
                context = context,
                sourceId = i.id,
                sourceName = i.name,
                siteUrl = i.websiteUrl,
                description = "Installed · ${i.packageName}",
                kind = kind
            )
        }
}
