package com.omnihub.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Encrypted storage via Android Keystore (AES256-GCM). */
object SecureStore {
    private const val PREFS_NAME = "omnihub_secure"

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun putSecret(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value).apply()
    }

    fun getSecret(context: Context, key: String): String? =
        prefs(context).getString(key, null)

    fun removeSecret(context: Context, key: String) {
        prefs(context).edit().remove(key).apply()
    }

    fun clearAllSecrets(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun setApiKey(context: Context, providerId: String, apiKey: String) =
        putSecret(context, "api_key_$providerId", apiKey)

    fun getApiKey(context: Context, providerId: String): String? =
        getSecret(context, "api_key_$providerId")

    fun setSession(context: Context, providerId: String, cookieHeader: String) =
        putSecret(context, "session_$providerId", cookieHeader)

    fun getSession(context: Context, providerId: String): String? =
        getSecret(context, "session_$providerId")

    fun clearSession(context: Context, providerId: String) =
        removeSecret(context, "session_$providerId")
}
