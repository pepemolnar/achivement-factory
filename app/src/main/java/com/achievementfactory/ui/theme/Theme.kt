package com.achievementfactory.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    background = LightBackground,
    surface = LightBackground,
    surfaceContainer = LightSurfaceContainer,
    primary = LightPrimary,
    error = LightError,
)

private val DarkColors = darkColorScheme(
    background = DarkBackground,
    surface = DarkBackground,
    surfaceContainer = DarkSurfaceContainer,
    primary = DarkPrimary,
    error = DarkError,
)

/**
 * App-wide Material3 theme. Selects the baseline light/dark color scheme from the system
 * setting only — deliberately **no dynamic color**, per 01-UI-SPEC.md "Design System": dynamic
 * color would undermine the fixed, predictable per-achievement accent palette.
 */
@Composable
fun AchievementFactoryTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}
