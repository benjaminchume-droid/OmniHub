package com.omnihub.soul

import android.content.Context
import android.util.Log
import com.omnihub.providers.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class SoulManager(context: Context) {
    private val TAG = "SoulManager"
    private val root = File(context.filesDir, "Omni/memory").also { it.mkdirs() }
    private val soulFile = File(root, "soul.md")
    private val unitsFile = File(root, "units.json")
    private val soulUnits = mutableListOf<SoulUnit>()

    init { load() }

    fun setStoragePath(path: String) {
        Log.d(TAG, "Custom storage path requested: $path")
    }

    suspend fun learnFromExchange(
        sourceId: String,
        messages: List<ChatMessage>,
        assistantReply: String,
        conversationId: String?
    ) = withContext(Dispatchers.Default) {
        val convId = conversationId ?: UUID.randomUUID().toString()
        val userText = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        val topic = extractTopic(userText.ifBlank { assistantReply })
        val unit = SoulUnit(
            id = "soul-${UUID.randomUUID()}",
            type = SoulType.CONVERSATION,
            content = "User: ${userText.take(300)}\nAssistant: ${assistantReply.take(400)}",
            sourceId = sourceId,
            conversationId = convId,
            topic = topic,
            summary = assistantReply.take(400),
            keyFacts = extractFacts(userText + "\n" + assistantReply),
            tags = extractTags(userText + " " + assistantReply),
            importance = 0.6,
            policy = SoulPolicy.LONG_TERM
        )
        synchronized(soulUnits) {
            soulUnits.add(unit)
            if (soulUnits.size > 200) {
                soulUnits.sortByDescending { it.importance * it.createdAt }
                while (soulUnits.size > 150) soulUnits.removeAt(soulUnits.lastIndex)
            }
            persist()
        }
    }

    suspend fun learnFromConversation(
        sourceId: String,
        request: com.omnihub.providers.ChatRequest,
        response: com.omnihub.providers.ChatResponse
    ) {
        learnFromExchange(sourceId, request.messages, response.content, null)
    }

    suspend fun generatePromptContext(maxUnits: Int = 5): String = withContext(Dispatchers.Default) {
        synchronized(soulUnits) {
            val recent = soulUnits.sortedByDescending { it.importance * it.createdAt }.take(maxUnits)
            if (recent.isEmpty()) return@withContext ""
            buildString {
                append("## Omni memory (relevant units only)\n")
                recent.forEach { u ->
                    append("- [${u.type}] ${u.topic.ifBlank { u.summary.take(40) }}: ${u.summary.take(180)}\n")
                }
            }
        }
    }

    fun unitCount(): Int = synchronized(soulUnits) { soulUnits.size }

    private fun load() {
        try {
            if (unitsFile.exists()) {
                val arr = JSONArray(unitsFile.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    soulUnits.add(
                        SoulUnit(
                            id = o.optString("id"),
                            type = runCatching { SoulType.valueOf(o.optString("type", "CONVERSATION")) }.getOrDefault(SoulType.CONVERSATION),
                            content = o.optString("content"),
                            sourceId = o.optString("sourceId"),
                            conversationId = o.optString("conversationId"),
                            topic = o.optString("topic"),
                            summary = o.optString("summary"),
                            importance = o.optDouble("importance", 0.5),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            tags = o.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "load failed: ${e.message}")
        }
    }

    private fun persist() {
        try {
            val arr = JSONArray()
            soulUnits.forEach { u ->
                arr.put(
                    JSONObject()
                        .put("id", u.id)
                        .put("type", u.type.name)
                        .put("content", u.content)
                        .put("sourceId", u.sourceId)
                        .put("conversationId", u.conversationId)
                        .put("topic", u.topic)
                        .put("summary", u.summary)
                        .put("importance", u.importance)
                        .put("createdAt", u.createdAt)
                        .put("tags", JSONArray(u.tags))
                )
            }
            unitsFile.writeText(arr.toString())
            val md = buildString {
                append("# Omni Soul\n\n")
                append("_Auto-generated memory projection. Runtime uses indexed units._\n\n")
                soulUnits.sortedByDescending { it.createdAt }.take(40).forEach { u ->
                    append("## ${u.topic.ifBlank { u.type.name }}\n")
                    append("- source: ${u.sourceId}\n")
                    append("- ${u.summary}\n\n")
                }
            }
            soulFile.writeText(md)
        } catch (e: Exception) {
            Log.w(TAG, "persist failed: ${e.message}")
        }
    }

    private fun extractTopic(text: String): String {
        val t = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return t.take(60).ifBlank { "Conversation" }
    }

    private fun extractFacts(text: String): List<String> =
        text.lines().map { it.trim() }.filter { it.length in 20..160 }.take(5)

    private fun extractTags(text: String): List<String> {
        val keywords = listOf("android", "kotlin", "omnihub", "code", "design", "api", "source", "memory")
        val lower = text.lowercase()
        return keywords.filter { it in lower }.take(6)
    }
}
