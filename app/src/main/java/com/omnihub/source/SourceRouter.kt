package com.omnihub.source

import com.omnihub.mcp.McpClient
import com.omnihub.data.UserPreferences
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.content.Context
import android.util.Log

/**
 * Enhanced Omni Source Router with AgentRuntime, Task execution, and permission boundaries.
 * 
 * Routes requests to appropriate sources based on:
 * - Task type (chat, code, research, files, etc.)
 * - Source capabilities
 * - Account availability and quotas
 * - User preferences and routing policies
 * - Permission boundaries for destructive operations
 */
class SourceRouter(
    private val context: Context,
    private val sourceManager: SourceManager,
    private val mcpClient: McpClient,
    private val userPrefs: UserPreferences
) {
    private val routingMutex = Mutex()
    
    // Routing decision cache
    private val routingCache = mutableMapOf<String, RoutingDecision>()
    
    /**
     * Route a request to the best source/account/model combination.
     * 
     * @param instruction The user's instruction/request
     * @param capabilities Required capabilities (CHAT, CODE, FILES, etc.)
     * @param preferences User routing preferences
     * @return RoutingDecision with selected source, account, and model
     */
    suspend fun route(
        instruction: String,
        capabilities: Set<SourceCapability> = emptySet(),
        preferences: RoutingPreferences = RoutingPreferences()
    ): RoutingDecision {
        return routingMutex.withLock {
            // Check cache first
            val cacheKey = generateCacheKey(instruction, capabilities)
            routingCache[cacheKey]?.let { return@withLock it }
            
            // Analyze instruction for intent
            val intent = analyzeIntent(instruction)
            
            // Find capable sources
            val capableSources = sourceManager.getInstalledSources()
                .filter { source ->
                    capabilities.all { source.capabilities.contains(it) } &&
                    source.supportsIntent(intent)
                }
            
            if (capableSources.isEmpty()) {
                throw RoutingException("No sources available with required capabilities: $capabilities")
            }
            
            // Score each source
            val scoredSources = capableSources.map { source ->
                scoreSource(source, instruction, intent, preferences)
            }.sortedByDescending { it.score }
            
            // Select best source
            val bestSource = scoredSources.firstOrNull() 
                ?: throw RoutingException("Failed to score sources")
            
            // Check permissions for destructive operations
            if (intent.requiresPermission) {
                val permissionGranted = checkPermission(intent, bestSource.source)
                if (!permissionGranted) {
                    throw PermissionDeniedException(
                        "Permission denied for operation: ${intent.type}"
                    )
                }
            }
            
            // Build routing decision
            val decision = RoutingDecision(
                source = bestSource.source,
                account = bestSource.account,
                model = bestSource.model,
                score = bestSource.score,
                intent = intent,
                timestamp = System.currentTimeMillis()
            )
            
            // Cache decision
            routingCache[cacheKey] = decision
            
            decision
        }
    }
    
    /**
     * Execute a task through the routed source.
     * 
     * @param instruction The user's instruction
     * @param capabilities Required capabilities
     * @param preferences Routing preferences
     * @return ExecutionResult with response and metadata
     */
    suspend fun execute(
        instruction: String,
        capabilities: Set<SourceCapability> = emptySet(),
        preferences: RoutingPreferences = RoutingPreferences()
    ): ExecutionResult {
        val decision = route(instruction, capabilities, preferences)
        
        return try {
            // Log execution start
            Log.d("SourceRouter", "Executing via ${decision.source.id} with model ${decision.model}")
            
            // Execute through source
            val response = decision.source.execute(
                instruction = instruction,
                account = decision.account,
                model = decision.model,
                context = buildExecutionContext(instruction)
            )
            
            ExecutionResult(
                success = true,
                response = response,
                routingDecision = decision,
                executionTimeMs = System.currentTimeMillis() - decision.timestamp
            )
        } catch (e: Exception) {
            Log.e("SourceRouter", "Execution failed", e)
            ExecutionResult(
                success = false,
                error = e,
                routingDecision = decision,
                executionTimeMs = System.currentTimeMillis() - decision.timestamp
            )
        }
    }
    
    /**
     * Check if an operation requires explicit user permission.
     */
    private suspend fun checkPermission(intent: TaskIntent, source: AiSource): Boolean {
        return when (intent.type) {
            IntentType.DELETE_FILE, 
            IntentType.SEND_MESSAGE,
            IntentType.MAKE_PURCHASE,
            IntentType.DEPLOY_CODE,
            IntentType.SIGN_ARTIFACT,
            IntentType.ACCESS_CREDENTIALS -> {
                // Require biometric or explicit confirmation
                userPrefs.isBiometricEnabled() && userPrefs.isPermissionGranted(intent.type.name)
            }
            else -> true // No permission required
        }
    }
    
    /**
     * Analyze instruction to determine intent.
     */
    private fun analyzeIntent(instruction: String): TaskIntent {
        val lowerInstruction = instruction.lowercase()
        
        return when {
            lowerInstruction.contains("delete") || lowerInstruction.contains("remove") ->
                TaskIntent(IntentType.DELETE_FILE, requiresPermission = true)
            lowerInstruction.contains("send") && (lowerInstruction.contains("message") || lowerInstruction.contains("email")) ->
                TaskIntent(IntentType.SEND_MESSAGE, requiresPermission = true)
            lowerInstruction.contains("buy") || lowerInstruction.contains("purchase") ->
                TaskIntent(IntentType.MAKE_PURCHASE, requiresPermission = true)
            lowerInstruction.contains("deploy") || lowerInstruction.contains("publish") ->
                TaskIntent(IntentType.DEPLOY_CODE, requiresPermission = true)
            lowerInstruction.contains("sign") || lowerInstruction.contains("certificate") ->
                TaskIntent(IntentType.SIGN_ARTIFACT, requiresPermission = true)
            lowerInstruction.contains("password") || lowerInstruction.contains("api key") || lowerInstruction.contains("token") ->
                TaskIntent(IntentType.ACCESS_CREDENTIALS, requiresPermission = true)
            lowerInstruction.contains("code") || lowerInstruction.contains("program") || lowerInstruction.contains("function") ->
                TaskIntent(IntentType.WRITE_CODE, requiresPermission = false)
            lowerInstruction.contains("research") || lowerInstruction.contains("search") ->
                TaskIntent(IntentType.RESEARCH, requiresPermission = false)
            lowerInstruction.contains("file") || lowerInstruction.contains("document") ->
                TaskIntent(IntentType.FILE_OPERATION, requiresPermission = false)
            else -> TaskIntent(IntentType.CHAT, requiresPermission = false)
        }
    }
    
    /**
     * Score a source based on multiple factors.
     */
    private fun scoreSource(
        source: AiSource,
        instruction: String,
        intent: TaskIntent,
        preferences: RoutingPreferences
    ): ScoredSource {
        var score = 0.0
        var bestAccount: SourceAccount? = null
        var bestModel: String? = null
        
        // Base score from source quality
        score += source.qualityScore * 20
        
        // Capability match bonus
        score += source.capabilities.size * 5
        
        // Account availability and quota
        val availableAccounts = source.getAvailableAccounts()
        if (availableAccounts.isNotEmpty()) {
            bestAccount = availableAccounts.maxByOrNull { it.remainingQuota } ?: availableAccounts.first()
            score += (bestAccount.remainingQuota / 100.0) * 15
            bestModel = bestAccount.preferredModel ?: source.defaultModel
        }
        
        // Latency bonus (lower latency = higher score)
        score += (1000 - source.averageLatencyMs) / 100.0 * 10
        
        // Cost penalty (higher cost = lower score)
        score -= source.costPerRequest * 5
        
        // User preference bonus
        if (source.id == preferences.preferredSourceId) {
            score += 25
        }
        
        // Intent specialization bonus
        if (source.specializesIn(intent.type)) {
            score += 30
        }
        
        // Health check
        if (!source.isHealthy()) {
            score -= 50
        }
        
        return ScoredSource(
            source = source,
            account = bestAccount,
            model = bestModel,
            score = score
        )
    }
    
    /**
     * Build execution context for the source.
     */
    private fun buildExecutionContext(instruction: String): ExecutionContext {
        return ExecutionContext(
            instruction = instruction,
            timestamp = System.currentTimeMillis(),
            userId = userPrefs.getUserId(),
            sessionId = userPrefs.getSessionId(),
            locale = context.resources.configuration.locale,
            timezone = java.util.TimeZone.getDefault().id
        )
    }
    
    /**
     * Generate cache key for routing decisions.
     */
    private fun generateCacheKey(instruction: String, capabilities: Set<SourceCapability>): String {
        return "${instruction.hashCode()}:${capabilities.joinToString(",")}"
    }
    
    /**
     * Clear routing cache.
     */
    fun clearCache() {
        routingCache.clear()
    }
}

