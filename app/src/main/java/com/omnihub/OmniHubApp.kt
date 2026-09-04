package com.omnihub

import android.app.Application
import com.omnihub.core.OmniRouter
import com.omnihub.history.ChatRepository
import com.omnihub.mcp.McpClient
import com.omnihub.providers.ProviderRegistry
import com.omnihub.providers.impl.ProviderBootstrap
import com.omnihub.providers.websession.WebSessionManager
import com.omnihub.soul.SoulManager

class OmniHubApp : Application() {
    lateinit var registry: ProviderRegistry
        private set
    lateinit var router: OmniRouter
        private set
    lateinit var soul: SoulManager
        private set
    lateinit var chatRepo: ChatRepository
        private set
    lateinit var webSessions: WebSessionManager
        private set
    lateinit var mcp: McpClient
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        registry = ProviderRegistry(this)
        soul = SoulManager(this)
        chatRepo = ChatRepository(this)
        webSessions = WebSessionManager(this)
        mcp = McpClient(this)
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
