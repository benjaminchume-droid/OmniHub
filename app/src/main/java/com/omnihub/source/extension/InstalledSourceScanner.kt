package com.omnihub.source.extension

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.omnihub.source.AiSource
import com.omnihub.source.SourceKind
import com.omnihub.source.bundled.WebProviderSource
import com.omnihub.source.core.ProviderCatalog

object InstalledSourceScanner {
    private const val TAG = "InstalledSourceScanner"
    private const val META_ID = "omnihub.source.id"
    private const val META_NAME = "omnihub.source.name"
    private const val META_KIND = "omnihub.source.kind"
    private const val META_URL = "omnihub.source.url"

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
                val rawId = meta?.getString(META_ID)
                    ?: pkg.removePrefix("com.omnisource.").replace('_', '-')
                val normId = normalizeId(rawId, pkg)
                val catalog = ProviderCatalog.ALL.find { it.id.equals(normId, true) }
                val name = meta?.getString(META_NAME)
                    ?: catalog?.name
                    ?: normId.replaceFirstChar { it.titlecase() }
                val kind = meta?.getString(META_KIND)
                    ?: catalog?.kind
                    ?: if (normId.startsWith("mcp")) "MCP" else "WEB"
                val url = meta?.getString(META_URL)?.takeIf { it.startsWith("http") }
                    ?: catalog?.url
                    ?: ProviderCatalog.urlFor(normId)
                    ?: "https://chatgpt.com"
                out.add(Installed(pkg, normId, name, kind, url))
            } catch (e: Exception) {
                Log.w(TAG, "skip $pkg: ${e.message}")
            }
        }
        return out.distinctBy { it.id }
    }

    private fun normalizeId(id: String, pkg: String): String {
        val s = id.lowercase()
        val p = pkg.lowercase()
        return when {
            s.contains("chatgpt") || p.contains("chatgpt") -> "chatgpt"
            s.contains("claude") || p.contains("claude") -> "claude"
            s.contains("gemini") || p.contains("gemini") -> "gemini"
            s.contains("perplexity") -> "perplexity"
            s.contains("deepseek") -> "deepseek"
            s.contains("grok") -> "grok"
            s.contains("groq") -> "groq"
            s.contains("kimi") -> "kimi"
            s.contains("zai") || s == "z_ai" || s == "z-ai" -> "zai"
            else -> s.replace('_', '-').trim('-')
        }
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
        if (isPackageInstalled(context, packageForId(sourceId))) return true
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
