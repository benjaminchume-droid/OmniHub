package com.omnihub.core

import com.omnihub.source.SourceRouter
import com.omnihub.source.SourceCapability
import com.omnihub.source.ExecutionContext

/**
 * Planner Agent - breaks down complex tasks into executable steps.
 */
class PlannerAgent(
    private val sourceRouter: SourceRouter
) {
    /**
     * Create an execution plan for the given instruction.
     */
    suspend fun plan(instruction: String): ExecutionPlan {
        val steps = analyzeAndPlan(instruction)
        
        return ExecutionPlan(
            steps = steps,
            context = ExecutionContext(
                instruction = instruction,
                timestamp = System.currentTimeMillis(),
                userId = null,
                sessionId = null,
                locale = java.util.Locale.getDefault(),
                timezone = java.util.TimeZone.getDefault().id,
                previousStepResults = emptyList()
            )
        )
    }
    
    /**
     * Analyze instruction and create plan steps.
     */
    private suspend fun analyzeAndPlan(instruction: String): List<PlanStep> {
        val lowerInstruction = instruction.lowercase()
        
        return when {
            // Coding tasks
            lowerInstruction.contains("code") || 
            lowerInstruction.contains("program") || 
            lowerInstruction.contains("function") ||
            lowerInstruction.contains("class") ||
            lowerInstruction.contains("app") -> {
                listOf(
                    PlanStep(
                        instruction = "Analyze requirements and design solution for: $instruction",
                        agentType = AgentType.CODER,
                        description = "Design and implement code solution",
                        requiresPermission = false
                    )
                )
            }
            
            // Research tasks
            lowerInstruction.contains("research") || 
            lowerInstruction.contains("search") ||
            lowerInstruction.contains("find information") -> {
                listOf(
                    PlanStep(
                        instruction = "Research and gather information about: $instruction",
                        agentType = AgentType.RESEARCHER,
                        description = "Conduct research and compile findings",
                        requiresPermission = false
                    )
                )
            }
            
            // Writing tasks
            lowerInstruction.contains("write") || 
            lowerInstruction.contains("draft") ||
            lowerInstruction.contains("compose") ||
            lowerInstruction.contains("create document") -> {
                listOf(
                    PlanStep(
                        instruction = "Write content for: $instruction",
                        agentType = AgentType.WRITER,
                        description = "Create written content",
                        requiresPermission = false
                    )
                )
            }
            
            // File operations
            lowerInstruction.contains("file") || 
            lowerInstruction.contains("document") ||
            lowerInstruction.contains("save") ||
            lowerInstruction.contains("read") -> {
                listOf(
                    PlanStep(
                        instruction = "Perform file operation: $instruction",
                        agentType = AgentType.FILE,
                        description = "Execute file operation",
                        requiresPermission = lowerInstruction.contains("delete") || lowerInstruction.contains("overwrite")
                    )
                )
            }
            
            // Android-specific tasks
            lowerInstruction.contains("android") || 
            lowerInstruction.contains("apk") ||
            lowerInstruction.contains("build") -> {
                listOf(
                    PlanStep(
                        instruction = "Build Android project: $instruction",
                        agentType = AgentType.ANDROID,
                        description = "Build and compile Android application",
                        requiresPermission = true
                    )
                )
            }
            
            // Complex multi-step tasks
            lowerInstruction.contains("analyze") || 
            lowerInstruction.contains("compare") ||
            lowerInstruction.contains("evaluate") -> {
                listOf(
                    PlanStep(
                        instruction = "Research and gather data for: $instruction",
                        agentType = AgentType.RESEARCHER,
                        description = "Gather research data",
                        requiresPermission = false
                    ),
                    PlanStep(
                        instruction = "Analyze findings and provide insights: $instruction",
                        agentType = AgentType.ANALYST,
                        description = "Analyze and synthesize information",
                        requiresPermission = false
                    )
                )
            }
            
            // Default: simple chat/response
            else -> {
                listOf(
                    PlanStep(
                        instruction = instruction,
                        agentType = AgentType.WRITER,
                        description = "Generate response",
                        requiresPermission = false
                    )
                )
            }
        }
    }
}

/**
 * Extended execution context with step results.
 */
data class ExecutionContext(
    val instruction: String,
    val timestamp: Long,
    val userId: String?,
    val sessionId: String?,
    val locale: java.util.Locale,
    val timezone: String,
    val previousStepResults: List<StepResult> = emptyList()
)