package com.omnihub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnihub.OmniHubApp
import com.omnihub.ui.theme.OmniAmber
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    onBack: () -> Unit,
    onPick: (List<File>) -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as OmniHubApp
    var current by remember { mutableStateOf(app.workspace.root()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val entries = remember(current) {
        current.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))?.toList()
            ?: emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.name.ifBlank { "Omni" }, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = {
                        val parent = current.parentFile
                        val root = app.workspace.root()
                        if (parent != null && parent.path.startsWith(root.path)) {
                            current = parent
                        } else onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val files = entries.filter { selected.contains(it.absolutePath) }
                            onPick(files)
                            onBack()
                        },
                        enabled = selected.isNotEmpty()
                    ) { Text("Add to chat", color = OmniAmber) }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            items(entries, key = { it.absolutePath }) { f ->
                ListItem(
                    headlineContent = { Text(f.name) },
                    supportingContent = { Text(if (f.isDirectory) "Folder" else "${f.length()} B") },
                    leadingContent = {
                        Icon(
                            if (f.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                            null,
                            tint = OmniAmber
                        )
                    },
                    trailingContent = {
                        if (!f.isDirectory) {
                            Checkbox(
                                checked = selected.contains(f.absolutePath),
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + f.absolutePath else selected - f.absolutePath
                                }
                            )
                        }
                    },
                    modifier = Modifier.clickable {
                        if (f.isDirectory) current = f
                        else {
                            selected = if (selected.contains(f.absolutePath)) selected - f.absolutePath
                            else selected + f.absolutePath
                        }
                    }
                )
            }
        }
    }
}
