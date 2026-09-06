package com.omnihub.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Login
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
import com.omnihub.source.core.ProviderAuthStore
import com.omnihub.ui.WebLoginActivity
import com.omnihub.ui.theme.OmniAmber
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(onBack: () -> Unit, onOpenStore: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as OmniHubApp
    val sources by app.sourceManager.sources.collectAsState()
    var tick by remember { mutableStateOf(0) }

    // Refresh when returning from install / sign-in
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                app.sourceManager.reload()
                tick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Providers", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        app.sourceManager.reload()
                        tick++
                    }) { Icon(Icons.Default.Refresh, "Refresh") }
                    IconButton(onClick = onOpenStore) { Icon(Icons.Default.Store, "Store") }
                }
            )
        }
    ) { padding ->
        if (sources.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No sources installed", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Open Store, install a provider APK, then come back here to sign in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onOpenStore,
                    colors = ButtonDefaults.buttonColors(containerColor = OmniAmber, contentColor = Color.Black)
                ) { Text("Open Store") }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "Installed sources · sign in, then chat",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(sources, key = { it.info.id + tick }) { src ->
                    val signed = ProviderAuthStore.isSignedIn(context, src.info.id)
                    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(src.info.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (signed) "Ready" else "Sign in",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (signed) OmniAmber else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (signed) {
                                Icon(Icons.Default.CheckCircle, null, tint = OmniAmber)
                                TextButton(onClick = {
                                    ProviderAuthStore.setSignedIn(context, src.info.id, false)
                                    tick++
                                }) { Text("Sign out") }
                            } else {
                                Button(
                                    onClick = {
                                        val url = src.info.websiteUrl.ifBlank { "https://chatgpt.com" }
                                        val i = Intent(context, WebLoginActivity::class.java).apply {
                                            putExtra(WebLoginActivity.EXTRA_URL, url)
                                            putExtra(WebLoginActivity.EXTRA_TITLE, src.info.name)
                                            putExtra("provider_id", src.info.id)
                                            putExtra("provider_name", src.info.name)
                                        }
                                        context.startActivity(i)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = OmniAmber, contentColor = Color.Black)
                                ) {
                                    Icon(Icons.Default.Login, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Sign in")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
