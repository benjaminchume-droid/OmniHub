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
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> = dao.observeMessages(conversationId)
    suspend fun getMessages(conversationId: String): List<MessageEntity> = dao.getMessages(conversationId)
    fun observeProjects(): Flow<List<ProjectEntity>> = dao.observeProjects()
    suspend fun getProjects(): List<ProjectEntity> = dao.getProjects()
    suspend fun getProject(id: String): ProjectEntity? = dao.getProject(id)

    suspend fun createConversation(
        title: String,
        temporary: Boolean = false,
        projectId: String? = null,
        reuseEmptyId: String? = null
    ): String {
        if (reuseEmptyId != null) {
            val existing = dao.getMessages(reuseEmptyId)
            if (existing.isEmpty()) return reuseEmptyId
        }
        if (temporary) dao.clearTemporary()
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.upsertConversation(
            ConversationEntity(
                id = id,
                title = title.take(80).ifBlank { "New chat" },
                createdAt = now,
                updatedAt = now,
                isTemporary = temporary,
                projectId = projectId
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
            ?: ConversationEntity(conversationId, "New chat", now, now)
        val newTitle = if (role == "user" && (existing.title == "New chat" || existing.title == "Temporary")) {
            content.take(60)
        } else existing.title
        dao.upsertConversation(existing.copy(title = newTitle, updatedAt = now))
    }

    suspend fun deleteConversation(id: String) = dao.deleteConversation(id)
    suspend fun clearTemporary() = dao.clearTemporary()
    suspend fun renameConversation(id: String, title: String) =
        dao.renameConversation(id, title.take(80), System.currentTimeMillis())
    suspend fun setPinned(id: String, pinned: Boolean) =
        dao.setPinned(id, pinned, System.currentTimeMillis())
    suspend fun setProject(id: String, projectId: String?) =
        dao.setProject(id, projectId, System.currentTimeMillis())

    suspend fun createProject(name: String): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.upsertProject(ProjectEntity(id, name.take(80).ifBlank { "Project" }, now, now))
        return id
    }

    suspend fun updateProjectMemory(id: String, memory: String) {
        val p = dao.getProject(id) ?: return
        dao.upsertProject(p.copy(sharedMemory = memory, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProject(id: String) = dao.deleteProject(id)

    suspend fun getRecentAsChatMessages(conversationId: String, limit: Int = 20): List<ChatMessage> {
        return dao.getMessages(conversationId).takeLast(limit).map { ChatMessage(it.role, it.content) }
    }

    suspend fun getContextMessages(conversationId: String, limit: Int = 20): List<ChatMessage> {
        val conv = dao.getConversations().find { it.id == conversationId }
        val out = mutableListOf<ChatMessage>()
        val projectId = conv?.projectId
        if (!projectId.isNullOrBlank()) {
            val proj = dao.getProject(projectId)
            if (proj != null && proj.sharedMemory.isNotBlank()) {
                out.add(ChatMessage("system", "Project memory (${proj.name}):\n${proj.sharedMemory}"))
            }
        }
        out.addAll(getRecentAsChatMessages(conversationId, limit))
        return out
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
            dao.getMessages(c.id).forEachIndexed { j, m ->
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
