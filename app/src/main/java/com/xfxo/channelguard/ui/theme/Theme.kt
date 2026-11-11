package com.xfxo.channelguard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B5B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBCEBDD),
    onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF4E635A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1E8DD),
    onSecondaryContainer = Color(0xFF0B2018),
    tertiary = Color(0xFF805A20),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB0),
    onTertiaryContainer = Color(0xFF2A1700),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F9F7),
    onBackground = Color(0xFF191D1B),
    surface = Color(0xFFF7F9F7),
    onSurface = Color(0xFF191D1B),
    surfaceVariant = Color(0xFFE1E7E3),
    onSurfaceVariant = Color(0xFF414944),
    outline = Color(0xFF727A75),
    outlineVariant = Color(0xFFC1C9C3)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD4C1),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005143),
    onPrimaryContainer = Color(0xFFAEF1DC),
    secondary = Color(0xFFB5CCC1),
    onSecondary = Color(0xFF20352C),
    secondaryContainer = Color(0xFF374B41),
    onSecondaryContainer = Color(0xFFD1E8DD),
    tertiary = Color(0xFFE9C18A),
    onTertiary = Color(0xFF482900),
    tertiaryContainer = Color(0xFF633F09),
    onTertiaryContainer = Color(0xFFFFDDB0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111614),
    onBackground = Color(0xFFE0E4E1),
    surface = Color(0xFF111614),
    onSurface = Color(0xFFE0E4E1),
    surfaceVariant = Color(0xFF414944),
    onSurfaceVariant = Color(0xFFC1C9C3),
    outline = Color(0xFF8B938D),
    outlineVariant = Color(0xFF414944)
)

@Composable
fun ChannelGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
