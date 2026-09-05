package com.omnihub.ui

import java.util.Calendar

/**
 * Greeting is UI-only. Never becomes a conversation message.
 */
object ContextualGreetingEngine {
    data class GreetingContext(
        val hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        val isFirstLaunch: Boolean = false,
        val isReturning: Boolean = true,
        val isNewConversation: Boolean = true
    )

    fun generate(ctx: GreetingContext = GreetingContext()): String {
        if (ctx.isFirstLaunch) return "Welcome to OmniHub."
        val band = when {
            ctx.hour < 5 -> "late"
            ctx.hour < 12 -> "morning"
            ctx.hour < 17 -> "afternoon"
            ctx.hour < 21 -> "evening"
            else -> "night"
        }
        val options = when (band) {
            "morning" -> listOf("Good morning.", "Ready to build.", "Morning — what are we shipping?")
            "afternoon" -> listOf("Good afternoon.", "Let's move.", "What should Omni handle?")
            "evening" -> listOf("Good evening.", "Evening session.", "One more win before night.")
            "late" -> listOf("Still up?", "Quiet hours. Loud ideas.", "Night lab.")
            else -> listOf("Ready when you are.", "What's next?", "Tell Omni what you need.")
        }
        return options[kotlin.math.abs(ctx.hour * 31 + band.hashCode()) % options.size]
    }
}
