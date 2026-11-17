package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val BrstiColorScheme = darkColorScheme(
    primary = NeonViolet,
    onPrimary = ObsidianDeep,
    primaryContainer = DeepViolet,
    onPrimaryContainer = StarlightWhite,
    secondary = NeonCyan,
    onSecondary = ObsidianDeep,
    secondaryContainer = ObsidianCardHigh,
    onSecondaryContainer = NeonCyan,
    tertiary = NeonMagenta,
    onTertiary = StarlightWhite,
    background = ObsidianDeep,
    onBackground = StarlightWhite,
    surface = ObsidianSurface,
    onSurface = StarlightWhite,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = LavenderMuted,
    outline = IndigoOutline,
    error = CrimsonError,
    onError = StarlightWhite
)

private val BrstiShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun BrstiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BrstiColorScheme,
        typography = Typography,
        shapes = BrstiShapes,
        content = content
    )
}
