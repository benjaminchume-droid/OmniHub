package com.omnihub.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.omnihub.OmniHubApp
import com.omnihub.data.SecureStore
import com.omnihub.data.UserPrefs
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenConnectors: () -> Unit,
    onOpenLegal: (LegalDoc) -> Unit,
    onOpenAnalytics: () -> Unit = {},
    onOpenStore: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as OmniHubApp
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var collect by remember { mutableStateOf(UserPrefs.isAnalyticsCollectionEnabled(context)) }
    var language by remember { mutableStateOf(UserPrefs.isLanguageAnalysisEnabled(context)) }
    var personality by remember { mutableStateOf(UserPrefs.isPersonalityInsightsEnabled(context)) }
    var analyticsSummary by remember { mutableStateOf("Your Omni activity") }

    LaunchedEffect(Unit) {
        try {
            val snap = app.analyticsRepo.snapshot(30)
            analyticsSummary = com.omnihub.analytics.AnalyticsEntitlement.compactSummaryLabel(snap)
        } catch (_: Exception) {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Omni", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ListItem(
                headlineContent = { Text("Analytics") },
                supportingContent = { Text(analyticsSummary) },
                leadingContent = { Icon(Icons.Default.Analytics, null) },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clickable { onOpenAnalytics() }
            )
            ListItem(
                headlineContent = { Text("Omni Store") },
                supportingContent = { Text("Install API, Web, and MCP sources from OmniHub-Sources") },
                leadingContent = { Icon(Icons.Default.Store, null) },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clickable { onOpenStore() }
            )

            Divider(Modifier.padding(vertical = 8.dp))
            Text("Privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ListItem(
                headlineContent = { Text("Analytics collection") },
                trailingContent = {
                    Switch(checked = collect, onCheckedChange = {
                        collect = it
                        UserPrefs.setAnalyticsCollectionEnabled(context, it)
                    })
                }
            )
            ListItem(
                headlineContent = { Text("Language analysis") },
                trailingContent = {
                    Switch(checked = language, onCheckedChange = {
                        language = it
                        UserPrefs.setLanguageAnalysisEnabled(context, it)
                    })
                }
            )
            ListItem(
                headlineContent = { Text("Personality insights") },
                trailingContent = {
                    Switch(checked = personality, onCheckedChange = {
                        personality = it
                        UserPrefs.setPersonalityInsightsEnabled(context, it)
                    })
                }
            )

            Divider(Modifier.padding(vertical = 8.dp))
            Text("API Keys", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Keys use Android Keystore. Paste a key and send a message — that is the path that replies.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ProviderKeyField("OpenAI", "openai")
            ProviderKeyField("Anthropic", "anthropic")
            ProviderKeyField("Gemini", "gemini")
            ProviderKeyField("Groq", "groq")
            ProviderKeyField("DeepSeek", "deepseek")
            ProviderKeyField("OpenRouter", "openrouter")
            ProviderKeyField("Kimi", "kimi")
            ProviderKeyField("Mistral", "mistral")
            ProviderKeyField("Perplexity", "perplexity")
            ProviderKeyField("NVIDIA", "nvidia")
            ProviderKeyField("Z.AI", "zai")

            Divider(Modifier.padding(vertical = 8.dp))
            Text("Connectors", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ListItem(
                headlineContent = { Text("MCP / Connectors") },
                supportingContent = { Text("GitHub, Supabase, Vercel, Gmail, Maps, custom URLs") },
                leadingContent = { Icon(Icons.Default.Extension, null) },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clickable { onOpenConnectors() }
            )

            Divider(Modifier.padding(vertical = 8.dp))
            Text("Data & Privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ListItem(
                headlineContent = { Text("Export my data") },
                supportingContent = { Text("Save conversations as JSON on device") },
                leadingContent = { Icon(Icons.Default.Download, null) },
                modifier = Modifier.clickable {
                    scope.launch {
                        try {
                            val json = app.chatRepo.exportAsJson()
                            val dir = context.getExternalFilesDir(null) ?: context.filesDir
                            val file = File(dir, "omnihub_export_${System.currentTimeMillis()}.json")
                            file.writeText(json)
                            status = "Exported to ${file.absolutePath}"
                            Toast.makeText(context, "Exported", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            status = e.message
                        }
                    }
                }
            )
            ListItem(
                headlineContent = { Text("Delete all data") },
                supportingContent = { Text("Wipe chats, keys, and sessions from this device") },
                leadingContent = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { confirmDelete = true }
            )

            Divider(Modifier.padding(vertical = 8.dp))
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

            Divider(Modifier.padding(vertical = 8.dp))
            Text("App", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text("1.0.2") },
                leadingContent = { Icon(Icons.Default.Info, null) }
            )
            ListItem(
                headlineContent = { Text("Set as Digital Assistant") },
                supportingContent = { Text("Opens system settings so you can set OmniHub as default") },
                leadingContent = { Icon(Icons.Default.RecordVoiceOver, null) },
                modifier = Modifier.clickable {
                    try {
                        val intents = listOf(
                            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
                            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
                            Intent(Settings.ACTION_SETTINGS)
                        )
                        var launched = false
                        for (intent in intents) {
                            try {
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                                launched = true
                                break
                            } catch (_: Exception) {}
                        }
                        if (!launched) {
                            Toast.makeText(context, "Open Settings → Default apps → Digital assistant", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "Could not open settings", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            status?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all data?") },
            text = { Text("This permanently removes all conversations, API keys, and web sessions from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        try {
                            app.chatRepo.deleteAll()
                            SecureStore.clearAllSecrets(context)
                            app.reloadProviders()
                            status = "All local data deleted"
                            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            status = e.message
                        }
                    }
                }) { Text("Delete everything") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProviderKeyField(label: String, providerId: String) {
    val context = LocalContext.current
    val app = context.applicationContext as OmniHubApp
    var value by remember { mutableStateOf(SecureStore.getApiKey(context, providerId).orEmpty()) }
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = {
            value = it
            if (it.isBlank()) {
                SecureStore.removeSecret(context, "api_key_$providerId")
            } else {
                SecureStore.setApiKey(context, providerId, it.trim())
            }
            app.reloadProviders()
        },
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
