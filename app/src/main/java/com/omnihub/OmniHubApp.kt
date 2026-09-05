package com.omnihub

import android.app.Application
import com.omnihub.analytics.AnalyticsCollector
import com.omnihub.analytics.AnalyticsRepository
import com.omnihub.core.OmniRouter
import com.omnihub.history.ChatRepository
import com.omnihub.mcp.McpClient
import com.omnihub.providers.ProviderRegistry
import com.omnihub.providers.impl.ProviderBootstrap
import com.omnihub.providers.websession.WebSessionManager
import com.omnihub.soul.SoulManager
import com.omnihub.source.AutoIssueReporter
import com.omnihub.source.SourceManager
import com.omnihub.source.SourceRouter

class OmniHubApp : Application() {
    lateinit var registry: ProviderRegistry
        private set
    lateinit var router: OmniRouter
        private set
    lateinit var sourceRouter: SourceRouter
        private set
    lateinit var sourceManager: SourceManager
        private set
    lateinit var issueReporter: AutoIssueReporter
        private set
    lateinit var soul: SoulManager
        private set
    lateinit var chatRepo: ChatRepository
        private set
    lateinit var webSessions: WebSessionManager
        private set
    lateinit var mcp: McpClient
        private set
    lateinit var analytics: AnalyticsCollector
        private set
    lateinit var analyticsRepo: AnalyticsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        registry = ProviderRegistry(this)
        soul = SoulManager(this)
        chatRepo = ChatRepository(this)
        webSessions = WebSessionManager(this)
        mcp = McpClient(this)
        sourceManager = SourceManager(this)
        issueReporter = AutoIssueReporter(this)
        analytics = AnalyticsCollector(this)
        analyticsRepo = AnalyticsRepository(analytics, chatRepo, soul)
        ProviderBootstrap.reload(this, registry)
        router = OmniRouter(registry)
        sourceRouter = SourceRouter(sourceManager, soul, issueReporter)
    }

    fun reloadProviders() {
        ProviderBootstrap.reload(this, registry)
        sourceManager.reload()
    }

    companion object {
        lateinit var instance: OmniHubApp
            private set
    }
}
