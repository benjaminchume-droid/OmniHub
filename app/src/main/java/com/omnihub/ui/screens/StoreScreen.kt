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
import com.omnihub.source.extension.ApkInstaller
import com.omnihub.ui.theme.OmniAmber
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<ApkInstaller.ReleaseItem>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    var installing by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                items = ApkInstaller.fetchReleases()
                if (items.isEmpty()) {
                    error = "No source APKs in Releases yet. Run OmniHub-Sources factory to publish APKs."
                }
            } catch (e: Exception) {
                error = e.message ?: "Failed to load releases"
                items = emptyList()
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    val filtered = remember(items, query, filter) {
        items.filter { s ->
            (filter == "ALL" || s.kind.equals(filter, true)) &&
                (query.isBlank() || s.name.contains(query, true) || s.id.contains(query, true))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("OmniSource Store", fontWeight = FontWeight.SemiBold)
                        Text("From GitHub Releases", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { load() }) { Icon(Icons.Default.Refresh, "Refresh") }
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
                listOf("ALL", "WEB", "MCP").forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${filtered.size} APKs · enable Install unknown apps for OmniHub",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OmniAmber)
                }
                error != null && items.isEmpty() -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Store, null, tint = OmniAmber)
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { load() },
                        colors = ButtonDefaults.buttonColors(containerColor = OmniAmber, contentColor = Color.Black)
                    ) { Text("Retry") }
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.apkUrl }) { rel ->
                        Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CloudDownload, null, tint = OmniAmber)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(rel.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${rel.kind} · ${rel.tag} · ${rel.size / 1024} KB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            installing = rel.id
                                            try {
                                                val file = ApkInstaller.downloadApk(context, rel.apkUrl, "${rel.id}.apk")
                                                ApkInstaller.promptInstall(context, file)
                                                Toast.makeText(context, "Allow install from this source if asked", Toast.LENGTH_LONG).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, e.message ?: "Install failed", Toast.LENGTH_LONG).show()
                                            } finally {
                                                installing = null
                                            }
                                        }
                                    },
                                    enabled = installing == null,
                                    colors = ButtonDefaults.buttonColors(containerColor = OmniAmber, contentColor = Color.Black)
                                ) { Text(if (installing == rel.id) "\u2026" else "Install") }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
