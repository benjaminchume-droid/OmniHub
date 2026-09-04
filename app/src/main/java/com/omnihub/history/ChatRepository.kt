package com.omnihub.history

import android.content.Context
import androidx.room.Room
import com.omnihub.providers.ChatMessage
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ChatRepository(context: Context) {
    private val db = Room.databaseBuilder(
        context.applicationContext,
        ChatDatabase::class.java,
        "omnihub_chat.db"
    ).fallbackToDestructiveMigration().build()

    private val dao = db.chatDao()

    fun observeConversations(): Flow<List<ConversationEntity>> = dao.observeConversations()

    suspend fun getConversations(): List<ConversationEntity> = dao.getConversations()

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        dao.observeMessages(conversationId)

    suspend fun getMessages(conversationId: String): List<MessageEntity> =
        dao.getMessages(conversationId)

    suspend fun createConversation(title: String, temporary: Boolean = false): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.upsertConversation(
            ConversationEntity(
                id = id,
                title = title.take(80).ifBlank { "New chat" },
                createdAt = now,
                updatedAt = now,
                isTemporary = temporary
            )
        )
        return id
    }

    suspend fun addMessage(
        conversationId: String,
        role: String,
        content: String,
        model: String? = null,
        providerId: String? = null
    ) {
        val now = System.currentTimeMillis()
        dao.insertMessage(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                role = role,
                content = content,
                timestamp = now,
                model = model,
                providerId = providerId
            )
        )
        val existing = dao.getConversations().find { it.id == conversationId }
        if (existing != null) {
            val newTitle = if (role == "user" && existing.title == "New chat") {
                content.take(60)
            } else existing.title
            dao.upsertConversation(
                existing.copy(title = newTitle, updatedAt = now)
            )
        }
    }

    suspend fun deleteConversation(id: String) = dao.deleteConversation(id)

    suspend fun clearTemporary() = dao.clearTemporary()

    suspend fun getRecentAsChatMessages(conversationId: String, limit: Int = 20): List<ChatMessage> {
        return dao.getMessages(conversationId)
            .takeLast(limit)
            .map { ChatMessage(it.role, it.content) }
    }

    suspend fun deleteAll() {
        dao.deleteAllMessages()
        dao.deleteAllConversations()
    }

    suspend fun exportAsJson(): String {
        val convs = dao.getConversations()
        val sb = StringBuilder()
        sb.append("{\"exportedAt\":").append(System.currentTimeMillis()).append(",\"conversations\":[")
        convs.forEachIndexed { i, c ->
            if (i > 0) sb.append(",")
            val safeTitle = c.title.replace("\\", "\\\\").replace("\"", "\\\"")
            sb.append("{\"id\":\"").append(c.id).append("\",\"title\":\"").append(safeTitle).append("\",\"messages\":[")
            val msgs = dao.getMessages(c.id)
            msgs.forEachIndexed { j, m ->
                if (j > 0) sb.append(",")
                val safe = m.content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                sb.append("{\"role\":\"").append(m.role).append("\",\"content\":\"").append(safe).append("\"}")
            }
            sb.append("]}")
        }
        sb.append("]}")
        return sb.toString()
    }
}
