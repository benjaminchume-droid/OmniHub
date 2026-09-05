package com.omnihub.source.websession

import com.omnihub.source.SourceCatalog
import com.omnihub.source.SourceDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ProtocolUpdate(
    val sourceId: String,
    val currentRevision: String,
    val newRevision: String,
    val changelog: String = ""
)

class ProtocolUpdater {
    suspend fun checkUpdates(
        installed: Map<String, String>,
        catalogUrl: String? = null
    ): List<ProtocolUpdate> = withContext(Dispatchers.IO) {
        val catalog: List<SourceDescriptor> = try {
            SourceCatalog.fetch(catalogUrl ?: SourceCatalog.DEFAULT_INDEX)
        } catch (_: Exception) {
            emptyList()
        }
        catalog.mapNotNull { d ->
            val current = installed[d.id] ?: return@mapNotNull null
            if (d.revision != current) {
                ProtocolUpdate(d.id, current, d.revision, "Catalog revision changed")
            } else null
        }
    }
}
