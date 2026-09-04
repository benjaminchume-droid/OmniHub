package com.omnihub

import android.app.Application
import com.omnihub.core.OmniRouter
import com.omnihub.providers.ProviderRegistry
import com.omnihub.providers.impl.ProviderBootstrap
import com.omnihub.soul.SoulManager

class OmniHubApp : Application() {
    lateinit var registry: ProviderRegistry
        private set
    lateinit var router: OmniRouter
        private set
    lateinit var soul: SoulManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        registry = ProviderRegistry(this)
        soul = SoulManager(this)
        ProviderBootstrap.reload(this, registry)
        router = OmniRouter(registry)
    }

    fun reloadProviders() {
        ProviderBootstrap.reload(this, registry)
    }

    companion object {
        lateinit var instance: OmniHubApp
            private set
    }
}
