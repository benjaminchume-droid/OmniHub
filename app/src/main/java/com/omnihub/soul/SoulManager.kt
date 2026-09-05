package com.omnihub.soul

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.omnihub.providers.ChatRequest
import com.omnihub.providers.ChatResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

/**
 * Manages persistent memory (soul) system.
 * Compresses conversations into reusable facts across all providers.
 */
class SoulManager(private val context: Context) {
    private val gson = Gson()
    private val soulUnits = mutableListOf<SoulUnit>()
    private var storagePath: String = "/storage/OmniHub/soul"  // User-configurable
    private val soulFile: File
        get() = File(storagePath, "memory.md")

    init {
        // Create storage directory if needed
        File(storagePath).mkdirs()
        loadSoulFromDisk()
    }

    /**
     * Set user-configured storage path (from app settings)
     */
    fun setStoragePath(path: String) {
        storagePath = path
        File(storagePath).mkdirs()
        Log.d(TAG, "Soul storage path set to: $path")
    }

    /**
     * Extract soul units from a conversation
     */
    suspend fun learnFromConversation(
        sourceId: String,
        request: ChatRequest,
        response: ChatResponse
    ) = withContext(Dispatchers.Default) {
        Log.d(TAG, "Learning from conversation with $sourceId")
        val conversationId = request.conversationId ?: UUID.randomUUID().toString()
        
        // Simple extraction: merge assistant response into a single unit
        // TODO: Phase 2 - Add NLP for entity extraction & fact discovery
        val newUnit = SoulUnit(
            id = "soul-${UUID.randomUUID()}",
            timestamp = System.currentTimeMillis(),
            sourceId = sourceId,
            conversationId = conversationId,
            topic = extractTopic(request.messages),
            summary = response.message.take(500),  // First 500 chars
            keyFacts = extractFacts(request.messages + response.message),
            topicTags = extractTags(request.messages + response.message),
            compressed = compress(response.message),
            importance = 1.0
        )
        
        synchronized(soulUnits) {
            soulUnits.add(newUnit)
            deduplicateUnits()
            persistSoulToDisk()
        }
    }

    /**
     * Generate prompt context from recent soul units
     */
    suspend fun generatePromptContext(maxUnits: Int = 5): String = withContext(Dispatchers.Default) {
        synchronized(soulUnits) {
            val recent = soulUnits.sortedByDescending { it.timestamp }.take(maxUnits)
            if (recent.isEmpty()) return@withContext ""
            
            val context = StringBuilder()
            context.append("## User Context (from memory):\n")
            recent.forEach { unit ->
                context.append("- **${unit.topic}**: ${unit.summary}\n")
                context.append("  Tags: ${unit.topicTags.joinToString(" ")}\n\n")
            }
            context.toString()
        }
    }

    /**
     * Load soul from memory.md file
     */
    private suspend fun loadSoulFromDisk() = withContext(Dispatchers.IO) {
        if (!soulFile.exists()) {
            Log.d(TAG, "No existing soul found")
            return@withContext
        }
        
        try {
            val content = soulFile.readText()
            // Parse markdown and extract units
            val units = parseMarkdownUnits(content)
            synchronized(soulUnits) {
                soulUnits.clear()
                soulUnits.addAll(units)
            }
            Log.d(TAG, "Loaded ${units.size} soul units from disk")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load soul", e)
        }
    }

    /**
     * Persist soul to memory.md
     */
    private suspend fun persistSoulToDisk() = withContext(Dispatchers.IO) {
        try {
            val markdown = generateMarkdown()
            soulFile.writeText(markdown)
            Log.d(TAG, "Persisted soul to ${soulFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist soul", e)
        }
    }

    /**
     * Remove duplicate/redundant units
     */
    private fun deduplicateUnits() {
        // Simple similarity check: if summaries are similar, keep only newer one
        val toRemove = mutableSetOf<String>()
        for (i in soulUnits.indices) {
            for (j in i + 1 until soulUnits.size) {
                if (areSimilar(soulUnits[i], soulUnits[j])) {
                    // Keep newer one
                    if (soulUnits[i].timestamp < soulUnits[j].timestamp) {
                        toRemove.add(soulUnits[i].id)
                    } else {
                        toRemove.add(soulUnits[j].id)
                    }
                }
            }
        }
        soulUnits.removeAll { it.id in toRemove }
    }

    private fun areSimilar(a: SoulUnit, b: SoulUnit): Boolean {
        // Simple heuristic: same topic tags = similar
        val commonTags = a.topicTags.intersect(b.topicTags.toSet())
        return commonTags.size >= 2
    }

    private fun generateMarkdown(): String {
        val md = StringBuilder()
        md.append("# OmniHub Soul\n")
        md.append("**Generated**: ${Date()}\n")
        md.append("**Version**: 1.0\n\n")
        
        synchronized(soulUnits) {
            soulUnits.sortedByDescending { it.timestamp }.forEach { unit ->
                md.append("## Memory: ${unit.topic}\n")
                md.append("**Date**: ${Date(unit.timestamp)}\n")
                md.append("**Provider**: ${unit.sourceId}\n")
                md.append("**Tags**: ${unit.topicTags.joinToString(", ")}\n\n")
                md.append("${unit.summary}\n\n")
            }
        }
        
        return md.toString()
    }

    private fun parseMarkdownUnits(content: String): List<SoulUnit> {
        // TODO: Implement markdown parser
        return emptyList()
    }

    private fun extractTopic(messages: List<Any>): String {
        // Simple: use first user message
        return messages.firstOrNull()?.toString()?.take(50) ?: "Unknown"
    }

    private fun extractFacts(content: String): List<String> {
        // TODO: Phase 2 - NLP-based fact extraction
        return content.split(".")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(3)
    }

    private fun extractTags(content: String): List<String> {
        // TODO: Phase 2 - Semantic tagging
        return listOf("#general")
    }

    private fun compress(text: String): String {
        // TODO: Phase 2 - LZ4 compression
        return text
    }

    companion object {
        private const val TAG = "SoulManager"
    }
}
