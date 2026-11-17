package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val JarvisColorScheme = darkColorScheme(
    primary = ArcCyan,
    onPrimary = ObsidianBackground,
    primaryContainer = ArcCyanContainer,
    onPrimaryContainer = ArcCyan,
    secondary = ReactorBlue,
    onSecondary = TextPrimary,
    secondaryContainer = ReactorBlueContainer,
    onSecondaryContainer = TextPrimary,
    tertiary = HologramTeal,
    onTertiary = ObsidianBackground,
    background = ObsidianBackground,
    onBackground = TextPrimary,
    surface = MidnightSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedSlate,
    onSurfaceVariant = TextSecondary,
    outline = CardBorderSlate,
    outlineVariant = ColorTokens.OutlineDim,
    error = ErrorCoral,
    onError = TextPrimary
)

private object ColorTokens {
    val OutlineDim = androidx.compose.ui.graphics.Color(0xFF1A2640)
}

val JarvisShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = JarvisTypography,
        shapes = JarvisShapes,
        content = content
    )
}
