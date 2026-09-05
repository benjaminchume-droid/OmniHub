package com.omnihub.analytics

import androidx.room.*

enum class AnalyticsEventType {
    REQUEST_STARTED, REQUEST_COMPLETED, REQUEST_FAILED, REQUEST_TIMEOUT,
    MESSAGE_SENT, MESSAGE_RECEIVED,
    SESSION_STARTED, SESSION_ENDED,
    SOURCE_SELECTED, SOURCE_FAILED,
    AGENT_STARTED, AGENT_COMPLETED, AGENT_FAILED,
    TOOL_STARTED, TOOL_COMPLETED, TOOL_FAILED,
    FILE_PROCESSED,
    TASK_STARTED, TASK_COMPLETED, TASK_FAILED,
    AUTH_STARTED, AUTH_COMPLETED, AUTH_FAILED,
    WARNING, ERROR,
    BILLING_CHARGE, BILLING_REFUND
}

enum class AnalyticsStatus { SUCCESS, FAILED, TIMEOUT, CANCELLED, UNKNOWN }

@Entity(
    tableName = "analytics_events",
    indices = [
        Index("timestamp"),
        Index("sessionId"),
        Index("conversationId"),
        Index("requestId"),
        Index("sourceId"),
        Index("eventType")
    ]
)
data class AnalyticsEventEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val sessionId: String?,
    val conversationId: String?,
    val requestId: String?,
    val sourceId: String?,
    val modelId: String?,
    val eventType: String,
    val status: String,
    val durationMs: Long = 0,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0,
    val tokensEstimated: Boolean = false,
    val estimatedCost: Double = 0.0,
    val errorCategory: String? = null,
    val metadataJson: String? = null
)

@Entity(tableName = "analytics_daily")
data class DailyAnalyticsEntity(
    @PrimaryKey val dayEpoch: Long,
    val requests: Int = 0,
    val successes: Int = 0,
    val failures: Int = 0,
    val timeouts: Int = 0,
    val totalTokens: Int = 0,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalDurationMs: Long = 0,
    val sessions: Int = 0,
    val conversationsTouched: Int = 0,
    val warnings: Int = 0,
    val specialEvents: Int = 0
)

@Entity(tableName = "word_frequency")
data class WordFrequencyEntity(
    @PrimaryKey val word: String,
    val count: Int,
    val lastSeen: Long
)

@Entity(tableName = "analytics_sessions")
data class SessionAnalyticsEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val messageCount: Int = 0,
    val requestCount: Int = 0,
    val tokenCount: Int = 0,
    val sourceIdsJson: String = "[]",
    val summary: String? = null
)

@Dao
interface AnalyticsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(e: AnalyticsEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDaily(d: DailyAnalyticsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWord(w: WordFrequencyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(s: SessionAnalyticsEntity)

    @Query("SELECT * FROM analytics_events WHERE timestamp >= :from AND timestamp <= :to ORDER BY timestamp DESC")
    suspend fun eventsInRange(from: Long, to: Long): List<AnalyticsEventEntity>

    @Query("SELECT * FROM analytics_events WHERE eventType IN ('REQUEST_COMPLETED','REQUEST_FAILED','REQUEST_TIMEOUT') AND timestamp >= :from")
    suspend fun requestEventsSince(from: Long): List<AnalyticsEventEntity>

    @Query("SELECT * FROM analytics_daily WHERE dayEpoch >= :from ORDER BY dayEpoch ASC")
    suspend fun dailySince(from: Long): List<DailyAnalyticsEntity>

    @Query("SELECT * FROM analytics_daily WHERE dayEpoch = :day LIMIT 1")
    suspend fun dailyFor(day: Long): DailyAnalyticsEntity?

    @Query("SELECT * FROM word_frequency ORDER BY count DESC LIMIT :limit")
    suspend fun topWords(limit: Int = 40): List<WordFrequencyEntity>

    @Query("SELECT * FROM analytics_sessions ORDER BY startedAt DESC LIMIT :limit")
    suspend fun recentSessions(limit: Int = 20): List<SessionAnalyticsEntity>

    @Query("SELECT * FROM analytics_sessions WHERE id = :id LIMIT 1")
    suspend fun session(id: String): SessionAnalyticsEntity?

    @Query("SELECT COUNT(*) FROM analytics_events WHERE eventType IN ('REQUEST_COMPLETED','REQUEST_FAILED','REQUEST_TIMEOUT')")
    suspend fun totalRequestEvents(): Int

    @Query("DELETE FROM analytics_events")
    suspend fun deleteAllEvents()

    @Query("DELETE FROM analytics_daily")
    suspend fun deleteAllDaily()

    @Query("DELETE FROM word_frequency")
    suspend fun deleteAllWords()

    @Query("DELETE FROM analytics_sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM analytics_events WHERE timestamp < :before")
    suspend fun pruneEvents(before: Long)
}

@Database(
    entities = [
        AnalyticsEventEntity::class,
        DailyAnalyticsEntity::class,
        WordFrequencyEntity::class,
        SessionAnalyticsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AnalyticsDatabase : RoomDatabase() {
    abstract fun dao(): AnalyticsDao
}
