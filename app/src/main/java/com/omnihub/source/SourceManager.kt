package com.omnihub.source

import android.content.Context
import android.util.Log
import com.omnihub.data.ChatRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages lifecycle of all AI sources: bundled, remote, extensions, web sessions.
 * Single source of truth for available sources.
 */
class SourceManager(private val context: Context) {
    private val mutex = Mutex()
    private val sources = mutableMapOf<String, AiSource>()
    private val listeners = mutableListOf<SourceListener>()

    suspend fun registerSource(source: AiSource) {
        mutex.withLock {
            Log.d(TAG, "Registering source: ${source.id}")
            if (sources.containsKey(source.id)) {
                Log.w(TAG, "Source ${source.id} already registered, replacing")
            }
            sources[source.id] = source
            source.onInstall()
            notifyListeners(SourceEvent.Registered(source))
        }
    }

    suspend fun unregisterSource(sourceId: String) {
        mutex.withLock {
            val source = sources.remove(sourceId)
            if (source != null) {
                Log.d(TAG, "Unregistering source: $sourceId")
                source.onUninstall()
                notifyListeners(SourceEvent.Unregistered(source))
            }
        }
    }

    suspend fun updateSource(sourceId: String, newSource: AiSource) {
        mutex.withLock {
            val oldSource = sources[sourceId]
            if (oldSource != null) {
                Log.d(TAG, "Updating source: $sourceId to version ${newSource.version}")
                newSource.onUpdate(oldSource.version)
                sources[sourceId] = newSource
                notifyListeners(SourceEvent.Updated(oldSource, newSource))
            }
        }
    }

    suspend fun getSource(id: String): AiSource? {
        return mutex.withLock { sources[id] }
    }

    suspend fun getAllSources(): List<AiSource> {
        return mutex.withLock { sources.values.toList() }
    }

    suspend fun getEnabledSources(): List<AiSource> {
        return mutex.withLock { sources.values.filter { it.isEnabled }.toList() }
    }

    suspend fun getSourcesByType(type: SourceType): List<AiSource> {
        return mutex.withLock { sources.values.filter { it.type == type }.toList() }
    }

    fun addListener(listener: SourceListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: SourceListener) {
        listeners.remove(listener)
    }

    private fun notifyListeners(event: SourceEvent) {
        listeners.forEach { it.onSourceEvent(event) }
    }

    suspend fun reload() {
        mutex.withLock {
            Log.d(TAG, "Reloading all sources")
            sources.clear()
            notifyListeners(SourceEvent.ReloadStarted)
            // Will be repopulated by SourceBootstrap
            notifyListeners(SourceEvent.ReloadCompleted)
        }
    }

    companion object {
        private const val TAG = "SourceManager"
    }
}

interface SourceListener {
    fun onSourceEvent(event: SourceEvent)
}

sealed class SourceEvent {
    data class Registered(val source: AiSource) : SourceEvent()
    data class Unregistered(val source: AiSource) : SourceEvent()
    data class Updated(val old: AiSource, val new: AiSource) : SourceEvent()
    object ReloadStarted : SourceEvent()
    object ReloadCompleted : SourceEvent()
}
