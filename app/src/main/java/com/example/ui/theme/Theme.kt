package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = VelocityCyan,
    onPrimary = ObsidianBg,
    primaryContainer = VelocityCyanContainer,
    onPrimaryContainer = VelocityCyan,
    secondary = LayerCoral,
    onSecondary = ObsidianBg,
    secondaryContainer = LayerCoralContainer,
    onSecondaryContainer = LayerCoral,
    tertiary = AudioViolet,
    onTertiary = ObsidianBg,
    tertiaryContainer = AudioVioletContainer,
    onTertiaryContainer = AudioViolet,
    background = ObsidianBg,
    onBackground = StudioTextPrimary,
    surface = StudioSurface,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = StudioTextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = TrackLaneBg,
    surfaceContainer = StudioSurface,
    surfaceContainerHigh = StudioSurfaceElevated,
    surfaceContainerHighest = StudioSurfaceHighest,
    outline = StudioBorder,
    outlineVariant = StudioBorder.copy(alpha = 0.5f),
    error = StudioError,
    onError = ObsidianBg
)

val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun VelocityCutTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = Typography,
        shapes = StudioShapes,
        content = content
    )
}
