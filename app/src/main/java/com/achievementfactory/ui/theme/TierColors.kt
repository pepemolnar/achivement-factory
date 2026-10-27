package com.achievementfactory.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.achievementfactory.domain.tier.Tier

/**
 * Fixed metal colours for the three tiers — the one place outside the per-achievement accent
 * palette where a card shows a colour of its own. Tier names are fixed (TIER-03), and so are their
 * colours: a Gold medal reads as gold on every achievement whatever its accent swatch is. Light
 * values are darkened enough to read as text on the light surface; dark values are lifted for the
 * dark surface.
 */
private val LightTierColors: Map<Tier, Color> = mapOf(
    Tier.BRONZE to Color(0xFFA0612B),
    Tier.SILVER to Color(0xFF6B7280),
    Tier.GOLD to Color(0xFFB8860B),
)

private val DarkTierColors: Map<Tier, Color> = mapOf(
    Tier.BRONZE to Color(0xFFE0A06A),
    Tier.SILVER to Color(0xFFD1D5DB),
    Tier.GOLD to Color(0xFFFCD34D),
)

/** Resolves this tier's metal [Color] against the current system light/dark theme setting. */
@Composable
fun Tier.color(): Color {
    val colors = if (isSystemInDarkTheme()) DarkTierColors else LightTierColors
    return colors.getValue(this)
}
