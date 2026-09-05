package com.omnihub.extension

import android.content.Context
import com.omnihub.source.AiSource

/**
 * Base class for APK extensions. Subclasses implement chat/info/isConfigured.
 */
abstract class ExtensionSource(
    protected val appContext: Context
) : AiSource {
    override suspend fun onInstall() {}
    override suspend fun onUninstall() {}
    open suspend fun onUpdate(fromVersion: String) {}
}
