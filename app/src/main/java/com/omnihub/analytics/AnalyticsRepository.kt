package com.omnihub.analytics

import com.omnihub.history.ChatRepository
import com.omnihub.soul.SoulManager
import com.omnihub.soul.SoulType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class AnalyticsSnapshot(
    val totalRequests: Int = 0,
    val successes: Int = 0,
    val failures: Int = 0,
    val timeouts: Int = 0,
    val successRate: Double = 0.0,
    val avgLatencyMs: Long = 0,
    val medianLatencyMs: Long = 0,
    val fastestMs: Long = 0,
    val slowestMs: Long = 0,
    val totalTokens: Int = 0,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val tokensEstimatedAny: Boolean = false,
    val sourceBreakdown: List<Pair<String, Int>> = emptyList(),
    val modelBreakdown: List<Pair<String, Int>> = emptyList(),
    val failureCategories: Map<String, Int> = emptyMap(),
    val hourlyActivity: IntArray = IntArray(24),
    val daily: List<DailyAnalyticsEntity> = emptyList(),
    val topWords: List<WordFrequencyEntity> = emptyList(),
    val conversationCount: Int = 0,
    val messageCount: Int = 0,
    val avgMessagesPerConv: Double = 0.0,
    val sessionsToday: Int = 0,
    val sessionMinutesToday: Int = 0,
    val insights: List<String> = emptyList(),
    val rangeLabel: String = "30 Days"
)

data class OmniBookPreview(
    val projectCount: Int,
    val preferenceCount: Int,
    val goalCount: Int,
    val factCount: Int,
    val personCount: Int,
    val snippets: List<String>
)

