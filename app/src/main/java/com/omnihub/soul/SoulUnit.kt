package com.omnihub.soul

import java.io.Serializable

/**
 * Compressed memory unit: single fact/topic extracted from conversation.
 * These are aggregated into memory.md for cross-provider context.
 */
data class SoulUnit(
    val id: String,                      // "soul-uuid-1"
    val timestamp: Long,                 // Creation time
    val sourceId: String,                // "openai", "anthropic", etc.
    val conversationId: String,          // Link to original conversation
    val topic: String,                   // "Python async patterns"
    val summary: String,                 // Compressed facts
    val keyFacts: List<String>,          // Individual facts
    val topicTags: List<String>,         // #python #async
    val compressed: String,              // LZ4 compressed JSON
    val embeddings: FloatArray? = null,  // For semantic search (Phase 2+)
    val importance: Double = 1.0         // 0.0-1.0 for ranking
) : Serializable

data class SoulMetadata(
    val generated: Long,
    val version: String = "1.0",
    val lastUpdated: Long,
    val storagePath: String,
    val providersUsed: List<String>,
    val totalConversations: Int,
    val totalMemoryUnits: Int,
    val compressedSize: Long
)

/**
 * Request to create soul units from conversation
 */
data class SoulGenerationRequest(
    val conversationId: String,
    val sourceId: String,
    val messages: List<ConversationMessage>,
    val model: String
)

data class ConversationMessage(
    val role: String,  // "user", "assistant"
    val content: String,
    val timestamp: Long
)
