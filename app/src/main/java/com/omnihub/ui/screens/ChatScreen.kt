package com.omnihub.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import com.omnihub.history.ConversationEntity
import com.omnihub.providers.ChatMessage
import com.omnihub.source.core.ProviderAuthStore
import com.omnihub.source.core.ProviderBridge
import com.omnihub.update.AppUpdateChecker
import com.omnihub.update.AppUpdateInfo
import com.omnihub.ui.theme.OmniAmber
import com.omnihub.ui.theme.OmniGlassBorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

data class ChatBubble(val role: String, val content: String)

private val GREETINGS = listOf("Ready when you are.", "What are we building?", "Let's continue.", "Pick up where you left off.", "Your move.")

private fun timeGreeting(name: String): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val base = when {
        hour < 5 -> "Still up"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        hour < 21 -> "Good evening"
        else -> "Late night"
    }
    val head = if (name.isBlank()) "$base." else "$base, $name."
    return "$head\n${GREETINGS[hour % GREETINGS.size]}"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    onOpenCustomize: () -> Unit,
    onOpenSkills: () -> Unit = {},
    onOpenProjects: () -> Unit = {},
    onOpenSources: () -> Unit = {},
    onOpenStore: () -> Unit = {},
    onOpenConnectors: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as OmniHubApp
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val keyboard = LocalSoftwareKeyboardController.current
    val convPrefs = remember { context.getSharedPreferences("omni_chat_session", 0) }
    var currentConvId by remember { mutableStateOf(convPrefs.getString("active_conv_id", null)) }
    var messages by remember { mutableStateOf(listOf<ChatBubble>()) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var conversations by remember { mutableStateOf(listOf<ConversationEntity>()) }
    var projects by remember { mutableStateOf(listOf<com.omnihub.history.ProjectEntity>()) }
    var incognito by remember { mutableStateOf(UserPrefs.isIncognito(context)) }
    var greeting by remember { mutableStateOf(timeGreeting(UserPrefs.getName(context))) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showProviderSheet by remember { mutableStateOf(false) }
    var pendingAttachments by remember { mutableStateOf(listOf<String>()) }
    var preferred by remember { mutableStateOf(ProviderAuthStore.preferredProvider(context)) }
    var menuConv by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showRename by remember { mutableStateOf(false) }
    var showProjectPicker by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    val listState = rememberLazyListState()
    val sources by app.sourceManager.sources.collectAsState()
    var uiLocked by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) pendingAttachments = pendingAttachments + uris.map { it.toString() }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) pendingAttachments = pendingAttachments + uris.map { it.toString() }
    }

    fun persistActive(id: String?) {
        currentConvId = id
        convPrefs.edit().putString("active_conv_id", id).apply()
    }

    suspend fun loadMessagesFromDb(id: String?) {
        if (uiLocked || sending) return
        if (id == null || incognito) { messages = emptyList(); return }
        val list = withContext(Dispatchers.IO) { app.chatRepo.getMessages(id) }
        if (uiLocked || sending) return
        if (id != currentConvId) return
        messages = list.map { ChatBubble(it.role, it.content) }
    }

    fun openConversation(id: String) {
        if (incognito || sending || uiLocked) return
        persistActive(id)
        scope.launch { loadMessagesFromDb(id); drawerState.close() }
    }

    fun startNewChat() {
        if (sending) return
        messages = emptyList()
        persistActive(null)
        pendingAttachments = emptyList()
        greeting = timeGreeting(UserPrefs.getName(context))
    }

    fun toggleIncognito() {
        if (sending) return
        incognito = !incognito
        UserPrefs.setIncognito(context, incognito)
        if (incognito) startNewChat()
    }

    fun providerLabel(): String {
        if (preferred == "auto") return "Auto"
        return sources.find { it.info.id == preferred }?.info?.name ?: preferred
    }

    LaunchedEffect(Unit) {
        greeting = timeGreeting(UserPrefs.getName(context))
        app.sourceManager.reload()
        loadMessagesFromDb(currentConvId)
        launch { app.chatRepo.observeConversations().collect { conversations = it } }
        launch { app.chatRepo.observeProjects().collect { projects = it } }
        launch { updateInfo = runCatching { AppUpdateChecker.checkOmniHub(context) }.getOrNull() }
    }

    fun send() {
        val text = input.trim()
        if ((text.isBlank() && pendingAttachments.isEmpty()) || sending) return
        val attachNote = if (pendingAttachments.isNotEmpty()) "\n\n[Attached: ${pendingAttachments.size} item(s)]" else ""
        val userText = text + attachNote
        input = ""
        pendingAttachments = emptyList()
        keyboard?.hide()
        sending = true
        uiLocked = true
        val baseline = messages
        messages = baseline + ChatBubble("user", userText) + ChatBubble("assistant", "")

        scope.launch {
            var convId = currentConvId
            try {
                if (!incognito) {
                    if (convId == null) {
                        convId = withContext(Dispatchers.IO) {
                            app.chatRepo.createConversation(userText.take(40).ifBlank { "Chat" })
                        }
                        currentConvId = convId
                        convPrefs.edit().putString("active_conv_id", convId).apply()
                    }
                    withContext(Dispatchers.IO) { app.chatRepo.addMessage(convId!!, "user", userText) }
                }

                val hist = (baseline + ChatBubble("user", userText)).map { ChatMessage(it.role, it.content) }
                val preferredId = if (preferred == "auto") null else preferred
                val allSources = app.sourceManager.all()
                val ordered = buildList {
                    if (preferredId != null) allSources.find { it.info.id == preferredId }?.let { add(it) }
                    allSources.filter { preferredId == null || it.info.id != preferredId }.forEach { add(it) }
                }
                val candidates = ordered.map { Triple(it.info.id, it.info.name, it.info.websiteUrl) }

                val reply: String = if (candidates.isEmpty()) {
                    "No source installed. Open Store → install → Providers → Sign in."
                } else {
                    val buf = StringBuilder()
                    ProviderBridge.streamChatWithFallback(
                        context = context,
                        candidates = candidates,
                        messages = hist,
                        kind = "WEB"
                    ).collect { tok ->
                        if (tok.text.isNotEmpty()) {
                            buf.append(tok.text)
                            messages = baseline + ChatBubble("user", userText) + ChatBubble("assistant", buf.toString())
                        }
                    }
                    buf.toString().ifBlank { "No reply." }
                }

                messages = baseline + ChatBubble("user", userText) + ChatBubble("assistant", reply)
                if (!incognito && convId != null) {
                    withContext(Dispatchers.IO) { app.chatRepo.addMessage(convId, "assistant", reply) }
                }
                ordered.firstOrNull()?.info?.id?.let { id ->
                    runCatching { app.soul.learnFromExchange(id, hist, reply, convId) }
                }
            } catch (e: Exception) {
                val err = e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong."
                messages = baseline + ChatBubble("user", userText) + ChatBubble("assistant", err)
                if (!incognito && convId != null) {
                    runCatching { withContext(Dispatchers.IO) { app.chatRepo.addMessage(convId, "assistant", err) } }
                }
            } finally {
                sending = false
                uiLocked = false
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("OmniHub", Modifier.padding(16.dp), fontWeight = FontWeight.Bold, color = OmniAmber)
                NavigationDrawerItem(icon = { Icon(Icons.Default.Add, null, tint = OmniAmber) }, label = { Text("New chat") }, selected = false, onClick = { startNewChat(); scope.launch { drawerState.close() } })
                NavigationDrawerItem(icon = { Icon(Icons.Outlined.Folder, null, tint = OmniAmber) }, label = { Text("Projects") }, selected = false, onClick = { scope.launch { drawerState.close() }; onOpenProjects() })
                NavigationDrawerItem(icon = { Icon(Icons.Outlined.Store, null, tint = OmniAmber) }, label = { Text("Store") }, selected = false, onClick = { scope.launch { drawerState.close() }; onOpenStore() })
                NavigationDrawerItem(icon = { Icon(Icons.Outlined.Extension, null, tint = OmniAmber) }, label = { Text("Connectors") }, selected = false, onClick = { scope.launch { drawerState.close() }; onOpenConnectors() })
                HorizontalDivider(color = OmniGlassBorder, modifier = Modifier.padding(vertical = 8.dp))
                Text("Chats", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.weight(1f)) {
                    items(conversations, key = { it.id }) { conv ->
                        Row(
                            modifier = Modifier.fillMaxWidth().combinedClickable(
                                onClick = { openConversation(conv.id) },
                                onLongClick = { menuConv = conv; renameText = conv.title }
                            ).background(if (conv.id == currentConvId) OmniAmber.copy(alpha = 0.12f) else Color.Transparent).padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (conv.isPinned) {
                                Icon(Icons.Default.PushPin, null, tint = OmniAmber, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(conv.title.ifBlank { "Chat" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                HorizontalDivider(color = OmniGlassBorder)
                val name = UserPrefs.getName(context).ifBlank { "You" }
                NavigationDrawerItem(
                    icon = { Box(Modifier.size(28.dp).clip(CircleShape).background(OmniAmber), contentAlignment = Alignment.Center) { Text(UserPrefs.getInitials(context), color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold) } },
                    label = { Text(name) }, selected = false, onClick = onOpenSettings
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.ime.union(WindowInsets.systemBars),
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { scope.launch { if (drawerState.isClosed) drawerState.open() else drawerState.close() } }) { Icon(Icons.Default.Menu, "Menu", tint = OmniAmber) }
                            IconButton(onClick = { toggleIncognito() }) { Icon(Icons.Outlined.VisibilityOff, "Incognito", tint = if (incognito) OmniAmber else MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (incognito) Text("Incognito", style = MaterialTheme.typography.labelMedium, color = OmniAmber)
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSources) { Icon(Icons.Outlined.AccountTree, "Providers", tint = OmniAmber) }
                        IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Settings", tint = OmniAmber) }
                    }
                )
            },
            bottomBar = {
                Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.ime).padding(8.dp)) {
                    if (pendingAttachments.isNotEmpty()) Text("${pendingAttachments.size} attached", style = MaterialTheme.typography.labelMedium, color = OmniAmber, modifier = Modifier.padding(horizontal = 8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showAddSheet = true }) { Icon(Icons.Default.Add, "Add", tint = OmniAmber) }
                        OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), placeholder = { Text(if (incognito) "Incognito message" else "Message") }, shape = RoundedCornerShape(24.dp), maxLines = 4)
                        IconButton(onClick = { send() }, enabled = !sending) { Icon(if (sending) Icons.Default.HourglassEmpty else Icons.Default.Send, "Send", tint = OmniAmber) }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                updateInfo?.let { info ->
                    Surface(color = OmniAmber.copy(alpha = 0.2f), modifier = Modifier.fillMaxWidth().clickable { AppUpdateChecker.openReleasePage(context, info) }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Update: ${info.tag}", fontWeight = FontWeight.SemiBold, color = OmniAmber)
                                Text(info.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                            Text("What's new", color = OmniAmber, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    if (messages.isEmpty()) {
                        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(greeting, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), modifier = Modifier.clickable { showProviderSheet = true }) {
                                Text(providerLabel(), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = OmniAmber, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
                                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.clickable { showProviderSheet = true }) {
                                    Text(providerLabel(), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = OmniAmber)
                                }
                            }
                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                                items(messages.size) { idx ->
                                    val msg = messages[idx]
                                    val mine = msg.role == "user"
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                                        Surface(shape = RoundedCornerShape(16.dp), color = if (mine) OmniAmber.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.widthIn(max = 320.dp)) {
                                            Text(msg.content.ifBlank { "…" }, Modifier.padding(12.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    menuConv?.let { conv ->
        AlertDialog(
            onDismissRequest = { menuConv = null },
            title = { Text(conv.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    TextButton(onClick = { renameText = conv.title; showRename = true }) { Text("Rename") }
                    TextButton(onClick = { scope.launch { app.chatRepo.setPinned(conv.id, !conv.isPinned); menuConv = null } }) { Text(if (conv.isPinned) "Unpin" else "Pin") }
                    TextButton(onClick = { showProjectPicker = true }) { Text("Add to project") }
                    TextButton(onClick = { scope.launch { app.chatRepo.deleteConversation(conv.id); if (currentConvId == conv.id) startNewChat(); menuConv = null } }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
                }
            },
            confirmButton = { TextButton(onClick = { menuConv = null }) { Text("Close") } }
        )
    }

    if (showRename && menuConv != null) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { val id = menuConv!!.id; scope.launch { app.chatRepo.renameConversation(id, renameText.ifBlank { "Chat" }); showRename = false; menuConv = null } }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } }
        )
    }

    if (showProjectPicker && menuConv != null) {
        AlertDialog(
            onDismissRequest = { showProjectPicker = false },
            title = { Text("Add to project") },
            text = {
                Column {
                    if (projects.isEmpty()) Text("No projects yet. Create one in Projects.")
                    projects.forEach { p -> TextButton(onClick = { val cid = menuConv!!.id; scope.launch { app.chatRepo.setProject(cid, p.id); showProjectPicker = false; menuConv = null } }) { Text(p.name) } }
                }
            },
            confirmButton = { TextButton(onClick = { showProjectPicker = false }) { Text("Close") } }
        )
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add to chat", fontWeight = FontWeight.Bold)
                Button(onClick = { showAddSheet = false; imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = OmniAmber, contentColor = Color.Black)) { Text("Add image") }
                OutlinedButton(onClick = { showAddSheet = false; filePicker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Add file") }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showProviderSheet) {
        ModalBottomSheet(onDismissRequest = { showProviderSheet = false }) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Provider", fontWeight = FontWeight.Bold)
                if (sources.isEmpty()) Text("Install a source from Store first.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                (listOf("auto" to "Auto") + sources.map { it.info.id to it.info.name }).forEach { (id, label) ->
                    NavigationDrawerItem(label = { Text(label) }, selected = preferred == id, onClick = { preferred = id; ProviderAuthStore.setPreferredProvider(context, id); showProviderSheet = false })
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