/**
 * Routing decision result.
 */
data class RoutingDecision(
    val source: AiSource,
    val account: SourceAccount?,
    val model: String?,
    val score: Double,
    val intent: TaskIntent,
    val timestamp: Long
)

/**
 * Execution result from a routed request.
 */
data class ExecutionResult(
    val success: Boolean,
    val response: String? = null,
    val error: Exception? = null,
    val routingDecision: RoutingDecision,
    val executionTimeMs: Long
)

/**
 * Task intent analysis.
 */
data class TaskIntent(
    val type: IntentType,
    val requiresPermission: Boolean
)

enum class IntentType {
    CHAT,
    WRITE_CODE,
    RESEARCH,
    FILE_OPERATION,
    DELETE_FILE,
    SEND_MESSAGE,
    MAKE_PURCHASE,
    DEPLOY_CODE,
    SIGN_ARTIFACT,
    ACCESS_CREDENTIALS
}

/**
 * Routing preferences from user.
 */
data class RoutingPreferences(
    val preferredSourceId: String? = null,
    val maxCostPerRequest: Double? = null,
    val maxLatencyMs: Long? = null,
    val requireBiometricForDestructive: Boolean = true
)

/**
 * Scored source for routing.
 */
data class ScoredSource(
    val source: AiSource,
    val account: SourceAccount?,
    val model: String?,
    val score: Double
)

/**
 * Execution context passed to sources.
 */
data class ExecutionContext(
    val instruction: String,
    val timestamp: Long,
    val userId: String?,
    val sessionId: String?,
    val locale: java.util.Locale,
    val timezone: String
)

/**
 * Routing exception.
 */
class RoutingException(message: String) : Exception(message)

/**
 * Permission denied exception.
 */
class PermissionDeniedException(message: String) : Exception(message)