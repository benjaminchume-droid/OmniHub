package com.omnihub.source.extension

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.omnihub.source.AiSource
import com.omnihub.source.bundled.WebProviderSource
import com.omnihub.source.SourceKind

/**
 * Discovers OmniSource packages installed on device (Mihon-style).
 * Source APKs declare meta-data:
 *   omnihub.source.id / name / kind / url
 */
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
                    pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)
                }
                val meta = ai.metaData ?: continue
                val id = meta.getString(META_ID) ?: pkg.removePrefix("com.omnisource.")
                val name = meta.getString(META_NAME) ?: id
                val kind = meta.getString(META_KIND) ?: "WEB"
                val url = meta.getString(META_URL) ?: "https://example.com"
                out.add(Installed(pkg, id, name, kind, url))
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

    /** Map source id → expected package name from factory. */
    fun packageForId(id: String): String {
        val suffix = id.lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
        return "com.omnisource.$suffix"
    }

    fun isSourceInstalled(context: Context, sourceId: String): Boolean {
        val pkg = packageForId(sourceId)
        if (isPackageInstalled(context, pkg)) return true
        // also match any scanned id
        return scan(context).any { it.id.equals(sourceId, true) || it.id.contains(sourceId, true) }
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
