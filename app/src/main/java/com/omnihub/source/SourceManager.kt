package com.omnihub.source

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Enhanced Source Manager with lifecycle, updates, and multi-account support.
 * 
 * Manages:
 * - Source installation/uninstallation
 * - Source updates and versioning
 * - Account authentication and aggregation
 * - Source health monitoring
 * - Catalog synchronization
 */
class SourceManager(
    private val context: Context,
    private val catalog: SourceCatalog
) {
    private val installationMutex = Mutex()
    
    // Installed sources
    private val _installedSources = MutableStateFlow<List<AiSource>>(emptyList())
    val installedSources: StateFlow<List<AiSource>> = _installedSources
    
    // Source health status
    private val _healthStatus = MutableStateFlow<Map<String, SourceHealth>>(emptyMap())
    val healthStatus: StateFlow<Map<String, SourceHealth>> = _healthStatus
    
    // Installation directory
    private val sourcesDir = File(context.filesDir, "sources")
    
    init {
        // Initialize sources directory
        if (!sourcesDir.exists()) {
            sourcesDir.mkdirs()
        }
        
        // Load installed sources
        loadInstalledSources()
    }
    
    /**
     * Install a source from catalog.
     */
    suspend fun installSource(sourceId: String): Result<Unit> {
        return installationMutex.withLock {
            try {
                // Check if already installed
                if (_installedSources.value.any { it.id == sourceId }) {
                    return@withLock Result.failure(AlreadyInstalledException("Source $sourceId already installed"))
                }
                
                // Get source definition from catalog
                val sourceDef = catalog.getSource(sourceId) 
                    ?: return@withLock Result.failure(SourceNotFoundException("Source $sourceId not found in catalog"))
                
                // Verify signature
                if (!verifySourceSignature(sourceDef)) {
                    return@withLock Result.failure(SecurityException("Source signature verification failed"))
                }
                
                // Check compatibility
                if (!isCompatible(sourceDef)) {
                    return@withLock Result.failure(CompatibilityException(
                        "Source ${sourceDef.name} is not compatible with this OmniHub version"
                    ))
                }
                
                // Download source
                val sourceFile = downloadSource(sourceDef)
                
                // Install source
                val source = loadSource(sourceFile, sourceDef)
                
                // Add to installed list
                val updatedList = _installedSources.value.toMutableList()
                updatedList.add(source)
                _installedSources.value = updatedList
                
                // Initialize source
                source.initialize(context)
                
                Log.i("SourceManager", "Installed source: ${sourceDef.name} v${sourceDef.version}")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("SourceManager", "Failed to install source: $sourceId", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Uninstall a source.
     */
    suspend fun uninstallSource(sourceId: String): Result<Unit> {
        return installationMutex.withLock {
            try {
                val source = _installedSources.value.find { it.id == sourceId }
                    ?: return@withLock Result.failure(SourceNotFoundException("Source $sourceId not installed"))
                
                // Remove from installed list
                val updatedList = _installedSources.value.toMutableList()
                updatedList.remove(source)
                _installedSources.value = updatedList
                
                // Cleanup
                source.cleanup()
                
                // Delete source file
                val sourceFile = File(sourcesDir, "$sourceId.apk")
                if (sourceFile.exists()) {
                    sourceFile.delete()
                }
                
                Log.i("SourceManager", "Uninstalled source: ${source.name}")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("SourceManager", "Failed to uninstall source: $sourceId", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Update a source to latest version.
     */
    suspend fun updateSource(sourceId: String): Result<Unit> {
        return installationMutex.withLock {
            try {
                val source = _installedSources.value.find { it.id == sourceId }
                    ?: return@withLock Result.failure(SourceNotFoundException("Source $sourceId not installed"))
                
                // Check for update
                val latestDef = catalog.getSource(sourceId)
                    ?: return@withLock Result.failure(SourceNotFoundException("Source $sourceId not found in catalog"))
                
                if (latestDef.version <= source.version) {
                    return@withLock Result.failure(NoUpdateAvailableException(
                        "Source ${source.name} is already up to date"
                    ))
                }
                
                // Uninstall old version
                uninstallSource(sourceId)
                
                // Install new version
                installSource(sourceId)
                
                Log.i("SourceManager", "Updated source: ${source.name} v${source.version} -> v${latestDef.version}")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("SourceManager", "Failed to update source: $sourceId", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Add an account to a source.
     */
    suspend fun addAccount(sourceId: String, account: SourceAccount): Result<Unit> {
        return try {
            val source = _installedSources.value.find { it.id == sourceId }
                ?: return Result.failure(SourceNotFoundException("Source $sourceId not installed"))
            
            // Authenticate account
            val authenticatedAccount = source.authenticate(account)
                ?: return Result.failure(AuthenticationException("Failed to authenticate account"))
            
            // Add to source
            source.addAccount(authenticatedAccount)
            
            Log.i("SourceManager", "Added account to source: ${source.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SourceManager", "Failed to add account to source: $sourceId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Remove an account from a source.
     */
    suspend fun removeAccount(sourceId: String, accountId: String): Result<Unit> {
        return try {
            val source = _installedSources.value.find { it.id == sourceId }
                ?: return Result.failure(SourceNotFoundException("Source $sourceId not installed"))
            
            // Remove account
            source.removeAccount(accountId)
            
            Log.i("SourceManager", "Removed account from source: ${source.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SourceManager", "Failed to remove account from source: $sourceId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get all available accounts across all sources.
     */
    fun getAllAccounts(): List<SourceAccount> {
        return _installedSources.value.flatMap { it.getAccounts() }
    }
    
    /**
     * Get sources by capability.
     */
    fun getSourcesByCapability(capability: SourceCapability): List<AiSource> {
        return _installedSources.value.filter { it.capabilities.contains(capability) }
    }
    
    /**
     * Check for source updates.
     */
    suspend fun checkForUpdates(): List<SourceUpdate> {
        return _installedSources.value.mapNotNull { source ->
            val latestDef = catalog.getSource(source.id)
            if (latestDef != null && latestDef.version > source.version) {
                SourceUpdate(
                    sourceId = source.id,
                    sourceName = source.name,
                    currentVersion = source.version,
                    latestVersion = latestDef.version,
                    changelog = latestDef.changelog
                )
            } else null
        }
    }
    
    /**
     * Update all sources.
     */
    suspend fun updateAllSources(): Map<String, Result<Unit>> {
        val updates = checkForUpdates()
        return updates.associate { update ->
            update.sourceId to updateSource(update.sourceId)
        }
    }
    
    /**
     * Monitor source health.
     */
    suspend fun monitorHealth() {
        val healthMap = _installedSources.value.associate { source ->
            source.id to source.getHealthStatus()
        }
        _healthStatus.value = healthMap
    }
    
    /**
     * Load installed sources from disk.
     */
    private fun loadInstalledSources() {
        try {
            val sources = sourcesDir.listFiles { file ->
                file.extension == "apk" || file.extension == "jar"
            }?.mapNotNull { file ->
                val sourceId = file.nameWithoutExtension
                val sourceDef = catalog.getSource(sourceId)
                if (sourceDef != null) {
                    loadSource(file, sourceDef)
                } else null
            } ?: emptyList()
            
            _installedSources.value = sources
            Log.i("SourceManager", "Loaded ${sources.size} installed sources")
        } catch (e: Exception) {
            Log.e("SourceManager", "Failed to load installed sources", e)
            _installedSources.value = emptyList()
        }
    }
    
    /**
     * Verify source signature.
     */
    private fun verifySourceSignature(sourceDef: SourceDefinition): Boolean {
        // TODO: Implement actual signature verification
        // For now, accept all sources
        return true
    }
    
    /**
     * Check source compatibility.
     */
    private fun isCompatible(sourceDef: SourceDefinition): Boolean {
        val currentOmniVersion = getOmniHubVersion()
        return currentOmniVersion >= sourceDef.minimumOmniVersion &&
               currentOmniVersion <= (sourceDef.maximumOmniVersion ?: Long.MAX_VALUE)
    }
    
    /**
     * Download source file.
     */
    private suspend fun downloadSource(sourceDef: SourceDefinition): File {
        val sourceFile = File(sourcesDir, "${sourceDef.id}.apk")
        
        // TODO: Implement actual download from catalog URL
        // For now, create empty file
        sourceFile.createNewFile()
        
        return sourceFile
    }
    
    /**
     * Load source from file.
     */
    private fun loadSource(file: File, sourceDef: SourceDefinition): AiSource {
        // TODO: Implement actual source loading (APK/JAR loading)
        // For now, return stub implementation
        return StubAiSource(sourceDef)
    }
    
    /**
     * Get OmniHub version.
     */
    private fun getOmniHubVersion(): Long {
        // TODO: Get from BuildConfig or package manager
        return 1L
    }
}

/**
 * Source update information.
 */
data class SourceUpdate(
    val sourceId: String,
    val sourceName: String,
    val currentVersion: Long,
    val latestVersion: Long,
    val changelog: String?
)

/**
 * Source health status.
 */
data class SourceHealth(
    val isHealthy: Boolean,
    val lastChecked: Long,
    val errorCount: Int,
    val successRate: Double,
    val averageLatencyMs: Long
)

/**
 * Already installed exception.
 */
class AlreadyInstalledException(message: String) : Exception(message)

/**
 * Source not found exception.
 */
class SourceNotFoundException(message: String) : Exception(message)

/**
 * Compatibility exception.
 */
class CompatibilityException(message: String) : Exception(message)

/**
 * No update available exception.
 */
class NoUpdateAvailableException(message: String) : Exception(message)

/**
 * Authentication exception.
 */
class AuthenticationException(message: String) : Exception(message)

/**
 * Stub AiSource for development.
 */
class StubAiSource(
    private val definition: SourceDefinition
) : AiSource {
    override val id: String = definition.id
    override val name: String = definition.name
    override val version: Long = definition.version
    override val capabilities: Set<SourceCapability> = definition.capabilities
    override val defaultModel: String = definition.defaultModel ?: "default"
    
    override fun initialize(context: android.content.Context) {}
    override fun cleanup() {}
    override fun getAvailableAccounts(): List<SourceAccount> = emptyList()
    override fun authenticate(account: SourceAccount): SourceAccount? = null
    override fun addAccount(account: SourceAccount) {}
    override fun removeAccount(accountId: String) {}
    override fun getAccounts(): List<SourceAccount> = emptyList()
    override suspend fun execute(instruction: String, account: SourceAccount?, model: String?, context: ExecutionContext): String = "Stub response"
    override fun getHealthStatus(): SourceHealth = SourceHealth(true, System.currentTimeMillis(), 0, 1.0, 100)
    override fun isHealthy(): Boolean = true
    override fun specializesIn(intent: IntentType): Boolean = false
    override val qualityScore: Double = 0.5
    override val costPerRequest: Double = 0.0
    override val averageLatencyMs: Long = 100
    override fun supportsIntent(intent: TaskIntent): Boolean = true
}