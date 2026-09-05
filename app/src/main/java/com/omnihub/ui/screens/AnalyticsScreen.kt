package com.omnihub.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnihub.OmniHubApp
import com.omnihub.analytics.AnalyticsEntitlement
import com.omnihub.analytics.AnalyticsSnapshot
import com.omnihub.analytics.OmniBookPreview
import com.omnihub.data.UserPrefs
import com.omnihub.ui.theme.OmniAmber
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as OmniHubApp
    val scope = rememberCoroutineScope()
    var rangeDays by remember { mutableIntStateOf(30) }
    var snap by remember { mutableStateOf<AnalyticsSnapshot?>(null) }
    var book by remember { mutableStateOf<OmniBookPreview?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selectedDay by remember { mutableStateOf<Long?>(null) }
    var premium by remember { mutableStateOf(AnalyticsEntitlement.isPremium(context)) }

    fun reload() {
        scope.launch {
            loading = true
            try {
                snap = app.analyticsRepo.snapshot(rangeDays)
                book = if (UserPrefs.isPersonalityInsightsEnabled(context)) app.analyticsRepo.omniBook() else null
            } catch (_: Exception) {
                snap = AnalyticsSnapshot()
            }
            loading = false
        }
    }

    LaunchedEffect(rangeDays, premium) { if (premium) reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (premium) {
                        TextButton(onClick = {
                            scope.launch {
                                val json = app.analyticsRepo.exportJson(rangeDays)
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("omni-analytics", json))
                                Toast.makeText(context, "Export copied (no secrets)", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Export") }
                    }
                }
            )
        }
    ) { padding ->
        if (!premium) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Lock, null, Modifier.size(48.dp), tint = OmniAmber)
                Spacer(Modifier.height(12.dp))
                Text("Omni Analytics", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Understand how you use Omni: usage, tokens, activity, sources, performance, Omni Book, insights.")
                Spacer(Modifier.height(16.dp))
                listOf("AI usage", "Tokens", "Activity", "Sources", "Performance", "Omni Book").forEach { Text("✓ $it") }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        UserPrefs.setAnalyticsUnlocked(context, true)
                        premium = true
                        Toast.makeText(context, "Analytics unlocked (dev entitlement until billing is live)", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OmniAmber)
                ) { Text("Unlock Analytics", color = Color.Black) }
            }
            return@Scaffold
        }

        if (loading && snap == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = OmniAmber)
            }
            return@Scaffold
        }

        val s = snap ?: AnalyticsSnapshot()
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "Today", 7 to "7D", 30 to "30D", 90 to "90D", 0 to "All").forEach { (d, label) ->
                        FilterChip(selected = rangeDays == d, onClick = { rangeDays = d }, label = { Text(label) })
                    }
                }
            }
            if (s.totalRequests == 0) {
                item {
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Icon(Icons.Default.Analytics, null, tint = OmniAmber)
                            Spacer(Modifier.height(8.dp))
                            Text("Your Omni Analytics", fontWeight = FontWeight.Bold)
                            Text("You haven't generated enough activity yet. Start using Omni and real metrics appear here.")
                        }
                    }
                }
            } else {
                item { Text(s.rangeLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Metric("Requests", formatN(s.totalRequests), Modifier.weight(1f))
                        Metric("Tokens", formatN(s.totalTokens) + if (s.tokensEstimatedAny) "*" else "", Modifier.weight(1f))
                        Metric("Success", String.format("%.1f%%", s.successRate), Modifier.weight(1f))
                    }
                }
                if (s.tokensEstimatedAny) {
                    item { Text("* Some token totals are estimated", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Metric("Failed", "${s.failures}", Modifier.weight(1f))
                        Metric("Timeouts", "${s.timeouts}", Modifier.weight(1f))
                        Metric("Avg latency", formatMs(s.avgLatencyMs), Modifier.weight(1f))
                    }
                }
                item {
                    Section("Performance")
                    Text("Median ${formatMs(s.medianLatencyMs)} · Fastest ${formatMs(s.fastestMs)} · Slowest ${formatMs(s.slowestMs)}")
                }
                item {
                    Section("Active hours")
                    val maxV = s.hourlyActivity.maxOrNull()?.coerceAtLeast(1) ?: 1
                    s.hourlyActivity.forEachIndexed { h, v ->
                        if (v > 0 || h in 8..22) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(String.format("%02d", h), modifier = Modifier.width(28.dp), style = MaterialTheme.typography.labelSmall)
                                LinearProgressIndicator(progress = { v.toFloat() / maxV }, modifier = Modifier.weight(1f).height(8.dp), color = OmniAmber, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                                Text("$v", modifier = Modifier.width(28.dp), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                item {
                    Section("Activity")
                    if (s.daily.isEmpty()) Text("No daily activity yet")
                    else {
                        s.daily.takeLast(28).chunked(7).forEach { week ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                week.forEach { d ->
                                    val intensity = when {
                                        d.requests >= 50 -> 0.95f; d.requests >= 20 -> 0.7f; d.requests >= 5 -> 0.45f; d.requests > 0 -> 0.25f; else -> 0.08f
                                    }
                                    val color = when {
                                        d.failures > d.successes && d.requests > 0 -> Color(0xFFE57373).copy(alpha = intensity)
                                        d.warnings > 0 -> Color(0xFFFFB74D).copy(alpha = intensity)
                                        d.specialEvents > 0 -> Color(0xFF64B5F6).copy(alpha = intensity)
                                        else -> Color(0xFF81C784).copy(alpha = intensity)
                                    }
                                    Box(Modifier.size(18.dp).background(color, RoundedCornerShape(4.dp)).clickable { selectedDay = d.dayEpoch })
                                }
                            }
                        }
                        Text("Green activity · Red failures · Orange warnings · Blue milestones", style = MaterialTheme.typography.labelSmall)
                    }
                }
                selectedDay?.let { day ->
                    item {
                        val d = s.daily.find { it.dayEpoch == day }
                        if (d != null) {
                            Card(shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(day)), fontWeight = FontWeight.Bold)
                                    Text("${d.requests} requests · ${d.totalTokens} tokens")
                                    Text("${d.failures} failures · ${d.timeouts} timeouts")
                                }
                            }
                        }
                    }
                }
                item {
                    Section("Sources")
                    s.sourceBreakdown.take(8).forEachIndexed { i, (id, n) ->
                        val pct = if (s.totalRequests == 0) 0 else n * 100 / s.totalRequests
                        Text(String.format("%02d  %s  %d (%d%%)", i + 1, id, n, pct))
                        LinearProgressIndicator(progress = { n.toFloat() / max(1, s.totalRequests) }, modifier = Modifier.fillMaxWidth().height(6.dp), color = OmniAmber, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(Modifier.height(6.dp))
                    }
                }
                item {
                    Section("Models")
                    s.modelBreakdown.take(6).forEach { (m, n) -> Text("$m — $n") }
                }
                item {
                    Section("Failures")
                    if (s.failureCategories.isEmpty()) Text("None in range")
                    else s.failureCategories.forEach { (k, v) -> Text("$k: $v") }
                }
                item {
                    Section("Conversations")
                    Text("${s.conversationCount} conversations · ${s.messageCount} messages · avg ${String.format("%.1f", s.avgMessagesPerConv)}")
                    Text("Today: ${s.sessionsToday} sessions · ${s.sessionMinutesToday}m")
                }
                item {
                    Section("Most-used words")
                    if (s.topWords.isEmpty()) Text("Not enough user text yet")
                    else s.topWords.take(12).forEachIndexed { i, w -> Text(String.format("%02d  %s  (%d)", i + 1, w.word, w.count)) }
                }
                item {
                    Section("Your Omni Book")
                    val b = book
                    if (b == null) Text("Personality insights disabled in Privacy")
                    else {
                        Text("Projects ${b.projectCount} · Preferences ${b.preferenceCount} · Goals ${b.goalCount}")
                        Text("Facts ${b.factCount} · People ${b.personCount}")
                        b.snippets.take(5).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        Text("Observations only — backed by Soul units, not diagnoses.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    Section("Insights")
                    if (s.insights.isEmpty()) Text("Need more activity for insights")
                    else s.insights.forEach { Text("• $it") }
                }
                item {
                    Section("Billing")
                    Text("Omni subscription and provider costs are separate.")
                    Text("Provider cost estimates: Cost unavailable (no pricing config).")
                }
                item {
                    OutlinedButton(onClick = {
                        scope.launch {
                            app.analyticsRepo.deleteAll()
                            reload()
                            Toast.makeText(context, "Analytics data deleted", Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Delete analytics data") }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable private fun Section(t: String) {
    Text(t, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OmniAmber)
}

@Composable private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatN(n: Int): String = when {
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
    else -> n.toString()
}

private fun formatMs(ms: Long): String = when {
    ms <= 0 -> "—"
    ms < 1000 -> "${ms}ms"
    else -> String.format("%.2fs", ms / 1000.0)
}
