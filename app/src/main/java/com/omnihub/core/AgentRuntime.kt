package com.omnihub.core

import com.omnihub.policy.OmniCapability
import com.omnihub.policy.PermissionEngine
import com.omnihub.providers.ChatMessage
import com.omnihub.source.SourceRouter

enum class AgentRole {
    RESEARCHER, CODER, WRITER, ANALYST, FILE_AGENT,
    ANDROID_AGENT, WEB_AGENT, EXECUTOR, CRITIC, PLANNER
}

data class AgentStep(
    val role: AgentRole,
    val instruction: String,
    val result: String = "",
    val status: String = "pending"
)

data class AgentRun(
    val id: String,
    val goal: String,
    val steps: List<AgentStep>,
    val finalResult: String = ""
)

/** Foundation agent runtime: plan → act → observe (no fake long-chat agents). */
class AgentRuntime(
    private val planner: PlannerAgent,
    private val router: SourceRouter
) {
    suspend fun run(goal: String, conversationId: String? = null): AgentRun {
        val plan = planner.plan(goal)
        val completed = mutableListOf<AgentStep>()
        for (step in plan) {
            val decision = PermissionEngine.evaluate(OmniCapability.HTTP_REQUEST)
            if (!decision.allowed && decision.requiresUserApproval) {
                completed.add(step.copy(status = "blocked", result = decision.reason))
                continue
            }
            val prompt = buildString {
                append("You are the ${step.role} agent for OmniHub.\n")
                append("Goal: $goal\n")
                append("Your step: ${step.instruction}\n")
                append("Respond with the concrete output for this step only.")
            }
            try {
                val routed = router.chat(
                    messages = listOf(ChatMessage("user", prompt)),
                    conversationId = conversationId,
                    taskHints = SourceRouter.TaskHints.fromPrompt(step.instruction)
                )
                completed.add(step.copy(status = "done", result = routed.response.content))
            } catch (e: Exception) {
                completed.add(step.copy(status = "failed", result = e.message ?: "failed"))
            }
        }
        return AgentRun(
            id = java.util.UUID.randomUUID().toString(),
            goal = goal,
            steps = completed,
            finalResult = completed.lastOrNull { it.status == "done" }?.result.orEmpty()
        )
    }
}
