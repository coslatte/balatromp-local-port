package com.balatromp.nativebridge.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Primary: Balatro red #B52D2D | Secondary: blue variant #325A8C
private val Primary = Color(0xFFB52D2D)
private val OnPrimary = Color(0xFFFFFFFF)
private val PrimaryContainer = Color(0xFFFFDAD4)
private val OnPrimaryContainer = Color(0xFF410002)

private val Secondary = Color(0xFF325A8C)
private val OnSecondary = Color(0xFFFFFFFF)
private val SecondaryContainer = Color(0xFFD3E3FF)
private val OnSecondaryContainer = Color(0xFF001C38)

private val Tertiary = Color(0xFF6B5778)
private val OnTertiary = Color(0xFFFFFFFF)
private val TertiaryContainer = Color(0xFFF2DAFF)
private val OnTertiaryContainer = Color(0xFF251431)

private val Error = Color(0xFFBA1A1A)
private val ErrorContainer = Color(0xFFFFDAD6)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = Error,
    errorContainer = ErrorContainer,
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF201A19),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF201A19),
    surfaceVariant = Color(0xFFF5DDDB),
    onSurfaceVariant = Color(0xFF534342),
    outline = Color(0xFF857371),
    outlineVariant = Color(0xFFD8C2BF),
    scrim = Color(0xFF000000),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB4A9),
    onPrimary = Color(0xFF690003),
    primaryContainer = Color(0xFF930006),
    onPrimaryContainer = Color(0xFFFFDAD4),
    secondary = Color(0xFF9FCAFF),
    onSecondary = Color(0xFF003258),
    secondaryContainer = Color(0xFF21476B),
    onSecondaryContainer = Color(0xFFD3E3FF),
    tertiary = Color(0xFFD9B9F0),
    onTertiary = Color(0xFF3B2948),
    tertiaryContainer = Color(0xFF523F60),
    onTertiaryContainer = Color(0xFFF2DAFF),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    background = Color(0xFF201A19),
    onBackground = Color(0xFFEEEDE7),
    surface = Color(0xFF201A19),
    onSurface = Color(0xFFEEEDE7),
    surfaceVariant = Color(0xFF534342),
    onSurfaceVariant = Color(0xFFD8C2BF),
    outline = Color(0xFFA08C8B),
    outlineVariant = Color(0xFF534342),
    scrim = Color(0xFF000000),
)

@Composable
fun BalatroNativeBridgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
