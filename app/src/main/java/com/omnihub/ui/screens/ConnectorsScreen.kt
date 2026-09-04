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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnihub.mcp.AuthType
import com.omnihub.mcp.BuiltInMcp
import com.omnihub.mcp.McpCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectorsScreen(onBack: () -> Unit) {
    var showAddSheet by remember { mutableStateOf(false) }
    var mcpUrl by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connectors", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { showAddSheet = true }) { Icon(Icons.Default.Add, "Add MCP") } }
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
                ConnectorCard(mcp = mcp, isConnected = mcp.id in connected, onConnect = { connected = connected + mcp.id })
            }
            item {
                Spacer(Modifier.height(16.dp))
                Text("Custom MCP servers", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text("Paste an MCP URL, authorize, then issue tasks from chat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Add MCP Server", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = mcpUrl, onValueChange = { mcpUrl = it }, label = { Text("MCP URL") }, placeholder = { Text("https://… or mcp://…") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                Button(onClick = { showAddSheet = false; mcpUrl = "" }, enabled = mcpUrl.isNotBlank(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("Connect & authorize") }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ConnectorCard(mcp: BuiltInMcp, isConnected: Boolean, onConnect: () -> Unit) {
    Card(onClick = onConnect, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Extension, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(mcp.name, fontWeight = FontWeight.Medium)
                Text(mcp.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(when (mcp.authType) { AuthType.API_KEY -> "API key"; AuthType.WEB_SESSION -> "Web session"; AuthType.OAUTH -> "OAuth"; AuthType.URL -> "URL" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (isConnected) AssistChip(onClick = {}, label = { Text("Connected") })
            else OutlinedButton(onClick = onConnect) { Text("Connect") }
        }
    }
}
