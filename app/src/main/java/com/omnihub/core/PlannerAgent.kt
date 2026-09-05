package com.omnihub.core

/** Produces a lightweight step graph for AgentRuntime. */
class PlannerAgent {
    fun plan(goal: String): List<AgentStep> {
        val g = goal.lowercase()
        val steps = mutableListOf<AgentStep>()
        steps.add(AgentStep(AgentRole.PLANNER, "Break down the goal into actionable steps: $goal"))
        when {
            listOf("research", "report", "analyze company").any { it in g } -> {
                steps.add(AgentStep(AgentRole.RESEARCHER, "Research relevant facts for: $goal"))
                steps.add(AgentStep(AgentRole.ANALYST, "Analyze findings and extract key points"))
                steps.add(AgentStep(AgentRole.WRITER, "Write a concise structured report"))
            }
            listOf("code", "app", "android", "kotlin", "build").any { it in g } -> {
                steps.add(AgentStep(AgentRole.CODER, "Design and outline implementation for: $goal"))
                steps.add(AgentStep(AgentRole.CRITIC, "Review the plan for risks and missing pieces"))
                steps.add(AgentStep(AgentRole.CODER, "Produce concrete code or file structure"))
            }
            else -> {
                steps.add(AgentStep(AgentRole.EXECUTOR, "Execute the user goal: $goal"))
            }
        }
        return steps
    }
}
