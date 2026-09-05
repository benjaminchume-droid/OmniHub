package com.omnihub.analytics

import android.content.Context
import com.omnihub.data.UserPrefs

/**
 * Premium gate for Analytics. Uses UserPrefs entitlement flags —
 * not hardcoded true. Wire to real billing when store is live.
 */
object AnalyticsEntitlement {
    fun isPremium(context: Context): Boolean =
        UserPrefs.isOmniPlus(context) || UserPrefs.isAnalyticsUnlocked(context)

    fun compactSummaryLabel(snapshot: AnalyticsSnapshot?): String {
        if (snapshot == null || snapshot.totalRequests == 0) return "Your Omni activity"
        val req = formatCount(snapshot.totalRequests)
        val tok = formatCount(snapshot.totalTokens)
        return "$req requests · $tok tokens"
    }

    private fun formatCount(n: Int): String = when {
        n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
        n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
        else -> n.toString()
    }
}
