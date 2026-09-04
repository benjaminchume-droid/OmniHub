package com.omnihub.providers

import android.content.Context

class ProviderRegistry(private val context: Context) {
    private val providers = mutableListOf<AiProvider>()

    fun register(provider: AiProvider) {
        providers.removeAll { it.id == provider.id }
        providers.add(provider)
    }

    fun allProviders(): List<AiProvider> = providers.toList()

    fun get(id: String): AiProvider? = providers.find { it.id == id }

    fun hasAny(): Boolean = providers.isNotEmpty()

    fun clear() {
        providers.clear()
    }
}
