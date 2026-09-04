package com.omnihub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnihub.OmniHubApp
import com.omnihub.mcp.AuthType
import com.omnihub.mcp.BuiltInMcp
import com.omnihub.mcp.McpCatalog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectorsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as OmniHubApp
    val scope = rememberCoroutineScope()
    var showAddSheet by remember { mutableStateOf(false) }
    var mcpUrl by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var servers by remember { mutableStateOf(app.mcp.listServers()) }
    var connecting by remember { mutableStateOf(false) }

    fun refresh() { servers = app.mcp.listServers() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connectors", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddSheet = true }) {
                        Icon(Icons.Default.Add, "Add MCP")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text("Built-in", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
            }
            items(McpCatalog.builtIn) { mcp ->
                ConnectorCard(mcp = mcp, onConnect = {
                    app.mcp.openAuthorization(mcp)
                    status = "Opened ${mcp.name} authorization. Complete login then return."
                })
            }
            item {
                Spacer(Modifier.height(16.dp))
                Text("Custom MCP servers", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text("Paste an MCP URL. OmniHub probes it, then opens the auth page if needed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(servers, key = { it.id }) { server ->
                Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(server.name, fontWeight = FontWeight.Medium)
                        Text(server.url, style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (server.connected) "Connected" else (server.lastError ?: "Not connected"),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (server.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        if (server.tools.isNotEmpty()) {
                            Text("Tools: ${server.tools.joinToString()}", style = MaterialTheme.typography.bodySmall)
                        }
                        Row {
                            TextButton(onClick = { app.mcp.openInBrowser(server.url) }) { Text("Open auth") }
                            TextButton(onClick = { app.mcp.disconnect(server.id); refresh() }) { Text("Remove") }
                        }
                    }
                }
            }
            status?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Add MCP Server", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = mcpUrl,
                    onValueChange = { mcpUrl = it },
                    label = { Text("MCP URL") },
                    placeholder = { Text("https://… or sse://…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = {
                        if (mcpUrl.isBlank() || connecting) return@Button
                        connecting = true
                        status = "Probing…"
                        scope.launch {
                            val result = app.mcp.connect(mcpUrl)
                            connecting = false
                            result.fold(
                                onSuccess = { server ->
                                    status = if (server.connected) "Connected to ${server.name}" else "Saved. ${server.lastError ?: "Open auth to finish"}"
                                    app.mcp.openInBrowser(server.url)
                                    refresh()
                                    showAddSheet = false
                                    mcpUrl = ""
                                },
                                onFailure = { e -> status = e.message ?: "Connect failed" }
                            )
                        }
                    },
                    enabled = mcpUrl.isNotBlank() && !connecting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if (connecting) "Connecting…" else "Connect & authorize") }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ConnectorCard(mcp: BuiltInMcp, onConnect: () -> Unit) {
    Card(onClick = onConnect, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Extension, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(mcp.name, fontWeight = FontWeight.Medium)
                Text(mcp.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    when (mcp.authType) {
                        AuthType.API_KEY -> "API key"
                        AuthType.WEB_SESSION -> "Web session"
                        AuthType.OAUTH -> "OAuth"
                        AuthType.URL -> "URL"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            OutlinedButton(onClick = onConnect) { Text("Connect") }
        }
    }
}
