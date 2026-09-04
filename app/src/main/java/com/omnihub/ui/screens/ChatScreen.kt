package com.omnihub.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnihub.OmniHubApp
import com.omnihub.data.UserPrefs
import com.omnihub.providers.ChatMessage
import com.omnihub.providers.ChatRequest
import com.omnihub.providers.impl.ProviderBootstrap
import com.omnihub.ui.WebLoginActivity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    onOpenCustomize: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as OmniHubApp
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var messageText by remember { mutableStateOf("") }
    var isTemporary by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showWebSessionSheet by remember { mutableStateOf(false) }
    val history = remember {
        listOf(
            "Building Payworth website frontend",
            "Portfolio website UI design specs",
            "Building relay messaging app"
        )
    }
    val messages = remember { mutableStateListOf<ChatBubble>() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            OmniDrawer(
                history = history,
                onNewChat = {
                    messages.clear()
                    isTemporary = false
                    scope.launch { drawerState.close() }
                },
                onSelect = { scope.launch { drawerState.close() } },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    onOpenSettings()
                },
                onOpenMcp = {
                    scope.launch { drawerState.close() }
                    onOpenCustomize()
                }
            )
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (isTemporary) "Temporary" else "OmniHub",
                            fontWeight = FontWeight.Medium,
                            fontSize = 17.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isTemporary = !isTemporary }) {
                            Icon(
                                if (isTemporary) Icons.Filled.Timer else Icons.Outlined.Timer,
                                "Temporary",
                                tint = if (isTemporary) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { messages.clear(); isTemporary = false }) {
                            Icon(Icons.Outlined.Edit, "New chat")
                        }
                    }
                )
            },
            bottomBar = {
                InputBar(
                    value = messageText,
                    onValueChange = { messageText = it },
                    onPlusClick = { showAddSheet = true },
                    onSend = {
                        if (messageText.isNotBlank()) {
                            val prompt = messageText
                            messageText = ""
                            messages.add(ChatBubble("user", prompt))
                            val pendingIndex = messages.size
                            messages.add(ChatBubble("assistant", "Thinking…"))
                            scope.launch {
                                try {
                                    if (!app.registry.hasAny()) {
                                        messages[pendingIndex] = ChatBubble(
                                            "assistant",
                                            "No API key yet. Tap + and paste a provider key (OpenAI, Groq, Gemini, OpenRouter…). Web login opens the provider site so you can copy an official key — chat does not hijack ChatGPT/Claude cookies."
                                        )
                                    } else {
                                        val hist = messages
                                            .filter { !(it.role == "assistant" && it.content.startsWith("Thinking")) }
                                            .map { ChatMessage(it.role, it.content) }
                                            .takeLast(16)
                                        val resp = app.router.chatWithFallback(
                                            ChatRequest(model = "", messages = hist)
                                        )
                                        messages[pendingIndex] = ChatBubble("assistant", resp.content)
                                    }
                                } catch (e: Exception) {
                                    messages[pendingIndex] = ChatBubble(
                                        "assistant",
                                        e.message ?: "All providers failed. Add another API key in Settings."
                                    )
                                }
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (messages.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("✦", fontSize = 42.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(20.dp))
                        Text("How can I help you this evening?", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    }
                } else {
                    LazyColumn(
                        state = rememberLazyListState(),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        items(messages) { MessageBubble(it) }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddProviderSheet(
            onDismiss = { showAddSheet = false },
            onOpenWebSession = { showAddSheet = false; showWebSessionSheet = true },
            onSaved = { showAddSheet = false }
        )
    }
    if (showWebSessionSheet) {
        WebSessionSearchSheet(onDismiss = { showWebSessionSheet = false })
    }
}

@Composable
private fun OmniDrawer(
    history: List<String>,
    onNewChat: () -> Unit,
    onSelect: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMcp: () -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.width(300.dp), drawerContainerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            Text("OmniHub", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp))
            DrawerItem(Icons.Outlined.Edit, "New chat", onNewChat, accent = true)
            DrawerItem(Icons.Outlined.ChatBubbleOutline, "Chats", {})
            DrawerItem(Icons.Outlined.Folder, "Projects", {})
            DrawerItem(Icons.Outlined.Extension, "MCP Servers", onOpenMcp)
            HorizontalDivider(Modifier.padding(vertical = 12.dp, horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Recents", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(history) { title ->
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().clickable { onSelect(title) }.padding(horizontal = 20.dp, vertical = 12.dp))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpenSettings).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF7C3AED)), contentAlignment = Alignment.Center) {
                    Text(UserPrefs.getInitials(LocalContext.current), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    val ctx = LocalContext.current
                    Text(UserPrefs.getName(ctx).ifBlank { "User" }, fontWeight = FontWeight.Medium)
                    Text("Free", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DrawerItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, accent: Boolean = false) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = if (accent) FontWeight.Medium else FontWeight.Normal)
    }
}

data class ChatBubble(val role: String, val content: String)

@Composable
private fun MessageBubble(bubble: ChatBubble) {
    val isUser = bubble.role == "user"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Surface(shape = RoundedCornerShape(18.dp), color = if (isUser) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent) {
            Text(bubble.content, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun InputBar(value: String, onValueChange: (String) -> Unit, onPlusClick: () -> Unit, onSend: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPlusClick, modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Icon(Icons.Default.Add, "Add", Modifier.size(22.dp))
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message OmniHub…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant),
                maxLines = 4,
                trailingIcon = {
                    if (value.isNotBlank()) {
                        IconButton(onClick = onSend) { Icon(Icons.Default.ArrowUpward, "Send", tint = MaterialTheme.colorScheme.primary) }
                    }
                }
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = {}, modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Icon(Icons.Default.Mic, "Voice", Modifier.size(22.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProviderSheet(onDismiss: () -> Unit, onOpenWebSession: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as OmniHubApp
    var apiKey by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Add AI Provider", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text("API Key") }, placeholder = { Text("sk-… or AIza…") }, singleLine = true, shape = RoundedCornerShape(12.dp))
            Button(
                onClick = {
                    try {
                        ProviderBootstrap.saveAndRegister(context, app.registry, apiKey)
                        error = null
                        onSaved()
                    } catch (e: Exception) {
                        error = e.message
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = apiKey.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Save & chat") }
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            HorizontalDivider()
            Text("Open a provider site", style = MaterialTheme.typography.titleMedium)
            Text("Sign in to copy an official API key. Cookie replay of ChatGPT/Claude web is not supported.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onOpenWebSession, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Language, null)
                Spacer(Modifier.width(8.dp))
                Text("Web Sessions")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebSessionSearchSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val catalog = listOf(
        "Google AI Studio" to "https://aistudio.google.com",
        "Gemini" to "https://gemini.google.com",
        "OpenRouter" to "https://openrouter.ai",
        "Groq" to "https://console.groq.com",
        "OpenAI" to "https://platform.openai.com/api-keys",
        "DeepSeek" to "https://platform.deepseek.com",
        "Mistral" to "https://console.mistral.ai"
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Provider sites", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Opens the official site so you can copy an API key.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Search") }, singleLine = true, shape = RoundedCornerShape(12.dp))
            Button(onClick = { results = catalog.filter { it.first.contains(query, true) || it.second.contains(query, true) } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("Search") }
            results.forEach { (name, url) ->
                Card(onClick = {
                    val intent = Intent(context, WebLoginActivity::class.java)
                    intent.putExtra(WebLoginActivity.EXTRA_URL, url)
                    intent.putExtra(WebLoginActivity.EXTRA_TITLE, name)
                    context.startActivity(intent)
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(name, fontWeight = FontWeight.Medium)
                        Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
