package com.omnihub.source.core

import android.content.Context
import org.json.JSONObject

/**
 * Persistent layout map per provider. Validated against live DOM before use.
 * Not blindly trusted — invalid maps trigger one rediscovery, then update.
 */
object ProviderPageMap {
    private const val PREFS = "omni_page_map_v1"

    data class Map(
        val composer: String = "",
        val send: String = "",
        val conversation: String = "",
        val successCount: Int = 0,
        val failCount: Int = 0,
        val updatedAt: Long = 0L
    ) {
        fun isUsable(): Boolean = composer.isNotBlank() && failCount < 4

        fun toJson(): String = JSONObject().apply {
            put("composer", composer)
            put("send", send)
            put("conversation", conversation)
            put("successCount", successCount)
            put("failCount", failCount)
            put("updatedAt", updatedAt)
        }.toString()

        companion object {
            fun fromJson(raw: String?): Map {
                if (raw.isNullOrBlank()) return Map()
                return try {
                    val o = JSONObject(raw)
                    Map(
                        composer = o.optString("composer"),
                        send = o.optString("send"),
                        conversation = o.optString("conversation"),
                        successCount = o.optInt("successCount"),
                        failCount = o.optInt("failCount"),
                        updatedAt = o.optLong("updatedAt")
                    )
                } catch (_: Exception) {
                    Map()
                }
            }
        }
    }

    fun load(context: Context, providerId: String): Map =
        Map.fromJson(
            context.getSharedPreferences(PREFS, 0).getString(providerId, null)
        )

    fun save(context: Context, providerId: String, map: Map) {
        context.getSharedPreferences(PREFS, 0).edit()
            .putString(providerId, map.copy(updatedAt = System.currentTimeMillis()).toJson())
            .apply()
    }

    fun rememberSuccess(
        context: Context,
        providerId: String,
        composer: String?,
        send: String?,
        conversation: String? = null
    ) {
        val prev = load(context, providerId)
        save(
            context,
            providerId,
            prev.copy(
                composer = composer?.takeIf { it.isNotBlank() } ?: prev.composer,
                send = send?.takeIf { it.isNotBlank() } ?: prev.send,
                conversation = conversation?.takeIf { it.isNotBlank() } ?: prev.conversation,
                successCount = prev.successCount + 1,
                failCount = 0
            )
        )
        // Keep InteractionMemory in sync for older paths
        InteractionMemory.remember(context, providerId, composer, send)
    }

    fun rememberFailure(context: Context, providerId: String) {
        val prev = load(context, providerId)
        save(context, providerId, prev.copy(failCount = prev.failCount + 1))
        InteractionMemory.markFailure(context, providerId, "in")
        InteractionMemory.markFailure(context, providerId, "send")
    }
}
