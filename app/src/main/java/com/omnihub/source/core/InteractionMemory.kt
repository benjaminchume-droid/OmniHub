package com.omnihub.source.core

import android.content.Context

/**
 * Per-provider learned interaction fingerprints.
 * On success we store input/send CSS-ish selectors; next time we try those first.
 */
object InteractionMemory {
    private const val PREFS = "omni_interaction_memory"

    fun inputSelector(context: Context, providerId: String): String? =
        context.getSharedPreferences(PREFS, 0).getString("in_$providerId", null)

    fun sendSelector(context: Context, providerId: String): String? =
        context.getSharedPreferences(PREFS, 0).getString("send_$providerId", null)

    fun remember(
        context: Context,
        providerId: String,
        inputSelector: String?,
        sendSelector: String?
    ) {
        context.getSharedPreferences(PREFS, 0).edit().apply {
            if (!inputSelector.isNullOrBlank()) putString("in_$providerId", inputSelector)
            if (!sendSelector.isNullOrBlank()) putString("send_$providerId", sendSelector)
            putLong("ts_$providerId", System.currentTimeMillis())
            apply()
        }
    }

    fun clear(context: Context, providerId: String) {
        context.getSharedPreferences(PREFS, 0).edit()
            .remove("in_$providerId")
            .remove("send_$providerId")
            .remove("ts_$providerId")
            .apply()
    }
}
