package com.omnihub.source

import com.omnihub.providers.ChatRequest
import com.omnihub.providers.ChatResponse
import com.omnihub.providers.ModelInfo
import kotlinx.coroutines.flow.Flow

/**
 * Abstract interface for all AI sources (bundled, remote, web, or extensions).
 * Replaces provider-specific implementations with a unified protocol.
 */
interface AiSource {
    /**
     * Unique identifier: "openai", "anthropic", "chatgpt-web", etc.
     */
    val id: String

    /**
     * Display name: "ChatGPT", "Claude", "ChatGPT (Web)"
     */
    val name: String

    /**
     * Brief description
     */
    val description: String

    /**
     * Icon drawable resource ID
     */
    val icon: Int

    /**
     * Source type: API, WEB_SESSION, EXTENSION, MCP
     */
    val type: SourceType

    /**
     * Authentication type required
     */
    val authType: AuthType

    /**
     * Available models/models for this source
     */
    val models: List<ModelInfo>

    /**
     * Default model if user doesn't specify
     */
    val defaultModel: String

    /**
     * Source version (for updates)
     */
    val version: String

    /**
     * Whether source is currently enabled and ready
     */
    val isEnabled: Boolean

    /**
     * Send single chat request and wait for response
     */
    suspend fun chat(request: ChatRequest): ChatResponse

    /**
     * Stream response as it arrives
     */
    suspend fun stream(request: ChatRequest): Flow<String>

    /**
     * Verify if credentials/auth is valid
     */
    suspend fun validateCredentials(): Boolean

    /**
     * Get estimated cost for a request (in USD or source-specific currency)
     */
    fun estimateCost(inputTokens: Int, outputTokens: Int): Double

    /**
     * Get average latency for this source (in ms)
     */
    fun getAverageLatency(): Long

    /**
     * Get reliability score (0.0 to 1.0)
     */
    fun getReliabilityScore(): Double

    /**
     * Called when source is installed
     */
    suspend fun onInstall() {}

    /**
     * Called when source is being updated
     */
    suspend fun onUpdate(fromVersion: String) {}

    /**
     * Called when source is uninstalled
     */
    suspend fun onUninstall() {}
}

enum class SourceType {
    API,                // OpenAI, Anthropic, etc.
    WEB_SESSION,       // ChatGPT.com, Claude.ai, etc.
    EXTENSION,         // APK extension
    MCP,               // Model Context Protocol
    LOCAL              // Local model (Ollama, etc.)
}

enum class AuthType {
    API_KEY,           // "sk-..." style
    WEB_SESSION,       // Cookies, localStorage
    USERNAME_PASSWORD, // Direct auth
    OAUTH2,            // OAuth flow
    NONE               // No auth (local models)
}
