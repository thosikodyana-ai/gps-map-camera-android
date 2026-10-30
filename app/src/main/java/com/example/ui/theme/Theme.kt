package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TacticalDarkColorScheme = darkColorScheme(
    primary = SignalAmber,
    onPrimary = Color(0xFF141100),
    primaryContainer = Color(0xFF3A2900),
    onPrimaryContainer = SignalAmberLight,
    secondary = GpsEmerald,
    onSecondary = Color(0xFF00210E),
    secondaryContainer = Color(0xFF00391C),
    onSecondaryContainer = GpsEmerald,
    tertiary = CompassCyan,
    onTertiary = Color(0xFF001F2A),
    tertiaryContainer = Color(0xFF003547),
    onTertiaryContainer = CompassCyan,
    background = ObsidianBackground,
    onBackground = TextPrimary,
    surface = TacticalSurface,
    onSurface = TextPrimary,
    surfaceVariant = TacticalSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    error = AlertCoral
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TacticalDarkColorScheme,
        typography = Typography,
        content = content
    )
}
