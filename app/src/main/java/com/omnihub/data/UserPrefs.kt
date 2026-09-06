package com.omnihub.data

import android.content.Context

object UserPrefs {
    private fun p(context: Context) =
        context.getSharedPreferences("omnihub_prefs", Context.MODE_PRIVATE)

    fun isSetupComplete(context: Context): Boolean =
        p(context).getBoolean("setup_complete", false)

    fun markSetupComplete(context: Context) {
        p(context).edit().putBoolean("setup_complete", true).apply()
    }

    fun acceptLegal(context: Context) {
        p(context).edit()
            .putBoolean("legal_accepted", true)
            .putLong("legal_accepted_at", System.currentTimeMillis())
            .apply()
    }

    fun hasAcceptedLegal(context: Context): Boolean =
        p(context).getBoolean("legal_accepted", false)

    fun saveProfile(
        context: Context,
        name: String,
        age: Int?,
        profession: String?,
        bio: String?
    ) {
        p(context).edit()
            .putString("name", name.trim())
            .putInt("age", age ?: -1)
            .putString("profession", profession?.trim() ?: "")
            .putString("bio", bio?.trim() ?: "")
            .apply()
    }

    fun getName(context: Context): String = p(context).getString("name", "") ?: ""
    fun getAge(context: Context): Int {
        val a = p(context).getInt("age", -1)
        return if (a > 0) a else 0
    }
    fun getProfession(context: Context): String = p(context).getString("profession", "") ?: ""
    fun getBio(context: Context): String = p(context).getString("bio", "") ?: ""

    fun getInitials(context: Context): String {
        val name = getName(context)
        if (name.isBlank()) return "?"
        val parts = name.trim().split(Regex("\\s+"))
        return when {
            parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
            else -> name.take(1).uppercase()
        }
    }

    fun setOmniFolder(context: Context, path: String) {
        p(context).edit()
            .putString("omni_folder", path)
            .putBoolean("omni_folder_set", true)
            .apply()
    }
    fun getOmniFolder(context: Context): String? = p(context).getString("omni_folder", null)
    fun isOmniFolderSet(context: Context): Boolean = p(context).getBoolean("omni_folder_set", false)

    fun isIncognito(context: Context): Boolean = p(context).getBoolean("incognito", false)
    fun setIncognito(context: Context, value: Boolean) {
        p(context).edit().putBoolean("incognito", value).apply()
    }

    fun isOmniPlus(context: Context): Boolean = p(context).getBoolean("omni_plus", false)
    fun setOmniPlus(context: Context, value: Boolean) {
        p(context).edit().putBoolean("omni_plus", value).apply()
    }
    fun isAnalyticsUnlocked(context: Context): Boolean =
        p(context).getBoolean("analytics_unlocked", false)
    fun setAnalyticsUnlocked(context: Context, value: Boolean) {
        p(context).edit().putBoolean("analytics_unlocked", value).apply()
    }
    fun isAnalyticsCollectionEnabled(context: Context): Boolean =
        p(context).getBoolean("analytics_collection", true)
    fun setAnalyticsCollectionEnabled(context: Context, value: Boolean) {
        p(context).edit().putBoolean("analytics_collection", value).apply()
    }
    fun isLanguageAnalysisEnabled(context: Context): Boolean =
        p(context).getBoolean("analytics_language", true)
    fun setLanguageAnalysisEnabled(context: Context, value: Boolean) {
        p(context).edit().putBoolean("analytics_language", value).apply()
    }
    fun isPersonalityInsightsEnabled(context: Context): Boolean =
        p(context).getBoolean("analytics_personality", true)
    fun setPersonalityInsightsEnabled(context: Context, value: Boolean) {
        p(context).edit().putBoolean("analytics_personality", value).apply()
    }
}
