package com.omnihub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Exact hub icon amber: #F5A623 */
val OmniAmber = Color(0xFFF5A623)
val OmniAmberDark = Color(0xFFD4890F)
val OmniAmberContainer = Color(0x33F5A623)
val OmniGlass = Color(0x22F5A623)
val OmniGlassBorder = Color(0x55F5A623)
val OmniBg = Color(0xFF0A0A0C)
val OmniSurface = Color(0xFF141418)
val OmniSurface2 = Color(0xFF1C1C22)

private val DarkColorScheme = darkColorScheme(
    primary = OmniAmber,
    onPrimary = Color(0xFF1A1200),
    primaryContainer = Color(0xFF3D2E0A),
    onPrimaryContainer = OmniAmber,
    secondary = Color(0xFFFFC857),
    onSecondary = Color(0xFF1A1200),
    secondaryContainer = Color(0xFF3D2E0A),
    background = OmniBg,
    surface = OmniSurface,
    surfaceVariant = OmniSurface2,
    onBackground = Color(0xFFF5F0E6),
    onSurface = Color(0xFFF5F0E6),
    onSurfaceVariant = Color(0xFFB8B0A0),
    outline = Color(0xFF4A4538),
    outlineVariant = Color(0xFF2A2620),
    error = Color(0xFFFF6B6B)
)

private val LightColorScheme = lightColorScheme(
    primary = OmniAmberDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE8B8),
    onPrimaryContainer = Color(0xFF3D2E0A),
    secondary = OmniAmber,
    onSecondary = Color(0xFF1A1200),
    background = Color(0xFFFFFBF5),
    surface = Color(0xFFFFF8EE),
    surfaceVariant = Color(0xFFFFF0D6),
    onBackground = Color(0xFF1A1200),
    onSurface = Color(0xFF1A1200),
    onSurfaceVariant = Color(0xFF5C5346),
    outline = Color(0xFFD4C4A8),
    error = Color(0xFFB3261E)
)

@Composable
fun OmniHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography(),
        content = content
    )
}
