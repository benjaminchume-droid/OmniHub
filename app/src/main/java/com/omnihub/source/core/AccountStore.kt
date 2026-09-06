package com.omnihub.source.core

import android.content.Context
import com.omnihub.data.SecureStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Multiple signed-in accounts per provider for routing. */
data class ProviderAccount(
    val accountId: String,
    val label: String,
    val providerId: String
)

object AccountStore {
    private const val PREFS = "omni_provider_accounts"

    fun listAccounts(context: Context, providerId: String): List<ProviderAccount> {
        val raw = context.getSharedPreferences(PREFS, 0).getString("accounts_$providerId", "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                ProviderAccount(
                    accountId = o.optString("id"),
                    label = o.optString("label"),
                    providerId = providerId
                )
            }
        }.getOrDefault(emptyList())
    }

    fun addAccount(context: Context, providerId: String, label: String): ProviderAccount {
        val id = UUID.randomUUID().toString().take(8)
        val acc = ProviderAccount(id, label.ifBlank { "Account" }, providerId)
        val list = listAccounts(context, providerId).toMutableList()
        list.add(acc)
        save(context, providerId, list)
        return acc
    }

    fun removeAccount(context: Context, providerId: String, accountId: String) {
        val list = listAccounts(context, providerId).filter { it.accountId != accountId }
        save(context, providerId, list)
        SecureStore.clearSession(context, sessionKey(providerId, accountId))
    }

    fun activeAccountId(context: Context, providerId: String): String? =
        context.getSharedPreferences(PREFS, 0).getString("active_$providerId", null)

    fun setActiveAccount(context: Context, providerId: String, accountId: String?) {
        context.getSharedPreferences(PREFS, 0).edit()
            .putString("active_$providerId", accountId)
            .apply()
    }

    fun sessionKey(providerId: String, accountId: String?): String =
        if (accountId.isNullOrBlank()) providerId else "${providerId}__$accountId"

    private fun save(context: Context, providerId: String, list: List<ProviderAccount>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.accountId).put("label", it.label))
        }
        context.getSharedPreferences(PREFS, 0).edit()
            .putString("accounts_$providerId", arr.toString())
            .apply()
    }
}
