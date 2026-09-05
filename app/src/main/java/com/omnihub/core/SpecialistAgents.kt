package com.omnihub.core

/** Specialist role catalog — execution is orchestrated by AgentRuntime + Sources. */
object SpecialistAgents {
    val roles: List<AgentRole> = AgentRole.entries
    fun describe(role: AgentRole): String = when (role) {
        AgentRole.RESEARCHER -> "Gathers and cites information"
        AgentRole.CODER -> "Writes and revises code"
        AgentRole.WRITER -> "Produces structured prose"
        AgentRole.ANALYST -> "Extracts structure and insight"
        AgentRole.FILE_AGENT -> "Manages workspace files"
        AgentRole.ANDROID_AGENT -> "Android project operations"
        AgentRole.WEB_AGENT -> "Web research and extraction"
        AgentRole.EXECUTOR -> "General task execution"
        AgentRole.CRITIC -> "Reviews quality and risks"
        AgentRole.PLANNER -> "Decomposes goals into steps"
    }
}
