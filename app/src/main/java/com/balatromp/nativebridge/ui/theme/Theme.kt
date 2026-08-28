package com.balatromp.nativebridge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Light: fondo blanco directo, Dark: fondo negro directo
// Botones principales: rojo desaturado (no saturado fuerte), Header siempre negro
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF9E4B4B), // rojo desaturado
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8D0D0),
    onPrimaryContainer = Color(0xFF3A1111),
    secondary = Color(0xFF2B2B2B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8E8E8),
    onSecondaryContainer = Color(0xFF111111),
    tertiary = Color(0xFF5A5A5A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE8E8E8),
    onTertiaryContainer = Color(0xFF111111),
    error = Color(0xFFB00020),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDE8E9),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF444444),
    outline = Color(0xFFCCCCCC),
    outlineVariant = Color(0xFFE0E0E0),
    scrim = Color(0xFF000000),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD9A8A8), // rojo desaturado claro
    onPrimary = Color(0xFF4A1A1A),
    primaryContainer = Color(0xFF6B3333),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFCCCCCC),
    onSecondary = Color(0xFF111111),
    secondaryContainer = Color(0xFF222222),
    onSecondaryContainer = Color(0xFFE8E8E8),
    tertiary = Color(0xFFCCCCCC),
    onTertiary = Color(0xFF111111),
    tertiaryContainer = Color(0xFF222222),
    onTertiaryContainer = Color(0xFFE8E8E8),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF4A0000),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFCCCCCC),
    outline = Color(0xFF444444),
    outlineVariant = Color(0xFF222222),
    scrim = Color(0xFF000000),
)

@Composable
fun BalatroNativeBridgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, content = content)
}
