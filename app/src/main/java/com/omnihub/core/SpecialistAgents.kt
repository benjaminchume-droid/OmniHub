package com.omnihub.core

import com.omnihub.source.SourceRouter
import com.omnihub.source.SourceCapability
import com.omnihub.workspace.OmniWorkspace

/**
 * Coder Agent - writes and modifies code.
 */
class CoderAgent(
    private val sourceRouter: SourceRouter,
    private val workspace: OmniWorkspace
) {
    suspend fun execute(instruction: String, context: ExecutionContext): StepResult {
        return try {
            val response = sourceRouter.execute(
                instruction = "Write code for: $instruction",
                capabilities = setOf(SourceCapability.CODE),
                preferences = com.omnihub.source.RoutingPreferences(
                    preferredSourceId = "claude" // Claude excels at coding
                )
            )
            
            StepResult(
                success = response.success,
                response = response.response,
                error = response.error,
                stepType = AgentType.CODER
            )
        } catch (e: Exception) {
            StepResult(
                success = false,
                response = null,
                error = e,
                stepType = AgentType.CODER
            )
        }
    }
}

/**
 * Researcher Agent - gathers information.
 */
class ResearcherAgent(
    private val sourceRouter: SourceRouter
) {
    suspend fun execute(instruction: String, context: ExecutionContext): StepResult {
        return try {
            val response = sourceRouter.execute(
                instruction = "Research and provide comprehensive information about: $instruction",
                capabilities = setOf(SourceCapability.RESEARCH, SourceCapability.WEB),
                preferences = com.omnihub.source.RoutingPreferences(
                    preferredSourceId = "perplexity" // Perplexity excels at research
                )
            )
            
            StepResult(
                success = response.success,
                response = response.response,
                error = response.error,
                stepType = AgentType.RESEARCHER
            )
        } catch (e: Exception) {
            StepResult(
                success = false,
                response = null,
                error = e,
                stepType = AgentType.RESEARCHER
            )
        }
    }
}

/**
 * Writer Agent - creates written content.
 */
class WriterAgent(
    private val sourceRouter: SourceRouter
) {
    suspend fun execute(instruction: String, context: ExecutionContext): StepResult {
        return try {
            val response = sourceRouter.execute(
                instruction = instruction,
                capabilities = setOf(SourceCapability.CHAT, SourceCapability.WRITING),
                preferences = com.omnihub.source.RoutingPreferences()
            )
            
            StepResult(
                success = response.success,
                response = response.response,
                error = response.error,
                stepType = AgentType.WRITER
            )
        } catch (e: Exception) {
            StepResult(
                success = false,
                response = null,
                error = e,
                stepType = AgentType.WRITER
            )
        }
    }
}

/**
 * Analyst Agent - analyzes and synthesizes information.
 */
class AnalystAgent(
    private val sourceRouter: SourceRouter
) {
    suspend fun execute(instruction: String, context: ExecutionContext): StepResult {
        return try {
            val response = sourceRouter.execute(
                instruction = "Analyze and provide insights on: $instruction. Context: ${context.previousStepResults}",
                capabilities = setOf(SourceCapability.ANALYSIS, SourceCapability.CHAT),
                preferences = com.omnihub.source.RoutingPreferences()
            )
            
            StepResult(
                success = response.success,
                response = response.response,
                error = response.error,
                stepType = AgentType.ANALYST
            )
        } catch (e: Exception) {
            StepResult(
                success = false,
                response = null,
                error = e,
                stepType = AgentType.ANALYST
            )
        }
    }
}

/**
 * File Agent - manages files and workspace.
 */
class FileAgent(
    private val sourceRouter: SourceRouter,
    private val workspace: OmniWorkspace
) {
    suspend fun execute(instruction: String, context: ExecutionContext): StepResult {
        return try {
            // Check if instruction involves file operations
            if (instruction.lowercase().containsAny("create", "write", "save", "delete", "read", "list")) {
                // Execute through workspace
                val result = workspace.executeFileOperation(instruction)
                
                StepResult(
                    success = result.success,
                    response = result.output,
                    error = result.error,
                    stepType = AgentType.FILE
                )
            } else {
                // Fall back to chat-based file discussion
                val response = sourceRouter.execute(
                    instruction = "Discuss file operation: $instruction",
                    capabilities = setOf(SourceCapability.CHAT),
                    preferences = com.omnihub.source.RoutingPreferences()
                )
                
                StepResult(
                    success = response.success,
                    response = response.response,
                    error = response.error,
                    stepType = AgentType.FILE
                )
            }
        } catch (e: Exception) {
            StepResult(
                success = false,
                response = null,
                error = e,
                stepType = AgentType.FILE
            )
        }
    }
}

/**
 * Android Agent - builds and manages Android projects.
 */
class AndroidAgent(
    private val sourceRouter: SourceRouter,
    private val workspace: OmniWorkspace
) {
    suspend fun execute(instruction: String, context: ExecutionContext): StepResult {
        return try {
            // Android build requires special handling
            val result = workspace.executeAndroidBuild(instruction)
            
            StepResult(
                success = result.success,
                response = result.output,
                error = result.error,
                stepType = AgentType.ANDROID
            )
        } catch (e: Exception) {
            StepResult(
                success = false,
                response = null,
                error = e,
                stepType = AgentType.ANDROID
            )
        }
    }
}

// Helper extension
private fun String.containsAny(vararg substrings: String): Boolean {
    val lower = this.lowercase()
    return substrings.any { lower.contains(it) }
}