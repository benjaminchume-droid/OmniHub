package com.omnihub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8B5CF6),
    onPrimary = Color.White,
    secondary = Color(0xFF22D3EE),
    background = Color(0xFF0B0F19),
    surface = Color(0xFF111827),
    surfaceVariant = Color(0xFF1F2937),
    onBackground = Color(0xFFE5E7EB),
    onSurface = Color(0xFFE5E7EB),
    outline = Color(0xFF374151)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF7C3AED),
    onPrimary = Color.White,
    secondary = Color(0xFF0891B2),
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    outline = Color(0xFFCBD5E1)
)

@Composable
fun OmniHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content
    )
}
