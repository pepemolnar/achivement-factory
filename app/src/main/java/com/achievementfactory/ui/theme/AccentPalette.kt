package com.achievementfactory.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.achievementfactory.data.achievement.AccentSwatch

/**
 * Light/dark hex pairs for the ten fixed per-achievement accent swatches (D-16), quoted
 * verbatim from 01-UI-SPEC.md "Per-Achievement Accent Palette". Reserved for accents only — an
 * achievement's icon/glyph tint, its progress-ring stroke, and (from plan 01-05) its streak
 * badge. Never the card background or border (D-10).
 */
private val LightSwatches: Map<AccentSwatch, Color> = mapOf(
    AccentSwatch.RED to Color(0xFFDC2626),
    AccentSwatch.ORANGE to Color(0xFFEA580C),
    AccentSwatch.AMBER to Color(0xFFD97706),
    AccentSwatch.LIME to Color(0xFF65A30D),
    AccentSwatch.EMERALD to Color(0xFF059669),
    AccentSwatch.TEAL to Color(0xFF0D9488),
    AccentSwatch.SKY to Color(0xFF0284C7),
    AccentSwatch.INDIGO to Color(0xFF4F46E5),
    AccentSwatch.VIOLET to Color(0xFF7C3AED),
    AccentSwatch.PINK to Color(0xFFDB2777),
)

private val DarkSwatches: Map<AccentSwatch, Color> = mapOf(
    AccentSwatch.RED to Color(0xFFF87171),
    AccentSwatch.ORANGE to Color(0xFFFB923C),
    AccentSwatch.AMBER to Color(0xFFFBBF24),
    AccentSwatch.LIME to Color(0xFFA3E635),
    AccentSwatch.EMERALD to Color(0xFF34D399),
    AccentSwatch.TEAL to Color(0xFF2DD4BF),
    AccentSwatch.SKY to Color(0xFF38BDF8),
    AccentSwatch.INDIGO to Color(0xFF818CF8),
    AccentSwatch.VIOLET to Color(0xFFA78BFA),
    AccentSwatch.PINK to Color(0xFFF472B6),
)

/** Resolves this swatch's [Color] against the current system light/dark theme setting. */
@Composable
fun AccentSwatch.color(): Color {
    val swatches = if (isSystemInDarkTheme()) DarkSwatches else LightSwatches
    return swatches.getValue(this)
}
