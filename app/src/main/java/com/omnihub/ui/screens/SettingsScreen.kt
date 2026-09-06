package com.omnihub.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnihub.BuildConfig
import com.omnihub.OmniHubApp
import com.omnihub.data.SecureStore
import com.omnihub.data.UserPrefs
import com.omnihub.update.AppUpdateChecker
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
    val omniFolder = remember { UserPrefs.getOmniFolder(context) ?: "Not set" }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }

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
                headlineContent = { Text("Store") },
                supportingContent = { Text("Install providers from OmniSource") },
                leadingContent = { Icon(Icons.Default.Store, null) },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clickable { onOpenStore() }
            )
            ListItem(
                headlineContent = { Text("Analytics") },
                supportingContent = { Text(analyticsSummary) },
                leadingContent = { Icon(Icons.Default.Analytics, null) },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clickable { onOpenAnalytics() }
            )
            ListItem(
                headlineContent = { Text("Omni folder") },
                supportingContent = { Text(omniFolder) },
                leadingContent = { Icon(Icons.Default.Folder, null) }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
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

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Connectors", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ListItem(
                headlineContent = { Text("MCP connectors") },
                supportingContent = { Text("Installed tool providers") },
                leadingContent = { Icon(Icons.Default.Extension, null) },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clickable { onOpenConnectors() }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ListItem(
                headlineContent = { Text("Export my data") },
                leadingContent = { Icon(Icons.Default.Download, null) },
                modifier = Modifier.clickable {
                    scope.launch {
                        try {
                            val json = app.chatRepo.exportAsJson()
                            val dir = context.getExternalFilesDir(null) ?: context.filesDir
                            val file = File(dir, "omnihub_export_${System.currentTimeMillis()}.json")
                            file.writeText(json)
                            status = "Exported"
                            Toast.makeText(context, "Exported", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            status = e.message
                        }
                    }
                }
            )
            ListItem(
                headlineContent = { Text("Delete all data") },
                leadingContent = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { confirmDelete = true }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Legal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ListItem(
                headlineContent = { Text("Privacy Policy") },
                leadingContent = { Icon(Icons.Default.PrivacyTip, null) },
                modifier = Modifier.clickable { onOpenLegal(LegalDoc.PRIVACY) }
            )
            ListItem(
                headlineContent = { Text("Terms of Service") },
                leadingContent = { Icon(Icons.Default.Description, null) },
                modifier = Modifier.clickable { onOpenLegal(LegalDoc.TERMS) }
            )
            ListItem(
                headlineContent = { Text("Community Guidelines") },
                leadingContent = { Icon(Icons.Default.Groups, null) },
                modifier = Modifier.clickable { onOpenLegal(LegalDoc.COMMUNITY) }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Updates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ListItem(
                headlineContent = { Text("Check for updates") },
                supportingContent = {
                    Text(updateStatus ?: "Looks for Nightly builds on GitHub")
                },
                leadingContent = { Icon(Icons.Default.SystemUpdate, null) },
                trailingContent = {
                    if (checkingUpdate) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.ChevronRight, null)
                    }
                },
                modifier = Modifier.clickable(enabled = !checkingUpdate) {
                    checkingUpdate = true
                    updateStatus = "Checking…"
                    scope.launch {
                        try {
                            val info = AppUpdateChecker.checkOmniHub(context)
                            if (info == null) {
                                updateStatus = "You're on the latest build"
                            } else {
                                updateStatus = "Downloading ${info.tag}…"
                                val result = AppUpdateChecker.downloadAndInstall(context, info)
                                updateStatus = if (result.isSuccess) {
                                    "Install when prompted (${info.tag})"
                                } else {
                                    result.exceptionOrNull()?.message ?: "Download failed"
                                }
                            }
                        } catch (e: Exception) {
                            updateStatus = e.message ?: "Check failed"
                        } finally {
                            checkingUpdate = false
                        }
                    }
                }
            )
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text(BuildConfig.VERSION_NAME) },
                leadingContent = { Icon(Icons.Default.Info, null) }
            )
            ListItem(
                headlineContent = { Text("Digital assistant") },
                supportingContent = { Text("Set as default") },
                leadingContent = { Icon(Icons.Default.RecordVoiceOver, null) },
                modifier = Modifier.clickable {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {
                        Toast.makeText(context, "Open system settings", Toast.LENGTH_SHORT).show()
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
            text = { Text("Removes chats and local secrets from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        try {
                            app.chatRepo.deleteAll()
                            SecureStore.clearAllSecrets(context)
                            app.reloadProviders()
                            status = "Deleted"
                        } catch (e: Exception) {
                            status = e.message
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}
