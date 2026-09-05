package com.omnihub.source.core

/**
 * Reusable Web transport. Provider-specific Sources configure this core;
 * OmniHub Core does not hardcode site logic.
 */
data class WebNavigation(
    val loginPath: String = "/login",
    val chatPath: String = "/",
    val successUrlContains: List<String> = emptyList()
)

data class WebProtocol(
    val revision: String = "1",
    val requestTemplate: String? = null,
    val responseSelector: String? = null,
    val headers: Map<String, String> = emptyMap()
)

data class WebCoreConfig(
    val baseUrl: String,
    val navigation: WebNavigation = WebNavigation(),
    val protocol: WebProtocol = WebProtocol(),
    val sessionCookieNames: List<String> = emptyList(),
    val updateEndpoint: String? = null,
    val compatibilityMinCore: String = "1.0.2"
)

/**
 * WebCore executes provider-configured web/session flows.
 * Full site protocol handlers live in Source revisions, not OmniHub Core.
 */
class WebCore(private val config: WebCoreConfig) {
    fun baseUrl(): String = config.baseUrl.trimEnd('/')
    fun loginUrl(): String = baseUrl() + config.navigation.loginPath
    fun protocolRevision(): String = config.protocol.revision
    fun config(): WebCoreConfig = config
}
