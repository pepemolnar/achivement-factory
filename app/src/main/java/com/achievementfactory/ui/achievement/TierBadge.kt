package com.achievementfactory.ui.achievement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.achievementfactory.domain.tier.Tier
import com.achievementfactory.ui.theme.Spacing
import com.achievementfactory.ui.theme.color

/**
 * The tier reached in the current period (TIER-07): a medal glyph plus the fixed tier name
 * (TIER-03), both in the tier's own metal colour. Callers compose it only when a tier has been
 * reached — an achievement below its done threshold, or with no ladder at all, shows nothing.
 *
 * Owns no animation clock: [scale] is the already-animated pop value
 * [com.achievementfactory.ui.celebration.CompletionCelebration] supplies during a celebration.
 */
@Composable
fun TierBadge(
    tier: Tier,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    val tierColor = tier.color()
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clearAndSetSemantics { contentDescription = "Tier reached: ${tier.displayName}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs / 2),
    ) {
        Icon(
            imageVector = Icons.Filled.MilitaryTech,
            contentDescription = null,
            tint = tierColor,
            modifier = Modifier.size(Spacing.tierBadgeIconSize),
        )
        Text(
            text = tier.displayName,
            style = MaterialTheme.typography.labelLarge,
            color = tierColor,
        )
    }
}
