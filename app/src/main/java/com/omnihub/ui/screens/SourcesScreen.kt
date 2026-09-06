package com.omnihub.ui.screens

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.omnihub.OmniHubApp
import com.omnihub.source.core.AccountStore
import com.omnihub.source.core.ProviderAuthStore
import com.omnihub.ui.WebLoginActivity
import com.omnihub.ui.theme.OmniAmber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SourcesScreen(onBack: () -> Unit, onOpenStore: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as OmniHubApp
    val sources by app.sourceManager.sources.collectAsState()
    var tick by remember { mutableStateOf(0) }
    var menuSourceId by remember { mutableStateOf<String?>(null) }
    var menuSourceName by remember { mutableStateOf("") }
    var menuSourceUrl by remember { mutableStateOf("") }

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

    fun openSignIn(providerId: String, name: String, url: String, accountId: String? = null) {
        if (accountId != null) {
            AccountStore.setActiveAccount(context, providerId, accountId)
        }
        val i = Intent(context, WebLoginActivity::class.java).apply {
            putExtra(WebLoginActivity.EXTRA_URL, url.ifBlank { "https://chatgpt.com" })
            putExtra(WebLoginActivity.EXTRA_TITLE, name)
            putExtra("provider_id", providerId)
            putExtra("provider_name", name)
            if (accountId != null) putExtra("account_id", accountId)
        }
        context.startActivity(i)
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
                    "Open Store, install a provider APK, then sign in here.",
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
                        "Tap to sign in · long-press for accounts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(sources, key = { it.info.id + tick }) { src ->
                    val signed = ProviderAuthStore.isSignedIn(context, src.info.id)
                    val accounts = AccountStore.listAccounts(context, src.info.id)
                    val active = AccountStore.activeAccountId(context, src.info.id)
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (!signed) {
                                        openSignIn(src.info.id, src.info.name, src.info.websiteUrl)
                                    }
                                },
                                onLongClick = {
                                    menuSourceId = src.info.id
                                    menuSourceName = src.info.name
                                    menuSourceUrl = src.info.websiteUrl
                                }
                            )
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(src.info.name, fontWeight = FontWeight.SemiBold)
                                val sub = when {
                                    accounts.isNotEmpty() && active != null ->
                                        accounts.find { it.accountId == active }?.label ?: "Ready"
                                    signed -> "Ready"
                                    else -> "Sign in · long-press for accounts"
                                }
                                Text(
                                    sub,
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
                                        openSignIn(src.info.id, src.info.name, src.info.websiteUrl)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = OmniAmber,
                                        contentColor = Color.Black
                                    )
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

    menuSourceId?.let { pid ->
        val accounts = AccountStore.listAccounts(context, pid)
        AlertDialog(
            onDismissRequest = { menuSourceId = null },
            title = { Text(menuSourceName) },
            text = {
                Column {
                    TextButton(onClick = {
                        val acc = AccountStore.addAccount(context, pid, "Account ${accounts.size + 1}")
                        AccountStore.setActiveAccount(context, pid, acc.accountId)
                        menuSourceId = null
                        openSignIn(pid, menuSourceName, menuSourceUrl, acc.accountId)
                    }) { Text("Add account") }
                    accounts.forEach { acc ->
                        TextButton(onClick = {
                            AccountStore.setActiveAccount(context, pid, acc.accountId)
                            menuSourceId = null
                            openSignIn(pid, menuSourceName, menuSourceUrl, acc.accountId)
                            tick++
                        }) { Text("Use ${acc.label}") }
                    }
                    if (accounts.isNotEmpty()) {
                        Text(
                            "Each account keeps its own session for routing.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuSourceId = null }) { Text("Close") }
            }
        )
    }
}
