package com.omnihub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnihub.OmniHubApp
import com.omnihub.ui.theme.OmniAmber
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as OmniHubApp
    val scope = rememberCoroutineScope()
    val projects by app.chatRepo.observeProjects().collectAsState(initial = emptyList())
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var editingMemoryId by remember { mutableStateOf<String?>(null) }
    var memoryText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projects", fontWeight = FontWeight.SemiBold, color = OmniAmber) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreate = true }) {
                        Icon(Icons.Default.Add, "New project", tint = OmniAmber)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "A project is shared memory. Chats inside a project inject that memory before every request.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (projects.isEmpty()) {
                item {
                    Text("No projects yet. Tap + to create one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(projects, key = { it.id }) { p ->
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, null, tint = OmniAmber)
                            Spacer(Modifier.width(10.dp))
                            Text(p.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { scope.launch { app.chatRepo.deleteProject(p.id) } }) {
                                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Text(
                            if (p.sharedMemory.isBlank()) "No shared memory yet"
                            else p.sharedMemory.take(120) + if (p.sharedMemory.length > 120) "\u2026" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = {
                            editingMemoryId = p.id
                            memoryText = p.sharedMemory
                        }) { Text("Edit shared memory") }
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New project") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        app.chatRepo.createProject(name.ifBlank { "Project" })
                        name = ""
                        showCreate = false
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }

    editingMemoryId?.let { id ->
        AlertDialog(
            onDismissRequest = { editingMemoryId = null },
            title = { Text("Shared memory") },
            text = {
                OutlinedTextField(
                    value = memoryText,
                    onValueChange = { memoryText = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    label = { Text("Injected into every chat in this project") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        app.chatRepo.updateProjectMemory(id, memoryText)
                        editingMemoryId = null
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingMemoryId = null }) { Text("Cancel") } }
        )
    }
}
