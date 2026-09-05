package com.omnihub.ui

import android.content.Context
import com.omnihub.soul.SoulManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * Contextual Greeting Engine - generates intelligent greetings based on time, context, and history.
 * 
 * Features:
 * - Time-aware (morning/afternoon/evening/night)
 * - Locale and timezone aware
 * - Activity-based (first use, returning, continuing)
 * - Soul context integration
 * - Non-repetitive
 */
class ContextualGreetingEngine(
    private val context: Context,
    private val soulManager: SoulManager
) {
    private val _currentGreeting = MutableStateFlow<GreetingState?>(null)
    val currentGreeting: StateFlow<GreetingState?> = _currentGreeting
    
    private val usedGreetings = mutableSetOf<String>()
    private val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    /**
     * Generate a contextual greeting for new conversation.
     */
    suspend fun generateGreeting(): GreetingState {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        
        // Time-based greeting
        val timeGreeting = when (hour) {
            in 0..4 -> "Late night session?"
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Ready to work?"
        }
        
        // Context-based additions
        val contextAdditions = mutableListOf<String>()
        
        // Check Soul for active projects
        val activeProject = soulManager.getActiveProject()
        if (activeProject != null) {
            contextAdditions.add("Continuing ${activeProject.name}?")
        }
        
        // Check for pending tasks
        val pendingTasks = soulManager.getPendingTasks()
        if (pendingTasks.isNotEmpty()) {
            contextAdditions.add("You have ${pendingTasks.size} pending task(s)")
        }
        
        // Day/date
        val dateString = dateFormat.format(Date())
        val timeString = timeFormat.format(Date())
        
        // Build greeting
        val greeting = buildString {
            append(timeGreeting)
            append(". ")
            
            if (contextAdditions.isNotEmpty()) {
                append(contextAdditions.joinToString(" "))
                append(" ")
            }
            
            append("$dateString, $timeString")
        }
        
        // Ensure uniqueness
        val uniqueGreeting = ensureUniqueGreeting(greeting)
        
        val state = GreetingState(
            text = uniqueGreeting,
            timestamp = System.currentTimeMillis(),
            isContextual = contextAdditions.isNotEmpty()
        )
        
        _currentGreeting.value = state
        
        return state
    }
    
    /**
     * Dismiss current greeting.
     */
    fun dismissGreeting() {
        _currentGreeting.value = null
    }
    
    /**
     * Ensure greeting is unique (not recently used).
     */
    private fun ensureUniqueGreeting(baseGreeting: String): String {
        if (!usedGreetings.contains(baseGreeting)) {
            usedGreetings.add(baseGreeting)
            
            // Keep only last 20 greetings
            if (usedGreetings.size > 20) {
                usedGreetings.toList().subList(0, usedGreetings.size - 20).forEach {
                    usedGreetings.remove(it)
                }
            }
            
            return baseGreeting
        }
        
        // Generate variant
        val variants = listOf(
            baseGreeting + " Let's make it count.",
            baseGreeting + " What's the plan?",
            baseGreeting + " Ready to build?",
            "New conversation. $baseGreeting"
        )
        
        return variants.firstOrNull { !usedGreetings.contains(it) } ?: baseGreeting
    }
    
    /**
     * Clear greeting history.
     */
    fun clearHistory() {
        usedGreetings.clear()
    }
}

/**
 * Greeting state.
 */
data class GreetingState(
    val text: String,
    val timestamp: Long,
    val isContextual: Boolean
)