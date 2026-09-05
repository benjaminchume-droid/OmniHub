package com.omnihub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnihub.source.AiSource

@Composable
fun ChatScreen(
    conversation: ConversationState,
    sources: List<AiSource>,
    onSendMessage: (String, String?) -> Unit,
    onNewConversation: () -> Unit,
    onSelectSource: (String) -> Unit
) {
    var messageInput by remember { mutableStateOf("") }
    var selectedSourceId by remember { mutableStateOf<String?>(null) }
    var showSourceSelector by remember { mutableStateOf(false) }
    var showGreeting by remember { mutableStateOf(true) }

    val greetings = listOf(
        "Good morning! ☀️ Ready to dive into some knowledge?",
        "Good afternoon! 🌤️ Let's blow the candle with some great ideas!",
        "Good evening! 🌙 Ready to build something amazing?",
        "Let's munch those questions and find tasty solutions! 🍽️",
        "Ready to build something incredible? 🚀",
        "Let's make some magic happen! ✨",
        "Time to get creative! 🎨",
        "Knowledge awaits! 📚",
        "Let's solve this together! 🤝",
        "Ready for a breakthrough? 💡",
        "Let's code some dreams! 💻",
        "Time to think big! 🧠",
        "Ready to innovate? 🔧",
        "Let's create something epic! 🎯",
        "Adventure awaits! 🗺️",
        "Ready to explore? 🔍",
        "Let's make it happen! 💪",
        "Time to be awesome! 😎",
        "Ready to transform? 🌟",
        "Let's build the future! 🌐"
    )

    val randomGreeting = remember { greetings.random() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0F1E),
                        Color(0xFF1A1A2E)
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            ChatHeader(
                selectedSource = sources.find { it.id == selectedSourceId },
                onSourceClick = { showSourceSelector = true },
                onNewConversation = {
                    onNewConversation()
                    showGreeting = true
                }
            )

            Divider(color = Color(0xFF2A2A3E), thickness = 1.dp)

            // Messages or Empty State
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (conversation.messages.isEmpty() && showGreeting) {
                    EmptyStateGreeting(greeting = randomGreeting)
                } else {
                    MessagesList(
                        messages = conversation.messages,
                        onGreetingDismissed = { showGreeting = false }
                    )
                }
            }

            Divider(color = Color(0xFF2A2A3E), thickness = 1.dp)

            // Input Area
            ChatInputArea(
                messageInput = messageInput,
                onMessageChange = { messageInput = it },
                onSend = {
                    if (messageInput.isNotBlank()) {
                        onSendMessage(messageInput, selectedSourceId)
                        messageInput = ""
                        showGreeting = false
                    }
                },
                selectedSource = sources.find { it.id == selectedSourceId },
                onSourceSelect = { showSourceSelector = true },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Source Selector Modal
        if (showSourceSelector) {
            SourceSelectorModal(
                sources = sources,
                selectedId = selectedSourceId,
                onSourceSelect = { sourceId ->
                    selectedSourceId = sourceId
                    onSelectSource(sourceId)
                    showSourceSelector = false
                },
                onDismiss = { showSourceSelector = false }
            )
        }
    }
}

@Composable
fun ChatHeader(
    selectedSource: AiSource?,
    onSourceClick: () -> Unit,
    onNewConversation: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Source Display
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onSourceClick() }
                .background(Color(0xFF1E1E2E))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.SmartToy,
                contentDescription = "AI Source",
                tint = Color(0xFF00D9FF),
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = selectedSource?.name ?: "Select Provider",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (selectedSource != null) {
                    Text(
                        text = selectedSource.models.firstOrNull()?.name ?: "Unknown",
                        color = Color(0xFFB0B0C0),
                        fontSize = 11.sp
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = "Expand",
                tint = Color(0xFF00D9FF),
                modifier = Modifier.size(20.dp)
            )
        }

        // New Conversation Button
        IconButton(
            onClick = onNewConversation,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1E2E))
        ) {
            Icon(
                imageVector = Icons.Filled.AddCircle,
                contentDescription = "New Conversation",
                tint = Color(0xFF00D9FF),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun EmptyStateGreeting(greeting: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated robot head (placeholder)
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF00D9FF), Color(0xFF0099CC))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🤖",
                fontSize = 60.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = greeting,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Choose a provider and start chatting",
            color = Color(0xFFB0B0C0),
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun MessagesList(
    messages: List<Message>,
    onGreetingDismissed: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        reverseLayout = true
    ) {
        items(messages.reversed()) { message ->
            MessageBubble(message)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (message.isUser)
                        Color(0xFF00D9FF)
                    else
                        Color(0xFF1E1E2E)
                )
                .padding(12.dp)
        ) {
            Text(
                text = message.content,
                color = if (message.isUser) Color.Black else Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun ChatInputArea(
    messageInput: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    selectedSource: AiSource?,
    onSourceSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xFF0F0F1E))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1E2E))
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = messageInput,
                onValueChange = onMessageChange,
                placeholder = {
                    Text(
                        "Ask anything...",
                        color = Color(0xFF808090)
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedTextColor = Color.White,
                    focusedTextColor = Color.White,
                    cursorColor = Color(0xFF00D9FF)
                ),
                singleLine = false,
                maxLines = 3
            )

            // Send Button
            IconButton(
                onClick = onSend,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (messageInput.isNotBlank())
                            Color(0xFF00D9FF)
                        else
                            Color(0xFF333344)
                    )
                    .size(40.dp),
                enabled = messageInput.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "Send",
                    tint = if (messageInput.isNotBlank()) Color.Black else Color(0xFF808090),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (selectedSource == null) {
            Text(
                text = "No provider selected. Tap the provider selector above.",
                color = Color(0xFFFF6B6B),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun SourceSelectorModal(
    sources: List<AiSource>,
    selectedId: String?,
    onSourceSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color(0xFF1A1A2E))
                .padding(24.dp)
                .clickable(enabled = false) {}
        ) {
            Text(
                text = "Select AI Provider",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(sources) { source ->
                    SourceOption(
                        source = source,
                        isSelected = source.id == selectedId,
                        onClick = { onSourceSelect(source.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun SourceOption(
    source: AiSource,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) Color(0xFF00D9FF).copy(alpha = 0.2f)
                else Color(0xFF2A2A3E)
            )
            .clickable { onClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = source.description,
                color = Color(0xFFB0B0C0),
                fontSize = 12.sp
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Selected",
                tint = Color(0xFF00D9FF),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// Data Classes
data class ConversationState(
    val id: String,
    val messages: List<Message>,
    val createdAt: Long
)

data class Message(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val sourceId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
