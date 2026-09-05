package com.omnihub.analytics

import android.content.Context
import androidx.room.Room
import com.omnihub.data.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** Async event sink. Never blocks chat. Failures must not propagate. */
class AnalyticsCollector(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = Room.databaseBuilder(appContext, AnalyticsDatabase::class.java, "omni_analytics.db")
        .fallbackToDestructiveMigration().build()
    private val dao = db.dao()
    private val activeSession = AtomicReference<String?>(null)
    private val sessionStart = AtomicReference(0L)

    fun ensureSession() {
        if (!UserPrefs.isAnalyticsCollectionEnabled(appContext)) return
        if (activeSession.get() != null) return
        val id = UUID.randomUUID().toString()
        activeSession.set(id)
        sessionStart.set(System.currentTimeMillis())
        emit(
            AnalyticsEventEntity(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                sessionId = id,
                conversationId = null,
                requestId = null,
                sourceId = null,
                modelId = null,
                eventType = AnalyticsEventType.SESSION_STARTED.name,
                status = AnalyticsStatus.SUCCESS.name
            )
        )
        scope.launch {
            dao.upsertSession(SessionAnalyticsEntity(id = id, startedAt = sessionStart.get()))
        }
    }

    fun currentSessionId(): String? = activeSession.get()

    fun emit(event: AnalyticsEventEntity) {
        if (!UserPrefs.isAnalyticsCollectionEnabled(appContext)) return
        scope.launch {
            try {
                dao.insertEvent(event)
                bumpDaily(event)
            } catch (_: Exception) {}
        }
    }

    fun recordRequestStart(requestId: String, conversationId: String?, sourceId: String?, modelId: String?) {
        ensureSession()
        emit(
            AnalyticsEventEntity(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                sessionId = currentSessionId(),
                conversationId = conversationId,
                requestId = requestId,
                sourceId = sourceId,
                modelId = modelId,
                eventType = AnalyticsEventType.REQUEST_STARTED.name,
                status = AnalyticsStatus.UNKNOWN.name
            )
        )
    }

    fun recordRequestResult(
        requestId: String,
        conversationId: String?,
        sourceId: String?,
        modelId: String?,
        durationMs: Long,
        success: Boolean,
        timedOut: Boolean,
        inputTokens: Int,
        outputTokens: Int,
        totalTokens: Int,
        tokensEstimated: Boolean,
        errorCategory: String? = null
    ) {
        ensureSession()
        val type = when {
            timedOut -> AnalyticsEventType.REQUEST_TIMEOUT
            success -> AnalyticsEventType.REQUEST_COMPLETED
            else -> AnalyticsEventType.REQUEST_FAILED
        }
        val status = when {
            timedOut -> AnalyticsStatus.TIMEOUT
            success -> AnalyticsStatus.SUCCESS
            else -> AnalyticsStatus.FAILED
        }
        emit(
            AnalyticsEventEntity(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                sessionId = currentSessionId(),
                conversationId = conversationId,
                requestId = requestId,
                sourceId = sourceId,
                modelId = modelId,
                eventType = type.name,
                status = status.name,
                durationMs = durationMs,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                totalTokens = totalTokens,
                tokensEstimated = tokensEstimated,
                errorCategory = errorCategory
            )
        )
        scope.launch {
            try {
                val sid = currentSessionId() ?: return@launch
                val s = dao.session(sid) ?: return@launch
                val sources = s.sourceIdsJson.trim('[', ']').split(',').map { it.trim().trim('"') }.filter { it.isNotBlank() }.toMutableSet()
                if (!sourceId.isNullOrBlank()) sources.add(sourceId)
                dao.upsertSession(
                    s.copy(
                        requestCount = s.requestCount + 1,
                        tokenCount = s.tokenCount + totalTokens,
                        sourceIdsJson = sources.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
                    )
                )
            } catch (_: Exception) {}
        }
    }

    fun recordUserMessage(conversationId: String?, text: String) {
        if (!UserPrefs.isLanguageAnalysisEnabled(appContext)) return
        ensureSession()
        emit(
            AnalyticsEventEntity(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                sessionId = currentSessionId(),
                conversationId = conversationId,
                requestId = null,
                sourceId = null,
                modelId = null,
                eventType = AnalyticsEventType.MESSAGE_SENT.name,
                status = AnalyticsStatus.SUCCESS.name
            )
        )
        scope.launch {
            try {
                val words = WordAnalyzer.extractUserWords(text)
                val now = System.currentTimeMillis()
                val existing = dao.topWords(200).associateBy { it.word }
                for (w in words) {
                    val count = (existing[w]?.count ?: 0) + 1
                    dao.upsertWord(WordFrequencyEntity(w, count, now))
                }
                val sid = currentSessionId() ?: return@launch
                val s = dao.session(sid) ?: return@launch
                dao.upsertSession(s.copy(messageCount = s.messageCount + 1))
            } catch (_: Exception) {}
        }
    }

    private suspend fun bumpDaily(event: AnalyticsEventEntity) {
        val day = startOfDayUtc(event.timestamp)
        val cur = dao.dailyFor(day) ?: DailyAnalyticsEntity(dayEpoch = day)
        val isReq = event.eventType in listOf(
            AnalyticsEventType.REQUEST_COMPLETED.name,
            AnalyticsEventType.REQUEST_FAILED.name,
            AnalyticsEventType.REQUEST_TIMEOUT.name
        )
        dao.upsertDaily(
            cur.copy(
                requests = cur.requests + if (isReq) 1 else 0,
                successes = cur.successes + if (event.eventType == AnalyticsEventType.REQUEST_COMPLETED.name) 1 else 0,
                failures = cur.failures + if (event.eventType == AnalyticsEventType.REQUEST_FAILED.name) 1 else 0,
                timeouts = cur.timeouts + if (event.eventType == AnalyticsEventType.REQUEST_TIMEOUT.name) 1 else 0,
                totalTokens = cur.totalTokens + event.totalTokens,
                inputTokens = cur.inputTokens + event.inputTokens,
                outputTokens = cur.outputTokens + event.outputTokens,
                totalDurationMs = cur.totalDurationMs + event.durationMs,
                warnings = cur.warnings + if (event.eventType == AnalyticsEventType.WARNING.name) 1 else 0
            )
        )
    }

    fun dao(): AnalyticsDao = dao

    companion object {
        fun startOfDayUtc(ts: Long): Long {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.timeInMillis = ts
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
    }
}