class AnalyticsRepository(
    private val collector: AnalyticsCollector,
    private val chatRepo: ChatRepository,
    private val soul: SoulManager
) {
    private val dao get() = collector.dao()

    suspend fun snapshot(rangeDays: Int = 30): AnalyticsSnapshot = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val from = if (rangeDays <= 0) 0L else now - TimeUnit.DAYS.toMillis(rangeDays.toLong())
        val events = dao.requestEventsSince(from)
        val total = events.size
        val successes = events.count { it.eventType == AnalyticsEventType.REQUEST_COMPLETED.name }
        val failures = events.count { it.eventType == AnalyticsEventType.REQUEST_FAILED.name }
        val timeouts = events.count { it.eventType == AnalyticsEventType.REQUEST_TIMEOUT.name }
        val latencies = events.map { it.durationMs }.filter { it > 0 }.sorted()
        val avg = if (latencies.isEmpty()) 0L else latencies.average().toLong()
        val median = if (latencies.isEmpty()) 0L else latencies[latencies.size / 2]
        val sourceMap = events.groupingBy { it.sourceId ?: "unknown" }.eachCount()
        val modelMap = events.groupingBy { it.modelId ?: "Unknown / Source Managed" }.eachCount()
        val failCats = events.filter {
            it.eventType != AnalyticsEventType.REQUEST_COMPLETED.name
        }.groupingBy { it.errorCategory ?: "other" }.eachCount()
        val hourly = IntArray(24)
        val cal = Calendar.getInstance()
        for (e in events) {
            cal.timeInMillis = e.timestamp
            hourly[cal.get(Calendar.HOUR_OF_DAY)]++
        }
        val daily = dao.dailySince(from)
        val tokensIn = events.sumOf { it.inputTokens }
        val tokensOut = events.sumOf { it.outputTokens }
        val tokensTot = events.sumOf { it.totalTokens }
        val estimated = events.any { it.tokensEstimated }
        val convs = chatRepo.getConversations()
        var msgCount = 0
        for (c in convs) {
            msgCount += chatRepo.getMessages(c.id).size
        }
        val sessions = dao.recentSessions(50)
        val localStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val sessionsToday = sessions.filter { it.startedAt >= localStart }
        val sessionMin = sessionsToday.sumOf {
            val end = it.endedAt ?: now
            ((end - it.startedAt).coerceAtLeast(0) / 60000).toInt()
        }
        val insights = buildInsights(total, successes, failures, timeouts, sourceMap, hourly, daily)
        AnalyticsSnapshot(
            totalRequests = total,
            successes = successes,
            failures = failures,
            timeouts = timeouts,
            successRate = if (total == 0) 0.0 else (successes * 100.0 / total),
            avgLatencyMs = avg,
            medianLatencyMs = median,
            fastestMs = latencies.firstOrNull() ?: 0,
            slowestMs = latencies.lastOrNull() ?: 0,
            totalTokens = tokensTot,
            inputTokens = tokensIn,
            outputTokens = tokensOut,
            tokensEstimatedAny = estimated,
            sourceBreakdown = sourceMap.entries.sortedByDescending { it.value }.map { it.key to it.value },
            modelBreakdown = modelMap.entries.sortedByDescending { it.value }.map { it.key to it.value },
            failureCategories = failCats,
            hourlyActivity = hourly,
            daily = daily,
            topWords = dao.topWords(20),
            conversationCount = convs.size,
            messageCount = msgCount,
            avgMessagesPerConv = if (convs.isEmpty()) 0.0 else msgCount.toDouble() / convs.size,
            sessionsToday = sessionsToday.size,
            sessionMinutesToday = sessionMin,
            insights = insights,
            rangeLabel = when (rangeDays) {
                1 -> "Today"; 7 -> "7 Days"; 30 -> "30 Days"; 90 -> "90 Days"; else -> "All Time"
            }
        )
    }

    suspend fun omniBook(): OmniBookPreview = withContext(Dispatchers.IO) {
        val units = soul.loadUnits()
        fun count(t: SoulType) = units.count { it.type == t }
        val snippets = units.sortedByDescending { it.importance }.take(8)
            .map { "${it.type.name}: ${it.content.take(120)}" }
        OmniBookPreview(
            projectCount = count(SoulType.PROJECT),
            preferenceCount = count(SoulType.PREFERENCE),
            goalCount = count(SoulType.GOAL),
            factCount = count(SoulType.FACT),
            personCount = count(SoulType.PERSON),
            snippets = snippets
        )
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAllEvents()
        dao.deleteAllDaily()
        dao.deleteAllWords()
        dao.deleteAllSessions()
    }

    suspend fun exportJson(rangeDays: Int = 30): String = withContext(Dispatchers.IO) {
        val snap = snapshot(rangeDays)
        buildString {
            appendLine("{")
            appendLine("  \"range\": \"${snap.rangeLabel}\",")
            appendLine("  \"totalRequests\": ${snap.totalRequests},")
            appendLine("  \"successes\": ${snap.successes},")
            appendLine("  \"failures\": ${snap.failures},")
            appendLine("  \"timeouts\": ${snap.timeouts},")
            appendLine("  \"successRate\": ${"%.2f".format(snap.successRate)},")
            appendLine("  \"totalTokens\": ${snap.totalTokens},")
            appendLine("  \"tokensEstimated\": ${snap.tokensEstimatedAny}")
            appendLine("}")
        }
    }

    private fun buildInsights(
        total: Int,
        successes: Int,
        failures: Int,
        timeouts: Int,
        sources: Map<String, Int>,
        hourly: IntArray,
        daily: List<DailyAnalyticsEntity>
    ): List<String> {
        if (total < 5) return emptyList()
        val out = mutableListOf<String>()
        val peakHour = hourly.indices.maxByOrNull { hourly[it] } ?: -1
        if (peakHour >= 0 && hourly[peakHour] > 0) {
            out += "Peak hour: ${peakHour}:00 (${hourly[peakHour]} requests in range)."
        }
        sources.maxByOrNull { it.value }?.let {
            out += "Most used source: ${it.key} (${it.value} requests)."
        }
        if (total > 0) out += "Success rate: ${"%.1f".format(successes * 100.0 / total)}%."
        if (failures + timeouts > 0) out += "Failures+timeouts: ${failures + timeouts}."
        if (daily.size >= 7) {
            val recent = daily.takeLast(3).sumOf { it.requests }
            val older = daily.dropLast(3).takeLast(3).sumOf { it.requests }
            if (older > 0) {
                val delta = ((recent - older) * 100.0 / older).roundToInt()
                if (kotlin.math.abs(delta) >= 10) {
                    out += if (delta > 0) "Activity up ~$delta% vs prior window."
                    else "Activity down ~${-delta}% vs prior window."
                }
            }
        }
        return out.take(6)
    }
}
