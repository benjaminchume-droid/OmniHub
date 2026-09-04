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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnihub.OmniHubApp
import com.omnihub.data.UserPrefs
import com.omnihub.providers.ChatMessage
import com.omnihub.providers.ChatRequest
import com.omnihub.providers.websession.WebSessionManager
import com.omnihub.ui.WebLoginActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class ChatBubble(val role: String, val content: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    onOpenCustomize: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as OmniHubApp
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val keyboard = LocalSoftwareKeyboardController.current

    var currentConvId by remember { mutableStateOf<String?>(null) }
    var isTemporary by remember { mutableStateOf(false) }
    var messageText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showWebSessionSheet by remember { mutableStateOf(false) }
    var webSearchQuery by remember { mutableStateOf("") }

    val conversations by app.chatRepo.observeConversations().collectAsState(initial = emptyList())
    val messages = remember { mutableStateListOf<ChatBubble>() }
    val listState = rememberLazyListState()

    LaunchedEffect(currentConvId) {
        messages.clear()
        val id = currentConvId ?: return@LaunchedEffect
        app.chatRepo.observeMessages(id).collectLatest { list ->
            messages.clear()
            messages.addAll(list.map { ChatBubble(it.role, it.content) })
            if (list.isNotEmpty()) {
                listState.animateScrollToItem(list.lastIndex)
            }
        }
    }

    fun startNewChat(temporary: Boolean = false) {
        scope.launch {
            app.chatRepo.clearTemporary()
            val id = app.chatRepo.createConversation(
                title = if (temporary) "Temporary" else "New chat",
                temporary = temporary
            )
            currentConvId = id
            isTemporary = temporary
            messages.clear()
            drawerState.close()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "OmniHub",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Add, null) },
                    label = { Text("New chat") },
                    selected = false,
                    onClick = { startNewChat(false) }
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    "History",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (conversations.isEmpty()) {
                    Text(
                        "No conversations yet",
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(conversations, key = { it.id }) { conv ->
                            NavigationDrawerItem(
                                label = {
                                    Text(
                                        conv.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                selected = conv.id == currentConvId,
                                onClick = {
                                    currentConvId = conv.id
                                    isTemporary = false
                                    scope.launch { drawerState.close() }
                                }
                            )
                        }
                    }
                }
                HorizontalDivider()
                val name = UserPrefs.getName(context).ifBlank { "User" }
                NavigationDrawerItem(
                    icon = {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                UserPrefs.getInitials(context),
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    label = { Text(name) },
                    selected = false,
                    onClick = onOpenSettings
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.AccountTree, null) },
                    label = { Text("Connected AI services") },
                    selected = false,
                    onClick = onOpenSettings
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            }) {
                                Icon(Icons.Default.Menu, contentDescription = "History")
                            }
                            IconButton(onClick = { startNewChat(temporary = true) }) {
                                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Temporary chat")
                            }
                            IconButton(onClick = { startNewChat(false) }) {
                                Icon(Icons.Default.Add, contentDescription = "New chat")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenCustomize) {
                            Icon(Icons.Outlined.Tune, contentDescription = "Customize")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                ) {
                    InputBar(
                        value = messageText,
                        onValueChange = { messageText = it },
                        onPlusClick = { showAddSheet = true },
                        enabled = !isSending,
                        onSend = {
                            val prompt = messageText.trim()
                            if (prompt.isBlank() || isSending) return@InputBar
                            messageText = ""
                            keyboard?.hide()
                            isSending = true
                            scope.launch {
                                try {
                                    var convId = currentConvId
                                    if (convId == null) {
                                        convId = app.chatRepo.createConversation(
                                            title = prompt.take(60),
                                            temporary = isTemporary
                                        )
                                        currentConvId = convId
                                    }
                                    app.chatRepo.addMessage(convId, "user", prompt)
                                    messages.add(ChatBubble("user", prompt))
                                    messages.add(ChatBubble("assistant", "Thinking\u2026"))
                                    val pendingIndex = messages.lastIndex

                                    if (!app.registry.hasAny()) {
                                        messages[pendingIndex] = ChatBubble(
                                            "assistant",
                                            "No provider connected yet.\n\nTap + \u2192 Web Sessions (recommended) to sign in and capture a session, or paste an API key."
                                        )
                                    } else {
                                        val hist = app.chatRepo.getRecentAsChatMessages(convId, 20)
                                        val resp = app.router.chatWithFallback(
                                            ChatRequest(model = "", messages = hist)
                                        )
                                        messages[pendingIndex] = ChatBubble("assistant", resp.content)
                                        app.chatRepo.addMessage(
                                            convId,
                                            "assistant",
                                            resp.content,
                                            model = resp.model,
                                            providerId = resp.providerId
                                        )
                                    }
                                } catch (e: Exception) {
                                    if (messages.isNotEmpty() && messages.last().content.startsWith("Thinking")) {
                                        messages[messages.lastIndex] = ChatBubble(
                                            "assistant",
                                            e.message ?: "All providers failed. Add another key or web session."
                                        )
                                    }
                                } finally {
                                    isSending = false
                                }
                            }
                        }
                    )
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (messages.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("\u2726", fontSize = 48.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "How can I help you?",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tap + \u2192 Web Sessions to sign in.\nCookies are captured automatically.\nOr paste an API key.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(messages) { bubble -> MessageBubble(bubble) }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            AddProviderSheet(
                onDismiss = { showAddSheet = false },
                onSaved = {
                    showAddSheet = false
                    app.reloadProviders()
                },
                onOpenWebSessions = {
                    showAddSheet = false
                    showWebSessionSheet = true
                }
            )
        }
    }

    if (showWebSessionSheet) {
        ModalBottomSheet(onDismissRequest = { showWebSessionSheet = false }) {
            WebSessionSearchSheet(
                query = webSearchQuery,
                onQueryChange = { webSearchQuery = it },
                onSelect = { site ->
                    showWebSessionSheet = false
                    val intent = Intent(context, WebLoginActivity::class.java).apply {
                        putExtra(WebLoginActivity.EXTRA_URL, site.url)
                        putExtra(WebLoginActivity.EXTRA_TITLE, site.name)
                    }
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
private fun MessageBubble(bubble: ChatBubble) {
    val isUser = bubble.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                bubble.content,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onPlusClick: () -> Unit,
    onSend: () -> Unit,
    enabled: Boolean = true
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPlusClick, enabled = enabled) {
                Icon(Icons.Default.Add, contentDescription = "Add provider")
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message OmniHub\u2026") },
                maxLines = 5,
                enabled = enabled,
                shape = RoundedCornerShape(24.dp)
            )
            IconButton(
                onClick = onSend,
                enabled = enabled && value.isNotBlank()
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
fun AddProviderSheet(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onOpenWebSessions: () -> Unit
) {
    val context = LocalContext.current
    var key by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as OmniHubApp

    Column(Modifier.padding(24.dp)) {
        Text("Add Provider", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Web Sessions is the main path \u2014 sign in once, cookies are captured automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenWebSessions, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Language, null)
            Spacer(Modifier.width(8.dp))
            Text("Web Sessions (recommended)")
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text("Or paste an API key", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("API Key") },
            placeholder = { Text("sk-\u2026 / gsk_\u2026 / AIza\u2026 / sk-or-\u2026") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                if (key.isBlank()) return@OutlinedButton
                scope.launch {
                    try {
                        com.omnihub.providers.impl.ProviderBootstrap.saveAndRegister(
                            context, app.registry, key
                        )
                        status = "Saved. You can chat now."
                        onSaved()
                    } catch (e: Exception) {
                        status = e.message
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save API key") }
        status?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun WebSessionSearchSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (WebSessionManager.Site) -> Unit
) {
    val context = LocalContext.current
    val manager = remember { (context.applicationContext as OmniHubApp).webSessions }
    val results = remember(query) { manager.search(query) }

    Column(Modifier.padding(24.dp)) {
        Text("Web Sessions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Search \u2192 open site \u2192 sign in. OmniHub captures cookies \u0026 tokens automatically. No copy-paste.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search (gemini, openai, kimi, grok\u2026)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn {
            items(results) { site ->
                ListItem(
                    headlineContent = { Text(site.name) },
                    supportingContent = { Text(site.url) },
                    modifier = Modifier.clickable { onSelect(site) }
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
