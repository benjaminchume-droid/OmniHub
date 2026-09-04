package com.omnihub.core

import com.omnihub.history.ChatRepository
import com.omnihub.providers.ChatMessage
import com.omnihub.providers.ChatRequest
import com.omnihub.providers.ChatResponse
import com.omnihub.soul.SoulManager

class RequestPipeline(
    private val router: OmniRouter,
    private val soulManager: SoulManager,
    private val history: ChatRepository
) {
    suspend fun process(
        conversationId: String,
        userPrompt: String,
        preferredModel: String? = null
    ): ChatResponse {
        val soul = soulManager.read()
        val recent = history.getRecentMessages(conversationId, limit = 12)
        val messages = mutableListOf<ChatMessage>()
        if (soul.isNotBlank()) {
            messages.add(ChatMessage("system", "User memory:\n$soul"))
        }
        messages.addAll(recent)
        messages.add(ChatMessage("user", userPrompt))

        val request = ChatRequest(
            model = preferredModel ?: "",
            messages = messages
        )
        val response = router.chatWithFallback(request)
        history.addMessage(conversationId, "user", userPrompt)
        history.addMessage(conversationId, "assistant", response.content)
        soulManager.append("Q: ${userPrompt.take(200)}\nA: ${response.content.take(400)}")
        return response
    }
}
