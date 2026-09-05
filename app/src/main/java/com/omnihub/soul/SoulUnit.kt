package com.omnihub.soul

import java.io.Serializable

enum class SoulType {
    PROFILE, PREFERENCE, PROJECT, PERSON, FACT, DECISION, GOAL, TASK,
    EVENT, CONVERSATION, KNOWLEDGE, INSTRUCTION, CONTEXT, RELATIONSHIP
}

enum class SoulPolicy {
    EPHEMERAL, SESSION, CONVERSATION, PROJECT, LONG_TERM, PERMANENT
}

data class SoulUnit(
    val id: String,
    val type: SoulType = SoulType.CONVERSATION,
    val content: String = "",
    val scope: String = "global",
    val importance: Double = 0.5,
    val confidence: Double = 0.8,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val sourceId: String = "",
    val conversationId: String = "",
    val tags: List<String> = emptyList(),
    val policy: SoulPolicy = SoulPolicy.LONG_TERM,
    val topic: String = "",
    val summary: String = content.take(500),
    val keyFacts: List<String> = emptyList(),
    val topicTags: List<String> = tags,
    val compressed: String = content,
    val embeddings: FloatArray? = null,
    val timestamp: Long = createdAt
) : Serializable

data class SoulMetadata(
    val generated: Long,
    val version: String = "1.0.2",
    val lastUpdated: Long,
    val storagePath: String,
    val providersUsed: List<String>,
    val totalConversations: Int,
    val totalMemoryUnits: Int,
    val compressedSize: Long
)
