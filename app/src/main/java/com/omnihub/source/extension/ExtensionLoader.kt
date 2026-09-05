package com.omnihub.source.extension

import android.content.Context
import android.util.Log
import com.omnihub.source.AiSource
import dalvik.system.PathClassLoader
import java.io.File

class ExtensionLoader(private val context: Context) {
    private val extensionDir = File(context.filesDir, "extensions")

    init {
        extensionDir.mkdirs()
    }

    suspend fun loadExtensions(): List<AiSource> {
        val extensions = mutableListOf<AiSource>()
        
        extensionDir.listFiles()?.filter { it.extension == "apk" }?.forEach { apk ->
            try {
                Log.d(TAG, "Loading extension: ${apk.name}")
                val source = loadExtensionApk(apk)
                // TODO: Verify signature
                // TODO: Show trust prompt
                extensions.add(source)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load extension: ${apk.name}", e)
            }
        }
        
        return extensions
    }

    private fun loadExtensionApk(apk: File): AiSource {
        try {
            val loader = PathClassLoader(apk.absolutePath, this.javaClass.classLoader)
            val clazz = loader.loadClass("com.omnihub.extension.ExtensionSource")
            val constructor = clazz.getConstructor(Context::class.java)
            return constructor.newInstance(context) as AiSource
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load extension class", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "ExtensionLoader"
    }
}
