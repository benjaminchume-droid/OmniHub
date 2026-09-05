package com.omnihub.source.builtin

import android.content.Context
import android.util.Log
import com.omnihub.source.SourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bootstrap all built-in sources on app startup
 */
object SourceBootstrap {
    private const val TAG = "SourceBootstrap"

    suspend fun loadBuiltinSources(context: Context, manager: SourceManager) {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Loading built-in sources...")

            val sources = listOf(
                OpenAiSource(context),
                AnthropicSource(context),
                GeminiSource(context),
                GroqSource(context),
                DeepSeekSource(context),
                MistralSource(context),
                PerplexitySource(context)
            )

            sources.forEach { source ->
                try {
                    manager.registerSource(source)
                    Log.d(TAG, "Registered ${source.id}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register ${source.id}", e)
                }
            }

            Log.d(TAG, "Built-in sources loaded: ${sources.size}")
        }
    }
}
