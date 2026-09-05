package com.omnihub.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnihub.ui.GreetingState

/**
 * Greeting overlay component - appears on new conversation, disappears after first message.
 */
@Composable
fun GreetingOverlay(
    greeting: GreetingState?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    greeting?.let { currentGreeting ->
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = spring()) + slideInVertically(initialOffsetY = { -20 }),
            exit = fadeOut(animationSpec = spring()) + slideOutVertically(targetOffsetY = { -20 }),
            modifier = modifier
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = currentGreeting.text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    if (currentGreeting.isContextual) {
                        Text(
                            text = "Tap + to start fresh anytime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}