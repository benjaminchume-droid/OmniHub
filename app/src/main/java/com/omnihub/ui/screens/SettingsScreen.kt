package com.omnihub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenConnectors: () -> Unit = {},
    onOpenLegal: (LegalDoc) -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("AI Providers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ProviderKeyField("OpenAI")
            ProviderKeyField("Anthropic (Claude)")
            ProviderKeyField("Google Gemini")
            ProviderKeyField("DeepSeek")
            ProviderKeyField("xAI Grok")
            ProviderKeyField("Perplexity")
            ProviderKeyField("Kimi (Moonshot)")
            ProviderKeyField("Z.AI (Zhipu)")
            ProviderKeyField("NVIDIA NIM")
            ProviderKeyField("Groq")

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Web Sessions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Paste the full Cookie header after logging into the site.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            ProviderKeyField("ChatGPT Web Cookie")
            ProviderKeyField("Claude Web Cookie")
            ProviderKeyField("Gemini Web Cookie")
            ProviderKeyField("Grok Web Cookie")

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Connectors", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ListItem(
                headlineContent = { Text("MCP Servers & tools") },
                supportingContent = { Text("GitHub, Supabase, Vercel, Gmail, Maps, custom URL") },
                leadingContent = { Icon(Icons.Outlined.Extension, null) },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clickable(onClick = onOpenConnectors)
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Legal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ListItem(
                headlineContent = { Text("Privacy Policy") },
                leadingContent = { Icon(Icons.Default.PrivacyTip, null) },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clickable { onOpenLegal(LegalDoc.PRIVACY) }
            )
            ListItem(
                headlineContent = { Text("Terms of Service") },
                leadingContent = { Icon(Icons.Default.Description, null) },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clickable { onOpenLegal(LegalDoc.TERMS) }
            )
            ListItem(
                headlineContent = { Text("Community Guidelines") },
                leadingContent = { Icon(Icons.Default.Groups, null) },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clickable { onOpenLegal(LegalDoc.COMMUNITY) }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("App", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ListItem(headlineContent = { Text("Version") }, supportingContent = { Text("1.0.0") }, leadingContent = { Icon(Icons.Default.Info, null) })
            ListItem(headlineContent = { Text("Set as Digital Assistant") }, supportingContent = { Text("Make OmniHub the default assistant in system settings") }, leadingContent = { Icon(Icons.Default.RecordVoiceOver, null) })
        }
    }
}

@Composable
private fun ProviderKeyField(label: String) {
    var value by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
            }
        }
    )
}
