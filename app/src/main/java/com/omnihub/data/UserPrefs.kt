package com.omnihub.data

import android.content.Context

/**
 * Local user profile & first-run state.
 * Never hardcodes personal data — collected once at setup.
 */
object UserPrefs {
    private const val PREFS = "omnihub_user"

    fun isSetupComplete(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("setup_complete", false)

    fun markSetupComplete(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("setup_complete", true).apply()
    }

    fun saveProfile(context: Context, name: String, age: Int?, bio: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("name", name.trim())
            .putInt("age", age ?: -1)
            .putString("bio", bio?.trim() ?: "")
            .putBoolean("setup_complete", true)
            .apply()
    }

    fun getName(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("name", "") ?: ""

    fun getAge(context: Context): Int {
        val a = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("age", -1)
        return if (a > 0) a else 0
    }

    fun getBio(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("bio", "") ?: ""

    fun getInitials(context: Context): String {
        val name = getName(context)
        if (name.isBlank()) return "?"
        val parts = name.trim().split(Regex("\\s+"))
        return when {
            parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
            else -> name.take(1).uppercase()
        }
    }
}
