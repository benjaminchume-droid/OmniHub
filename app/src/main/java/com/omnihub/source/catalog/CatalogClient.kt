package com.omnihub.source.catalog

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.omnihub.source.SourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File

data class SourceDescriptor(
    val id: String,
    val name: String,
    val type: String,
    val version: String,
    val description: String,
    val authType: String,
    val endpoint: String?
)

data class CatalogIndex(
    val version: Int,
    val lastUpdated: String,
    val sources: List<SourceDescriptor>
)

class CatalogClient(
    private val context: Context,
    private val sourceManager: SourceManager
) {
    private val gson = Gson()
    private val catalogUrl = "https://raw.githubusercontent.com/benjaminchume-droid/OmniHub/refs/heads/main/sources/index.min.json"
    private val cacheFile = File(context.cacheDir, "catalog.json")
    private val CACHE_DURATION = 86400000L // 24 hours

    suspend fun fetchCatalog(): CatalogIndex = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            if (cacheFile.exists() && System.currentTimeMillis() - cacheFile.lastModified() < CACHE_DURATION) {
                Log.d(TAG, "Using cached catalog")
                return@withContext parseCatalog(cacheFile.readText())
            }

            // Fetch from remote
            Log.d(TAG, "Fetching catalog from $catalogUrl")
            val response = okHttpClient.newCall(
                okhttp3.Request.Builder().url(catalogUrl).build()
            ).execute()

            if (!response.isSuccessful) {
                throw HttpException(response)
            }

            val json = response.body?.string() ?: "{}"
            cacheFile.writeText(json)
            Log.d(TAG, "Catalog cached")
            return@withContext parseCatalog(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch catalog", e)
            // Fall back to cache if available
            if (cacheFile.exists()) {
                return@withContext parseCatalog(cacheFile.readText())
            }
            throw e
        }
    }

    suspend fun installSource(descriptor: SourceDescriptor) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Installing source: ${descriptor.id}")
        // TODO: Download and instantiate source from descriptor
    }

    private fun parseCatalog(json: String): CatalogIndex {
        return gson.fromJson(json, CatalogIndex::class.java)
    }

    companion object {
        private const val TAG = "CatalogClient"
        private val okHttpClient = okhttp3.OkHttpClient()
    }
}
