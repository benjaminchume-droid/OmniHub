package com.omnihub.core

import android.content.Context
import android.util.Log
import com.omnihub.source.SourceRouter
import com.omnihub.source.ExecutionContext
import com.omnihub.source.SourceCapability
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Omni Agent Runtime - orchestrates planning, specialist agents, and execution.
 * 
 * Components:
 * - Planner: Breaks down complex tasks into subtasks
 * - Specialist Agents: Coder, Researcher, Writer, Analyst, FileAgent, AndroidAgent, etc.
 * - Execution Policy: Controls autonomous vs. human-in-the-loop execution
 * - Permission Engine: Enforces boundaries around destructive operations
 */
class AgentRuntime(
    private val context: Context,
    private val sourceRouter: SourceRouter,
    private val workspace: OmniWorkspace
) {
    private val runtimeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val executionMutex = Mutex()
    
    // Active tasks
    private val activeTasks = mutableMapOf<String, OmniTask>()
    
    // Specialist agents
    private val agents = mapOf(
        AgentType.PLANNER to PlannerAgent(sourceRouter),
        AgentType.CODER to CoderAgent(sourceRouter, workspace),
        AgentType.RESEARCHER to ResearcherAgent(sourceRouter),
        AgentType.WRITER to WriterAgent(sourceRouter),
        AgentType.ANALYST to AnalystAgent(sourceRouter),
        AgentType.FILE to FileAgent(sourceRouter, workspace),
        AgentType.ANDROID to AndroidAgent(sourceRouter, workspace)
    )
    
    /**
     * Execute a high-level instruction through the agent system.
     */
    suspend fun execute(
        instruction: String,
        policy: ExecutionPolicy = ExecutionPolicy.INTERACTIVE
    ): AgentResult {
        return executionMutex.withLock {
            try {
                val taskId = UUID.randomUUID().toString()
                
                Log.d("AgentRuntime", "Executing task $taskId: $instruction")
                
                // Step 1: Plan
                val planner = agents[AgentType.PLANNER] as PlannerAgent
                val plan = planner.plan(instruction)
                
                Log.d("AgentRuntime", "Plan created with ${plan.steps.size} steps")
                
                // Step 2: Execute plan steps
                val results = mutableListOf<StepResult>()
                var overallSuccess = true
                
                for ((index, step) in plan.steps.withIndex()) {
                    // Check if step requires permission
                    if (step.requiresPermission && policy == ExecutionPolicy.INTERACTIVE) {
                        // Request user confirmation
                        val confirmed = requestPermission(step)
                        if (!confirmed) {
                            return@withLock AgentResult(
                                success = false,
                                error = PermissionDeniedException("User denied permission for step: ${step.description}"),
                                taskId = taskId,
                                results = results
                            )
                        }
                    }
                    
                    // Select appropriate agent
                    val agent = agents[step.agentType] 
                        ?: throw AgentNotFoundException("Agent not found: ${step.agentType}")
                    
                    // Execute step
                    val stepResult = agent.execute(step.instruction, plan.context)
                    results.add(stepResult)
                    
                    if (!stepResult.success) {
                        overallSuccess = false
                        
                        // Check if we should continue or abort
                        if (policy.failFast) {
                            return@withLock AgentResult(
                                success = false,
                                error = stepResult.error,
                                taskId = taskId,
                                results = results,
                                completedSteps = index
                            )
                        }
                    }
                    
                    // Update context with step result
                    plan.context = plan.context.copy(
                        previousStepResults = plan.context.previousStepResults + stepResult
                    )
                }
                
                // Step 3: Aggregate results
                val finalResult = AgentResult(
                    success = overallSuccess,
                    response = aggregateResults(results),
                    taskId = taskId,
                    results = results,
                    completedSteps = results.count { it.success }
                )
                
                Log.d("AgentRuntime", "Task $taskId completed: ${if (overallSuccess) "success" else "failed"}")
                
                finalResult
            } catch (e: Exception) {
                Log.e("AgentRuntime", "Task execution failed", e)
                AgentResult(
                    success = false,
                    error = e,
                    taskId = null,
                    results = emptyList()
                )
            }
        }
    }
    
    /**
     * Schedule a recurring task.
     */
    fun schedule(
        instruction: String,
        schedule: TaskSchedule,
        policy: ExecutionPolicy = ExecutionPolicy.INTERACTIVE
    ): String {
        val taskId = UUID.randomUUID().toString()
        
        val task = OmniTask(
            id = taskId,
            instruction = instruction,
            schedule = schedule,
            policy = policy,
            createdAt = System.currentTimeMillis()
        )
        
        activeTasks[taskId] = task
        
        // Schedule execution
        scheduleTask(task)
        
        Log.i("AgentRuntime", "Scheduled task $taskId: $instruction")
        
        return taskId
    }
    
    /**
     * Cancel a scheduled task.
     */
    fun cancelTask(taskId: String): Boolean {
        val task = activeTasks.remove(taskId)
        return task != null
    }
    
    /**
     * Get all active tasks.
     */
    fun getActiveTasks(): List<OmniTask> {
        return activeTasks.values.toList()
    }
    
    /**
     * Request permission for a step.
     */
    private suspend fun requestPermission(step: PlanStep): Boolean {
        // TODO: Implement actual permission request UI
        // For now, return true
        return true
    }
    
    /**
     * Schedule task for future execution.
     */
    private fun scheduleTask(task: OmniTask) {
        runtimeScope.launch {
            while (true) {
                delay(task.schedule.nextExecutionDelay())
                
                if (activeTasks.containsKey(task.id)) {
                    execute(task.instruction, task.policy)
                } else {
                    break
                }
            }
        }
    }
    
    /**
     * Aggregate results from multiple steps.
     */
    private fun aggregateResults(results: List<StepResult>): String {
        return results.joinToString("\n\n") { result ->
            "${result.stepType}: ${result.response ?: "(no output)"}"
        }
    }
    
    /**
     * Cleanup runtime resources.
     */
    fun cleanup() {
        runtimeScope.cancel()
        activeTasks.clear()
    }
}

