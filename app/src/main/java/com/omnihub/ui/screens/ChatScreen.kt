package com.omnihub.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    onOpenCustomize: () -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var messageText by remember { mutableStateOf("") }
    var isTemporary by remember { mutableStateOf(false) }

    val historyItems = remember {
        listOf(
            "How to build an MCP client",
            "Explain soul.md compression",
            "Best free AI providers 2026",
            "Android VoiceInteractionService",
            "Compare Claude vs Grok routing"
        )
    }
    val messages = remember {
        mutableStateListOf(
            ChatBubble("user", "Hey OmniHub, what can you do?"),
            ChatBubble("assistant", "I’m your universal AI hub. I route across dozens of providers (API keys + web sessions), keep a living soul.md memory, support MCP servers you paste in, and can become your phone’s default assistant.\n\nSwipe left for history, hit Customize for Skills / Behavior / Tone / MCP.")
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            HistoryDrawer(
                history = historyItems,
                onNewChat = {
                    messages.clear()
                    isTemporary = false
                    scope.launch { drawerState.close() }
                },
                onSelectHistory = { scope.launch { drawerState.close() } },
                onOpenAccount = { },
                onAddService = { }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (isTemporary) "Temporary Chat" else "OmniHub",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "History")
                            }
                            IconButton(onClick = { isTemporary = !isTemporary }) {
                                Icon(
                                    if (isTemporary) Icons.Filled.Timer else Icons.Outlined.Timer,
                                    contentDescription = "Temporary chat",
                                    tint = if (isTemporary) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                )
                            }
                            IconButton(onClick = {
                                messages.clear()
                                isTemporary = false
                            }) {
                                Icon(Icons.Default.AddComment, contentDescription = "New chat")
                            }
                        }
                    },
                    actions = {
                        TextButton(onClick = onOpenCustomize) {
                            Text("Customize", fontSize = 14.sp)
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                MessageInputBar(
                    value = messageText,
                    onValueChange = { messageText = it },
                    onSend = {
                        if (messageText.isNotBlank()) {
                            messages.add(ChatBubble("user", messageText))
                            messages.add(ChatBubble("assistant", "Routing through OmniRouter… (bootstrap UI — connect providers in Settings)"))
                            messageText = ""
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                state = rememberLazyListState(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(messages) { bubble ->
                    MessageBubble(bubble)
                }
            }
        }
    }
}

@Composable
private fun HistoryDrawer(
    history: List<String>,
    onNewChat: () -> Unit,
    onSelectHistory: (String) -> Unit,
    onOpenAccount: () -> Unit,
    onAddService: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onNewChat) {
                    Icon(Icons.Default.Edit, contentDescription = "New chat")
                }
            }
            Divider()
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(history) { item ->
                    NavigationDrawerItem(
                        label = { Text(item, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        selected = false,
                        onClick = { onSelectHistory(item) },
                        icon = { Icon(Icons.Outlined.ChatBubbleOutline, null) },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
            Divider()
            Column(Modifier.padding(16.dp)) {
                Text("Account", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onOpenAccount() }
                        .padding(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("O", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("OmniHub User", fontWeight = FontWeight.Medium)
                        Text("Free plan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Connected AI Services", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                listOf("OpenAI", "Anthropic", "MCP Server").forEach { name ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF22C55E), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onAddService,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add AI Service")
                }
            }
        }
    }
}

data class ChatBubble(val role: String, val content: String)

@Composable
private fun MessageBubble(bubble: ChatBubble) {
    val isUser = bubble.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Text(
                text = bubble.content,
                modifier = Modifier.padding(14.dp),
                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message OmniHub…") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 5
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
                shape = CircleShape
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}
