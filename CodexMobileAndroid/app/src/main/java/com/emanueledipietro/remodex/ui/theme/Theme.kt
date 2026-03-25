package com.emanueledipietro.remodex.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.emanueledipietro.remodex.model.RemodexAppearanceMode

private val LightScheme = lightColorScheme(
    primary = BlueLight,
    onPrimary = SurfaceLight,
    primaryContainer = BlueContainerLight,
    onPrimaryContainer = InkLight,
    secondary = InkSecondaryLight,
    onSecondary = SurfaceLight,
    secondaryContainer = SurfaceAltLight,
    onSecondaryContainer = InkLight,
    tertiary = OrangeLight,
    onTertiary = SurfaceLight,
    tertiaryContainer = OrangeContainerLight,
    onTertiaryContainer = InkLight,
    background = CanvasLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = SurfaceAltLight,
    onSurfaceVariant = InkSecondaryLight,
    surfaceDim = CanvasLight,
    surfaceBright = SurfaceLight,
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = SurfaceLowLight,
    surfaceContainer = SurfaceMidLight,
    surfaceContainerHigh = SurfaceHighLight,
    surfaceContainerHighest = SurfaceHighestLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = RedLight,
)

private val DarkScheme = darkColorScheme(
    primary = BlueDark,
    onPrimary = SurfaceLight,
    primaryContainer = BlueContainerDark,
    onPrimaryContainer = InkDark,
    secondary = InkSecondaryDark,
    onSecondary = CanvasDark,
    secondaryContainer = SurfaceAltDark,
    onSecondaryContainer = InkDark,
    tertiary = OrangeDark,
    onTertiary = CanvasDark,
    tertiaryContainer = OrangeContainerDark,
    onTertiaryContainer = InkDark,
    background = CanvasDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = SurfaceAltDark,
    onSurfaceVariant = InkSecondaryDark,
    surfaceDim = SurfaceLowDark,
    surfaceBright = SurfaceMidDark,
    surfaceContainerLowest = SurfaceLowDark,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = SurfaceMidDark,
    surfaceContainerHigh = SurfaceHighDark,
    surfaceContainerHighest = SurfaceHighestDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = RedDark,
)

@Composable
fun RemodexTheme(
    appearanceMode: RemodexAppearanceMode = RemodexAppearanceMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (appearanceMode) {
        RemodexAppearanceMode.SYSTEM -> isSystemInDarkTheme()
        RemodexAppearanceMode.LIGHT -> false
        RemodexAppearanceMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = RemodexTypography,
        content = content,
    )
}
