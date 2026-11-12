package com.dshremote.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFF5B8CFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1D3568),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFF3DDC97),
    onSecondary = Color(0xFF062318),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE8EEF9),
    surface = Color(0xFF121C2E),
    onSurface = Color(0xFFE8EEF9),
    surfaceVariant = Color(0xFF1B2940),
    onSurfaceVariant = Color(0xFFA8B8D0),
    outline = Color(0xFF33445F),
    error = Color(0xFFFF6B6B),
    onError = Color.White,
)

@Composable
fun DshTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = Typography(),
        content = content,
    )
}
