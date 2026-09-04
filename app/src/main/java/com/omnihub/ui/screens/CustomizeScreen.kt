package com.omnihub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Skills", "Behavior", "Tone", "MCP Server")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customize", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> SkillsTab()
                1 -> BehaviorTab()
                2 -> ToneTab()
                3 -> McpServerTab()
            }
        }
    }
}

@Composable
private fun SkillsTab() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Skills & Tools", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Enable or disable skills the model can use. MCP servers also appear here once connected.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )

        listOf(
            "Web Search" to true,
            "Code Interpreter" to true,
            "Image Generation" to false,
            "File Analysis" to true,
            "Browser Control" to false,
            "Soul Memory" to true
        ).forEach { (name, enabled) ->
            var checked by remember { mutableStateOf(enabled) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = checked, onCheckedChange = { checked = it })
                }
            }
        }
    }
}

@Composable
private fun BehaviorTab() {
    var systemPrompt by remember {
        mutableStateOf("You are OmniHub, a helpful, truthful, and maximally useful AI assistant running on the user’s device.")
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Behavior", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Custom instructions that shape every response.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)

        OutlinedTextField(
            value = systemPrompt,
            onValueChange = { systemPrompt = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            label = { Text("Custom Instructions") },
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun ToneTab() {
    val tones = listOf("Neutral", "Friendly", "Professional", "Witty", "Direct", "Empathetic")
    var selected by remember { mutableStateOf("Direct") }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Tone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        tones.forEach { tone ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selected = tone },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected == tone) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected == tone, onClick = { selected = tone })
                    Spacer(Modifier.width(8.dp))
                    Text(tone, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun McpServerTab() {
    var url by remember { mutableStateOf("") }
    var servers by remember {
        mutableStateOf(
            listOf(
                "Local filesystem MCP" to true,
                "GitHub MCP" to false
            )
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("MCP Servers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Paste an MCP server URL (SSE or Streamable HTTP). Complete any login the server requires — then its tools are available. Same flow as Claude & Grok.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("MCP Server URL") },
            placeholder = { Text("https://mcp.example.com/sse") },
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Button(
            onClick = {
                if (url.isNotBlank()) {
                    servers = servers + (url.take(40) to true)
                    url = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = url.isNotBlank()
        ) {
            Icon(Icons.Default.Link, null)
            Spacer(Modifier.width(8.dp))
            Text("Connect MCP Server")
        }

        Divider()

        Text("Connected", style = MaterialTheme.typography.titleSmall)
        servers.forEach { (name, connected) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(name, fontWeight = FontWeight.Medium)
                        Text(if (connected) "Connected" else "Disconnected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    if (connected) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
