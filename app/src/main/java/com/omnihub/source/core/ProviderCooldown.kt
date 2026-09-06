package com.omnihub.source.core

import android.content.Context

/** Hard cooldown when a provider signals unusual activity / block. */
object ProviderCooldown {
    private const val PREFS = "omni_provider_cooldown"
    private const val DEFAULT_MS = 15 * 60 * 1000L // 15 min

    fun markBlocked(context: Context, providerId: String, minutes: Int = 15) {
        val until = System.currentTimeMillis() + minutes * 60_000L
        context.getSharedPreferences(PREFS, 0).edit()
            .putLong("until_$providerId", until)
            .putString("reason_$providerId", "unusual_activity")
            .apply()
    }

    fun isCooling(context: Context, providerId: String): Boolean {
        val until = context.getSharedPreferences(PREFS, 0).getLong("until_$providerId", 0L)
        return until > System.currentTimeMillis()
    }

    fun remainingMs(context: Context, providerId: String): Long {
        val until = context.getSharedPreferences(PREFS, 0).getLong("until_$providerId", 0L)
        return (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun clear(context: Context, providerId: String) {
        context.getSharedPreferences(PREFS, 0).edit()
            .remove("until_$providerId")
            .remove("reason_$providerId")
            .apply()
    }

    fun message(context: Context, providerId: String, name: String): String {
        val mins = (remainingMs(context, providerId) / 60_000L).coerceAtLeast(1)
        return "$name is cooling down (~${mins}m). Try another provider or account."
    }
}
