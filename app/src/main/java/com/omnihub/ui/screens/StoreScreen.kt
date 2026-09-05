package com.omnihub.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnihub.OmniHubApp
import com.omnihub.source.SourceCatalog
import com.omnihub.source.SourceDescriptor
import com.omnihub.ui.theme.OmniAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as OmniHubApp
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var sources by remember { mutableStateOf<List<SourceDescriptor>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    var repoLabel by remember { mutableStateOf("benjaminchume-droid/OmniHub-Sources") }
    var catalogUrl by remember {
        mutableStateOf("https://raw.githubusercontent.com/benjaminchume-droid/OmniHub-Sources/main/catalog/index.min.json")
    }

    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                val storeMeta = withContext(Dispatchers.IO) {
                    runCatching {
                        context.assets.open("store.json").bufferedReader().readText()
                    }.getOrNull()
                }
                if (!storeMeta.isNullOrBlank()) {
                    val o = JSONObject(storeMeta)
                    repoLabel = o.optString("repo", repoLabel)
                    catalogUrl = o.optString("catalogUrl", catalogUrl)
                }
                sources = SourceCatalog.fetch(catalogUrl)
            } catch (e: Exception) {
                error = e.message ?: "Failed to load store"
                sources = emptyList()
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    val filtered = remember(sources, query, filter) {
        sources.filter { s ->
            (filter == "ALL" || s.kind.equals(filter, true) ||
                (filter == "WEB_SESSION" && s.kind.contains("WEB", true))) &&
                (query.isBlank() || s.name.contains(query, true) || s.id.contains(query, true) ||
                    s.description.contains(query, true))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Omni Store", fontWeight = FontWeight.SemiBold)
                        Text(repoLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { load() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search sources") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ALL", "API", "WEB_SESSION", "MCP").forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(if (f == "WEB_SESSION") "Web" else f) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${filtered.size} sources · catalog live from GitHub",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OmniAmber)
                }
                error != null -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Store, null, tint = OmniAmber)
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Button(
                        onClick = { load() },
                        colors = ButtonDefaults.buttonColors(containerColor = OmniAmber, contentColor = Color.Black)
                    ) { Text("Retry") }
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { desc ->
                        StoreSourceCard(
                            desc = desc,
                            installed = app.sourceManager.get(desc.id) != null,
                            onInstall = {
                                scope.launch {
                                    try {
                                        app.sourceManager.installDescriptor(desc)
                                        Toast.makeText(context, "Installed ${desc.name}", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, e.message ?: "Install failed", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun StoreSourceCard(
    desc: SourceDescriptor,
    installed: Boolean,
    onInstall: () -> Unit
) {
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CloudDownload, null, tint = OmniAmber)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(desc.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "${desc.kind} · ${desc.authType} · ${desc.revision}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (desc.description.isNotBlank()) {
                    Text(desc.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
            if (installed) {
                Text("Installed", color = OmniAmber, style = MaterialTheme.typography.labelMedium)
            } else {
                Button(
                    onClick = onInstall,
                    colors = ButtonDefaults.buttonColors(containerColor = OmniAmber, contentColor = Color.Black)
                ) { Text("Install") }
            }
        }
    }
}
