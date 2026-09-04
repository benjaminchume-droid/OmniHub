package com.omnihub.history

import com.omnihub.providers.ChatMessage

class ChatRepository {
    private val messages = mutableMapOf<String, MutableList<ChatMessage>>()

    fun getRecentMessages(conversationId: String, limit: Int): List<ChatMessage> {
        val all = messages[conversationId] ?: return emptyList()
        return all.takeLast(limit)
    }

    fun addMessage(conversationId: String, role: String, content: String) {
        val list = messages.getOrPut(conversationId) { mutableListOf() }
        list.add(ChatMessage(role, content))
    }
}
