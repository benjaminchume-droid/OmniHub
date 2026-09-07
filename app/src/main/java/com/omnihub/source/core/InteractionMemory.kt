package com.omnihub.source.core

import android.content.Context

/**
 * Layout memory with success/failure confidence decay.
 */
object InteractionMemory {
    private const val PREFS = "omni_interaction_memory"

    fun inputSelector(context: Context, providerId: String): String? {
        val p = context.getSharedPreferences(PREFS, 0)
        val sel = p.getString("in_$providerId", null) ?: return null
        val ok = p.getInt("in_ok_$providerId", 0)
        val fail = p.getInt("in_fail_$providerId", 0)
        // decay: if more fails than successes recently, skip learned
        if (fail >= 3 && fail > ok) return null
        return sel
    }

    fun sendSelector(context: Context, providerId: String): String? {
        val p = context.getSharedPreferences(PREFS, 0)
        val sel = p.getString("send_$providerId", null) ?: return null
        val ok = p.getInt("send_ok_$providerId", 0)
        val fail = p.getInt("send_fail_$providerId", 0)
        if (fail >= 3 && fail > ok) return null
        return sel
    }

    fun remember(
        context: Context,
        providerId: String,
        inputSelector: String?,
        sendSelector: String?
    ) {
        context.getSharedPreferences(PREFS, 0).edit().apply {
            if (!inputSelector.isNullOrBlank()) {
                putString("in_$providerId", inputSelector)
                putInt("in_ok_$providerId", getInt("in_ok_$providerId", 0) + 1)
                putInt("in_fail_$providerId", 0)
            }
            if (!sendSelector.isNullOrBlank()) {
                putString("send_$providerId", sendSelector)
                putInt("send_ok_$providerId", getInt("send_ok_$providerId", 0) + 1)
                putInt("send_fail_$providerId", 0)
            }
            putLong("ts_$providerId", System.currentTimeMillis())
            apply()
        }
    }

    fun markFailure(context: Context, providerId: String, role: String) {
        val key = when (role) {
            "in" -> "in_fail_$providerId"
            "send" -> "send_fail_$providerId"
            else -> return
        }
        val p = context.getSharedPreferences(PREFS, 0)
        p.edit().putInt(key, p.getInt(key, 0) + 1).apply()
    }

    fun clear(context: Context, providerId: String) {
        context.getSharedPreferences(PREFS, 0).edit()
            .remove("in_$providerId")
            .remove("send_$providerId")
            .remove("ts_$providerId")
            .remove("in_ok_$providerId")
            .remove("in_fail_$providerId")
            .remove("send_ok_$providerId")
            .remove("send_fail_$providerId")
            .apply()
    }
}
