package com.omnihub.source.core

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Strict per-provider serialization.
 * Never runs two WebView transactions on the same provider at once.
 * Callers queue instead of force-unlocking or dual-injecting ("hey hey hey").
 */
object ProviderRelayQueue {
    private const val TAG = "WEB_QUEUE"
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val waiters = ConcurrentHashMap<String, Int>()

    private fun mutex(providerId: String): Mutex =
        locks.getOrPut(providerId) { Mutex() }

    /**
     * Run [block] exclusively for [providerId].
     * Returns queue-wait ms so callers can log overhead.
     */
    suspend fun <T> withProviderLock(providerId: String, block: suspend (queueWaitMs: Long) -> T): T {
        val m = mutex(providerId)
        val start = System.currentTimeMillis()
        val queued = m.isLocked
        if (queued) {
            val n = waiters.merge(providerId, 1) { a, _ -> a + 1 } ?: 1
            Log.i(TAG, "[$providerId] QUEUED waiters=$n")
        }
        return m.withLock {
            try {
                waiters.computeIfPresent(providerId) { _, v -> (v - 1).coerceAtLeast(0) }
                val waitMs = System.currentTimeMillis() - start
                if (waitMs > 50) Log.i(TAG, "[$providerId] DEQUEUED after ${waitMs}ms")
                block(waitMs)
            } finally {
                // lock released by withLock
            }
        }
    }
}
