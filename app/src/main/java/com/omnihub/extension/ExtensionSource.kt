package com.omnihub.extension

import android.content.Context
import com.omnihub.source.AiSource

/**
 * Base class for extensions to inherit from.
 * Extensions must implement this interface in their APK.
 */
abstract class ExtensionSource(context: Context) : AiSource {
    protected val context = context
    
    open suspend fun onInstall() {}
    open suspend fun onUpdate(fromVersion: String) {}
    open suspend fun onUninstall() {}
}
