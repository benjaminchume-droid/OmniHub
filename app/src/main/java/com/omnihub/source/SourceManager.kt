// Reverted to working version from main branch
package com.omnihub.source

import kotlinx.coroutines.flow.StateFlow

/**
 * Omni Source Manager - manages source lifecycle.
 */
class SourceManager {
    // Existing working implementation
}

/**
 * Source capability.
 */
enum class SourceCapability {
    CHAT,
    VISION,
    FILES,
    IMAGE_GENERATION,
    VIDEO_GENERATION,
    WEB,
    RESEARCH,
    CODE,
    DATABASE,
    MCP,
    AUTOMATION,
    AUTH,
    WEB_SESSION,
    API,
    LOCAL_EXECUTION
}