package com.omnihub.ui.screens

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.omnihub.OmniHubApp
import com.omnihub.data.UserPrefs
import com.omnihub.history.ConversationEntity
import com.omnihub.providers.ChatRequest
import com.omnihub.providers.websession.WebSessionManager
import com.omnihub.ui.WebLoginActivity
import com.omnihub.ui.theme.OmniAmber
import com.omnihub.ui.theme.OmniGlass
import com.omnihub.ui.theme.OmniGlassBorder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class ChatBubble(val role: String, val content: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    onOpenCustomize: () -> Unit,
    onOpenSkills: () -> Unit = {},
    onOpenProjects: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as OmniHubApp
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val keyboard = LocalSoftwareKeyboardController.current

    var currentConvId by remember { mutableStateOf<String?>(null) }
    var activeProjectId by remember { mutableStateOf<String?>(null) }
    var isTemporary by remember { mutableStateOf(false) }
    var messageText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showWebSessionSheet by remember { mutableStateOf(false) }
    var webSearchQuery by remember { mutableStateOf("") }
    var menuConv by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showProjectPicker by remember { mutableStateOf<ConversationEntity?>(null) }

    val conversations by app.chatRepo.observeConversations().collectAsState(initial = emptyList())
    val projects by app.chatRepo.observeProjects().collectAsState(initial = emptyList())
    val messages = remember { mutableStateListOf<ChatBubble>() }
    val listState = rememberLazyListState()

    LaunchedEffect(currentConvId) {
        messages.clear()
        val id = currentConvId ?: return@LaunchedEffect
        app.chatRepo.observeMessages(id).collectLatest { list ->
            messages.clear()
            messages.addAll(list.map { ChatBubble(it.role, it.content) })
            if (list.isNotEmpty()) listState.animateScrollToItem(list.lastIndex)
        }
    }

    fun startNewChat(temporary: Boolean = false) {
        scope.launch {
            val id = app.chatRepo.createConversation(
                title = if (temporary) "Temporary" else "New chat",
                temporary = temporary,
                projectId = activeProjectId,
                reuseEmptyId = if (!temporary) currentConvId else null
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
                drawerContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
            ) {
                Spacer(Modifier.height(12.dp))
                Text("OmniHub", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = OmniAmber)
                NavigationDrawerItem(icon = { Icon(Icons.Default.Add, null, tint = OmniAmber) },
                    label = { Text("New chat") }, selected = false, onClick = { startNewChat(false) })
                NavigationDrawerItem(icon = { Icon(Icons.Outlined.Folder, null, tint = OmniAmber) },
                    label = { Text("Projects") }, selected = false, onClick = {
                        scope.launch { drawerState.close() }; onOpenProjects()
                    })
                NavigationDrawerItem(icon = { Icon(Icons.Outlined.AutoAwesome, null, tint = OmniAmber) },
                    label = { Text("Skills") }, selected = false, onClick = {
                        scope.launch { drawerState.close() }; onOpenSkills()
                    })
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = OmniGlassBorder)
                Text("History", style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (conversations.isEmpty()) {
                    Text("No conversations yet", modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(conversations, key = { it.id }) { conv ->
                            Row(
                                Modifier.fillMaxWidth().combinedClickable(
                                    onClick = {
                                        currentConvId = conv.id
                                        activeProjectId = conv.projectId
                                        isTemporary = false
                                        scope.launch { drawerState.close() }
                                    },
                                    onLongClick = { menuConv = conv }
                                ).padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (conv.isPinned) {
                                    Icon(Icons.Default.PushPin, null, tint = OmniAmber, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(conv.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = if (conv.id == currentConvId) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (conv.id == currentConvId) OmniAmber else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
                HorizontalDivider(color = OmniGlassBorder)
                val name = UserPrefs.getName(context).ifBlank { "User" }
                NavigationDrawerItem(
                    icon = {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(OmniAmber), contentAlignment = Alignment.Center) {
                            Text(UserPrefs.getInitials(context), color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    label = { Text(name) }, selected = false, onClick = onOpenSettings
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.AccountTree, null, tint = OmniAmber) },
                    label = { Text("Connected AI services") }, selected = false, onClick = onOpenSettings
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { scope.launch { if (drawerState.isClosed) drawerState.open() else drawerState.close() } }) {
                                Icon(Icons.Default.Menu, "History", tint = OmniAmber)
                            }
                            IconButton(onClick = { startNewChat(true) }) {
                                Icon(Icons.Outlined.ChatBubbleOutline, "Temporary", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { startNewChat(false) }) {
                                Icon(Icons.Default.Add, "New chat", tint = OmniAmber)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSkills) { Icon(Icons.Outlined.AutoAwesome, "Skills", tint = OmniAmber) }
                        IconButton(onClick = onOpenCustomize) { Icon(Icons.Outlined.Tune, "Customize") }
                        IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            bottomBar = {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
                    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), tonalElevation = 2.dp) {
                        Row(Modifier.fillMaxWidth().padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showAddSheet = true }, enabled = !isSending,
                                modifier = Modifier.clip(CircleShape).background(OmniGlass).border(1.dp, OmniGlassBorder, CircleShape)) {
                                Icon(Icons.Default.Add, "Add", tint = OmniAmber)
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = messageText, onValueChange = { messageText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Message OmniHub\u2026") }, maxLines = 5, enabled = !isSending,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = OmniAmber, unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = OmniGlass, unfocusedContainerColor = OmniGlass
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    val prompt = messageText.trim()
                                    if (prompt.isBlank() || isSending) return@IconButton
                                    messageText = ""
                                    keyboard?.hide()
                                    isSending = true
                                    scope.launch {
                                        try {
                                            var convId = currentConvId
                                            if (convId == null) {
                                                convId = app.chatRepo.createConversation(prompt.take(60), isTemporary, activeProjectId)
                                                currentConvId = convId
                                            }
                                            app.chatRepo.addMessage(convId, "user", prompt)
                                            messages.add(ChatBubble("user", prompt))
                                            messages.add(ChatBubble("assistant", "Thinking\u2026"))
                                            val idx = messages.lastIndex
                                            if (!app.registry.hasAny()) {
                                                messages[idx] = ChatBubble("assistant",
                                                    "No API key registered.\n\nTap + \u2192 paste sk- / gsk_ / AIza / sk-ant key.\nWeb Sessions store cookies; chat replies need a real API key.")
                                            } else {
                                                val hist = app.chatRepo.getContextMessages(convId, 20)
                                                val resp = app.router.chatWithFallback(ChatRequest("", hist))
                                                messages[idx] = ChatBubble("assistant", resp.content)
                                                app.chatRepo.addMessage(convId, "assistant", resp.content, resp.model, resp.providerId)
                                            }
                                        } catch (e: Exception) {
                                            if (messages.isNotEmpty() && messages.last().content.startsWith("Thinking")) {
                                                messages[messages.lastIndex] = ChatBubble("assistant", e.message ?: "Providers failed")
                                            }
                                        } finally { isSending = false }
                                    }
                                },
                                enabled = !isSending && messageText.isNotBlank(),
                                modifier = Modifier.clip(CircleShape).background(if (messageText.isNotBlank()) OmniAmber else OmniGlass)
                            ) {
                                Icon(Icons.Default.Send, "Send", tint = if (messageText.isNotBlank()) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (messages.isEmpty()) {
                    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Box(Modifier.size(96.dp).clip(CircleShape).background(Brush.radialGradient(listOf(OmniAmber.copy(0.35f), Color.Transparent)))
                            .border(1.5.dp, OmniGlassBorder, CircleShape), contentAlignment = Alignment.Center) {
                            Text("\uD83E\uDD16", fontSize = 42.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("How can I help you?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Text("Tap + \u2192 paste an API key to chat.\nAmber hub \u00b7 glass bubbles \u00b7 one active chat.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(messages) { bubble ->
                            val isUser = bubble.role == "user"
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                                Box(Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(20.dp))
                                    .background(if (isUser) Brush.linearGradient(listOf(OmniAmber.copy(0.35f), OmniAmber.copy(0.18f)))
                                    else Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x14FFFFFF))))
                                    .border(1.dp, if (isUser) OmniGlassBorder else Color(0x22FFFFFF), RoundedCornerShape(20.dp))
                                    .padding(14.dp)) {
                                    Text(bubble.content, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    menuConv?.let { conv ->
        Popup(onDismissRequest = { menuConv = null }, properties = PopupProperties(focusable = true)) {
            Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 8.dp, shadowElevation = 12.dp,
                modifier = Modifier.padding(16.dp).border(1.dp, OmniGlassBorder, RoundedCornerShape(14.dp))) {
                Column(Modifier.width(200.dp).padding(vertical = 8.dp)) {
                    listOf(
                        Triple("Rename", Icons.Default.Edit as androidx.compose.ui.graphics.vector.ImageVector, {
                            renameText = conv.title; renameTarget = conv; menuConv = null
                        }),
                        Triple(if (conv.isPinned) "Unpin" else "Pin", Icons.Default.PushPin, {
                            scope.launch { app.chatRepo.setPinned(conv.id, !conv.isPinned) }; menuConv = null
                        }),
                        Triple("Add to project", Icons.Default.Folder, {
                            showProjectPicker = conv; menuConv = null
                        }),
                        Triple("Delete", Icons.Default.Delete, {
                            scope.launch {
                                app.chatRepo.deleteConversation(conv.id)
                                if (currentConvId == conv.id) { currentConvId = null; messages.clear() }
                            }; menuConv = null
                        })
                    ).forEach { (label, icon, action) ->
                        Row(Modifier.fillMaxWidth().clickable { action() }.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null, tint = if (label == "Delete") MaterialTheme.colorScheme.error else OmniAmber, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(label, color = if (label == "Delete") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { conv ->
        AlertDialog(onDismissRequest = { renameTarget = null }, title = { Text("Rename") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { scope.launch { app.chatRepo.renameConversation(conv.id, renameText.ifBlank { conv.title }); renameTarget = null } }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } })
    }

    showProjectPicker?.let { conv ->
        AlertDialog(onDismissRequest = { showProjectPicker = null }, title = { Text("Add to project") },
            text = {
                Column {
                    if (projects.isEmpty()) Text("No projects yet. Open Projects from the drawer.")
                    else projects.forEach { p ->
                        Text(p.name, modifier = Modifier.fillMaxWidth().clickable {
                            scope.launch { app.chatRepo.setProject(conv.id, p.id); showProjectPicker = null }
                        }.padding(vertical = 10.dp), color = OmniAmber)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showProjectPicker = null }) { Text("Close") } })
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            AddProviderSheet(onDismiss = { showAddSheet = false }, onSaved = { showAddSheet = false; app.reloadProviders() },
                onOpenWebSessions = { showAddSheet = false; showWebSessionSheet = true })
        }
    }
    if (showWebSessionSheet) {
        ModalBottomSheet(onDismissRequest = { showWebSessionSheet = false }) {
            WebSessionSearchSheet(query = webSearchQuery, onQueryChange = { webSearchQuery = it }, onSelect = { site ->
                showWebSessionSheet = false
                context.startActivity(Intent(context, WebLoginActivity::class.java).apply {
                    putExtra(WebLoginActivity.EXTRA_URL, site.url)
                    putExtra(WebLoginActivity.EXTRA_TITLE, site.name)
                })
            })
        }
    }
}

@Composable
fun AddProviderSheet(onDismiss: () -> Unit, onSaved: () -> Unit, onOpenWebSessions: () -> Unit) {
    val context = LocalContext.current
    var key by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as OmniHubApp
    Column(Modifier.padding(24.dp)) {
        Text("Add Provider", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = OmniAmber)
        Spacer(Modifier.height(8.dp))
        Text("API keys make chat reply. Web Sessions store cookies for tools.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("API Key") },
            placeholder = { Text("sk-\u2026 / gsk_\u2026 / AIza\u2026 / sk-ant-\u2026") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            if (key.isBlank()) return@Button
            scope.launch {
                try {
                    com.omnihub.providers.impl.ProviderBootstrap.saveAndRegister(context, app.registry, key)
                    status = "Saved. Send a message."
                    onSaved()
                } catch (e: Exception) { status = e.message }
            }
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = OmniAmber, contentColor = Color.Black)) {
            Text("Save API key & chat")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onOpenWebSessions, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Language, null, tint = OmniAmber); Spacer(Modifier.width(8.dp)); Text("Web Sessions")
        }
        status?.let { Text(it, color = OmniAmber, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun WebSessionSearchSheet(query: String, onQueryChange: (String) -> Unit, onSelect: (WebSessionManager.Site) -> Unit) {
    val context = LocalContext.current
    val manager = remember { (context.applicationContext as OmniHubApp).webSessions }
    val results = remember(query) { manager.search(query) }
    Column(Modifier.padding(24.dp)) {
        Text("Web Sessions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = OmniAmber)
        Spacer(Modifier.height(8.dp))
        Text("Cookies stored encrypted. For chat, paste an API key from the dashboard.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = query, onValueChange = onQueryChange, label = { Text("Search") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(12.dp))
        LazyColumn {
            items(results) { site ->
                ListItem(headlineContent = { Text(site.name) }, supportingContent = { Text(site.url) },
                    modifier = Modifier.clickable { onSelect(site) })
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
