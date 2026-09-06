package com.omnihub.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnihub.OmniHubApp
import com.omnihub.source.AiSource
import com.omnihub.source.AuthType
import com.omnihub.source.SourceKind
import com.omnihub.ui.WebLoginActivity
import com.omnihub.ui.theme.OmniAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as OmniHubApp
    var sources by remember { mutableStateOf(app.sourceManager.all()) }
    var selected by remember { mutableStateOf<AiSource?>(null) }

    fun refresh() { sources = app.sourceManager.all() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Providers", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sources, key = { it.info.id }) { src ->
                val ready = src.isConfigured() || src.info.authType == AuthType.NONE
                Card(
                    onClick = { selected = src },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Extension, null, tint = OmniAmber)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(src.info.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${src.info.kind} · ${if (ready) "Ready" else "Sign in"}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        if (ready) {
                            Icon(Icons.Default.CheckCircle, null, tint = OmniAmber)
                        } else {
                            Text("Sign in", color = OmniAmber, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }

    selected?.let { src ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(src.info.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(src.info.description.ifBlank { "Installed provider" })
                    Text("Type: ${src.info.kind}")
                    Text(if (src.isConfigured()) "Status: Ready" else "Status: Needs sign-in")
                }
            },
            confirmButton = {
                if (!src.isConfigured() && (src.info.kind == SourceKind.WEB_SESSION || src.info.authType == AuthType.WEB_SESSION)) {
                    TextButton(onClick = {
                        val url = src.info.websiteUrl.ifBlank { "https://chatgpt.com" }
                        context.startActivity(Intent(context, WebLoginActivity::class.java).apply {
                            putExtra(WebLoginActivity.EXTRA_URL, url)
                            putExtra(WebLoginActivity.EXTRA_TITLE, src.info.name)
                        })
                        selected = null
                    }) { Text("Sign in") }
                } else {
                    TextButton(onClick = { selected = null }) { Text("Close") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    app.sourceManager.uninstall(src.info.id)
                    refresh()
                    selected = null
                }) { Text("Uninstall") }
            }
        )
    }
}
