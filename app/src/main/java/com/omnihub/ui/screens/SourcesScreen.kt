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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as OmniHubApp
    val sources by app.sourceManager.sources.collectAsState()
    var tick by remember { mutableStateOf(0) }

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
            item {
                Text(
                    "Sign in once. Messages go to the provider and the reply shows here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(sources.filter { it.info.bundled }, key = { it.info.id + tick }) { src ->
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
                                    val i = Intent(context, WebLoginActivity::class.java).apply {
                                        putExtra(WebLoginActivity.EXTRA_URL, src.info.websiteUrl)
                                        putExtra(WebLoginActivity.EXTRA_TITLE, src.info.name)
                                        putExtra("provider_id", src.info.id)
                                        putExtra("provider_name", src.info.name)
                                    }
                                    context.startActivity(i)
                                    // Do NOT mark signed-in until WebLoginActivity saves a real session
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