/**
 * Agent execution result.
 */
data class AgentResult(
    val success: Boolean,
    val response: String? = null,
    val error: Exception? = null,
    val taskId: String?,
    val results: List<StepResult>,
    val completedSteps: Int = 0
)

/**
 * Individual step result.
 */
data class StepResult(
    val success: Boolean,
    val response: String?,
    val error: Exception?,
    val stepType: AgentType
)

/**
 * Execution policy.
 */
enum class ExecutionPolicy {
    INTERACTIVE,    // Require confirmation for destructive ops
    AUTONOMOUS,     // Execute without confirmation
    FAIL_FAST       // Stop on first error
}

/**
 * Omni Task definition.
 */
data class OmniTask(
    val id: String,
    val instruction: String,
    val schedule: TaskSchedule,
    val policy: ExecutionPolicy,
    val createdAt: Long,
    val lastRun: Long? = null,
    val nextRun: Long? = null,
    val executionHistory: List<TaskExecution> = emptyList()
)

/**
 * Task schedule.
 */
data class TaskSchedule(
    val frequency: ScheduleFrequency,
    val time: String? = null,  // HH:mm format for daily/weekly
    val dayOfWeek: Int? = null // 1-7 for weekly (1=Monday)
) {
    fun nextExecutionDelay(): Long {
        return when (frequency) {
            ScheduleFrequency.HOURLY -> 3600000L
            ScheduleFrequency.DAILY -> 86400000L
            ScheduleFrequency.WEEKLY -> 604800000L
            ScheduleFrequency.MONTHLY -> 2592000000L
        }
    }
}

enum class ScheduleFrequency {
    HOURLY, DAILY, WEEKLY, MONTHLY
}

/**
 * Task execution record.
 */
data class TaskExecution(
    val timestamp: Long,
    val success: Boolean,
    val result: String?
)

/**
 * Plan step.
 */
data class PlanStep(
    val instruction: String,
    val agentType: AgentType,
    val description: String,
    val requiresPermission: Boolean = false
)

/**
 * Execution plan.
 */
data class ExecutionPlan(
    val steps: List<PlanStep>,
    var context: ExecutionContext
)

/**
 * Agent types.
 */
enum class AgentType {
    PLANNER, CODER, RESEARCHER, WRITER, ANALYST, FILE, ANDROID
}

/**
 * Agent not found exception.
 */
class AgentNotFoundException(message: String) : Exception(message)

/**
 * Permission denied exception.
 */
class PermissionDeniedException(message: String) : Exception(message)