package com.omnihub.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import com.omnihub.ui.theme.OmniAmber
import com.omnihub.ui.theme.OmniGlassBorder
import kotlinx.coroutines.launch
import java.util.Calendar

data class ChatBubble(val role: String, val content: String)

private fun timeGreeting(name: String): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val who = name.ifBlank { "" }
    val base = when {
        hour < 5 -> "Still up"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        hour < 21 -> "Good evening"
        else -> "Late night"
    }
    return if (who.isBlank()) "$base." else "$base, $who."
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    onOpenCustomize: () -> Unit,
    onOpenSkills: () -> Unit = {},
    onOpenProjects: () -> Unit = {},
    onOpenSources: () -> Unit = {}
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
    var incognito by remember { mutableStateOf(UserPrefs.isIncognito(context)) }
    var greeting by remember { mutableStateOf(timeGreeting(UserPrefs.getName(context))) }
    var showAddSheet by remember { mutableStateOf(false) }
    var pendingAttachments by remember { mutableStateOf(listOf<String>()) }
    val listState = rememberLazyListState()

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            pendingAttachments = pendingAttachments + uris.map { it.toString() }
        }
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            pendingAttachments = pendingAttachments + uris.map { it.toString() }
        }
    }

    fun persistActive(id: String?) {
        currentConvId = id
        convPrefs.edit().putString("active_conv_id", id).apply()
    }

    fun startNewChat() {
        messages = emptyList()
        persistActive(null)
        pendingAttachments = emptyList()
        greeting = timeGreeting(UserPrefs.getName(context))
    }

    fun toggleIncognito() {
        incognito = !incognito
        UserPrefs.setIncognito(context, incognito)
        if (incognito) startNewChat()
    }

    LaunchedEffect(Unit) {
        app.chatRepo.observeConversations().collect { conversations = it }
    }

    LaunchedEffect(currentConvId) {
        val id = currentConvId
        if (id != null && !incognito) {
            messages = app.chatRepo.getMessages(id).map { ChatBubble(it.role, it.content) }
        }
    }

    fun send() {
        val text = input.trim()
        if ((text.isBlank() && pendingAttachments.isEmpty()) || sending) return
        val attachNote = if (pendingAttachments.isNotEmpty()) {
            "\n\n[Attached: ${pendingAttachments.size} item(s)]"
        } else ""
        val userText = text + attachNote
        input = ""
        pendingAttachments = emptyList()
        keyboard?.hide()
        messages = messages + ChatBubble("user", userText)
        sending = true
        scope.launch {
            try {
                var convId = currentConvId
                if (!incognito) {
                    if (convId == null) {
                        convId = app.chatRepo.createConversation(userText.take(40).ifBlank { "Chat" })
                        persistActive(convId)
                    }
                    app.chatRepo.addMessage(convId!!, "user", userText)
                }
                val hist = messages.map { ChatMessage(it.role, it.content) }
                val routed = app.sourceRouter.chat(hist, conversationId = convId)
                val reply = routed.response.content
                messages = messages + ChatBubble("assistant", reply)
                if (!incognito && convId != null) {
                    app.chatRepo.addMessage(convId, "assistant", reply)
                }
            } catch (e: Exception) {
                messages = messages + ChatBubble("assistant", e.message ?: "Something went wrong.")
            } finally {
                sending = false
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Chats", Modifier.padding(16.dp), fontWeight = FontWeight.Bold, color = OmniAmber)
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Add, null, tint = OmniAmber) },
                    label = { Text("New chat") },
                    selected = false,
                    onClick = {
                        startNewChat()
                        scope.launch { drawerState.close() }
                    }
                )
                HorizontalDivider(color = OmniGlassBorder)
                LazyColumn(Modifier.weight(1f)) {
                    items(conversations, key = { it.id }) { conv ->
                        NavigationDrawerItem(
                            label = {
                                Text(conv.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            selected = conv.id == currentConvId,
                            onClick = {
                                if (!incognito) {
                                    persistActive(conv.id)
                                    scope.launch { drawerState.close() }
                                }
                            }
                        )
                    }
                }
                HorizontalDivider(color = OmniGlassBorder)
                val name = UserPrefs.getName(context).ifBlank { "You" }
                NavigationDrawerItem(
                    icon = {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(OmniAmber),
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
                            IconButton(onClick = {
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            }) {
                                Icon(Icons.Default.Menu, "History", tint = OmniAmber)
                            }
                            IconButton(onClick = { toggleIncognito() }) {
                                Icon(
                                    Icons.Outlined.VisibilityOff,
                                    "Incognito",
                                    tint = if (incognito) OmniAmber else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (incognito) {
                                Text("Incognito", style = MaterialTheme.typography.labelMedium, color = OmniAmber)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSources) {
                            Icon(Icons.Outlined.AccountTree, "Sources", tint = OmniAmber)
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, "Settings", tint = OmniAmber)
                        }
                    }
                )
            },
            bottomBar = {
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    if (pendingAttachments.isNotEmpty()) {
                        Text(
                            "${pendingAttachments.size} attached",
                            style = MaterialTheme.typography.labelMedium,
                            color = OmniAmber,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showAddSheet = true }) {
                            Icon(Icons.Default.Add, "Add", tint = OmniAmber)
                        }
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(if (incognito) "Incognito message" else "Message") },
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 4
                        )
                        IconButton(onClick = { send() }, enabled = !sending) {
                            Icon(
                                if (sending) Icons.Default.HourglassEmpty else Icons.Default.Send,
                                "Send",
                                tint = OmniAmber
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (messages.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            greeting,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(messages) { msg ->
                            val mine = msg.role == "user"
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (mine) OmniAmber.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.widthIn(max = 320.dp)
                                ) {
                                    Text(msg.content, Modifier.padding(12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add to chat", fontWeight = FontWeight.Bold)
                Button(
                    onClick = {
                        showAddSheet = false
                        imagePicker.launch("image/*")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = OmniAmber, contentColor = Color.Black)
                ) { Text("Add image") }
                OutlinedButton(
                    onClick = {
                        showAddSheet = false
                        filePicker.launch(arrayOf("*/*"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add file") }
                if (pendingAttachments.isNotEmpty()) {
                    Text("${pendingAttachments.size} selected — send when ready", color = OmniAmber)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
