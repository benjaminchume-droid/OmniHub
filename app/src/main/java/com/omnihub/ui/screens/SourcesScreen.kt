package com.omnihub.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnihub.OmniHubApp
import com.omnihub.source.*
import com.omnihub.ui.WebLoginActivity
import com.omnihub.ui.theme.OmniAmber
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as OmniHubApp
    val scope = rememberCoroutineScope()
    val sources by app.sourceManager.sources.collectAsState()
    var catalog by remember { mutableStateOf<List<SourceDescriptor>>(emptyList()) }
    var catalogStatus by remember { mutableStateOf<String?>(null) }
    var configSource by remember { mutableStateOf<AiSource?>(null) }
    var apiKeyDraft by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sources", fontWeight = FontWeight.SemiBold, color = OmniAmber) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            catalogStatus = "Fetching catalog…"
                            try {
                                catalog = SourceCatalog.fetch()
                                catalogStatus = "Catalog: ${catalog.size} sources"
                            } catch (e: Exception) { catalogStatus = e.message }
                        }
                    }) { Icon(Icons.Default.CloudDownload, "Fetch catalog", tint = OmniAmber) }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Installed") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Catalog") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Failures") })
            }
            catalogStatus?.let { Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
            when (tab) {
                0 -> LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sources, key = { it.info.id }) { src ->
                        SourceCard(
                            source = src,
                            enabled = src.isConfigured(),
                            failures = app.issueReporter.failureCount(src.info.id),
                            onToggle = {},
                            onConfigure = {
                                configSource = src
                                apiKeyDraft = ""
                            },
                            onReport = {
                                val err = app.issueReporter.lastError(src.info.id) ?: "unknown"
                                Toast.makeText(context, err.take(200), Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                }
                1 -> LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Button(onClick = {
                            scope.launch {
                                try {
                                    catalog = SourceCatalog.fetch()
                                    catalogStatus = "Loaded ${catalog.size}"
                                } catch (e: Exception) { catalogStatus = e.message }
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = OmniAmber, contentColor = Color.Black)) {
                            Text("Refresh remote catalog")
                        }
                    }
                    items(catalog, key = { it.id }) { d ->
                        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(d.name, fontWeight = FontWeight.Medium)
                                Text("${d.kind} · v${d.version}", style = MaterialTheme.typography.bodySmall)
                                Text(d.description, style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = {
                                    app.sourceManager.installDescriptor(d)
                                    Toast.makeText(context, "Installed ${d.name}", Toast.LENGTH_SHORT).show()
                                }) { Text("Install") }
                            }
                        }
                    }
                }
                2 -> LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val fails = app.issueReporter.allFailures()
                    if (fails.isEmpty()) item { Text("No source failures logged yet.") }
                    items(fails) { (id, count, err) ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("$id ×$count", fontWeight = FontWeight.SemiBold)
                                Text(err, style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { app.issueReporter.clear(id) }) { Text("Clear") }
                            }
                        }
                    }
                }
            }
        }
    }

    configSource?.let { src ->
        AlertDialog(
            onDismissRequest = { configSource = null },
            title = { Text("Configure ${src.info.name}") },
            text = {
                Column {
                    Text(src.info.description, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    if (src.info.authType == AuthType.API_KEY) {
                        OutlinedTextField(
                            value = apiKeyDraft,
                            onValueChange = { apiKeyDraft = it },
                            label = { Text("API key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    if (src.info.authType == AuthType.WEB_SESSION || src.info.kind == SourceKind.WEB_SESSION) {
                        TextButton(onClick = {
                            val url = src.info.websiteUrl.ifBlank { "https://chatgpt.com" }
                            context.startActivity(Intent(context, WebLoginActivity::class.java).apply {
                                putExtra(WebLoginActivity.EXTRA_URL, url)
                                putExtra(WebLoginActivity.EXTRA_TITLE, src.info.name)
                            })
                            configSource = null
                        }) { Text("Web login") }
                    }
                    TextButton(onClick = {
                        scope.launch {
                            if (apiKeyDraft.isNotBlank()) {
                                src.configure(SourceConfig(apiKey = apiKeyDraft.trim()))
                                app.sourceManager.reload()
                                app.reloadProviders()
                            }
                            configSource = null
                            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Save key") }
                }
            },
            dismissButton = { TextButton(onClick = { configSource = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun SourceCard(
    source: AiSource, enabled: Boolean, failures: Int,
    onToggle: (Boolean) -> Unit, onConfigure: () -> Unit, onReport: () -> Unit
) {
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(source.info.name, fontWeight = FontWeight.SemiBold)
                    Text("${source.info.kind} · ${source.info.authType}", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            AssistChip(onClick = {}, label = {
                Text(if (source.isConfigured()) "Configured" else "Not configured")
            })
            if (failures > 0) Text("$failures fails", color = MaterialTheme.colorScheme.error)
            Row {
                TextButton(onClick = onConfigure) { Text("Configure") }
                if (failures > 0) TextButton(onClick = onReport) { Text("Report issue") }
            }
        }
    }
}
