package com.achievementfactory.ui.achievement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.achievementfactory.ui.theme.Spacing

/**
 * The current-streak indicator (D-07): a flame glyph plus the streak number, tinted with the
 * achievement's own accent swatch (never a fixed color — D-10's accent-only rule applies here
 * too). Composes nothing at all when [streak] is non-positive — the hidden-at-zero rule (D-08)
 * is enforced *inside* this component so no call site can forget it. A brand-new achievement, or
 * one that just reset to zero, therefore looks the same — clean, with nothing marking the
 * absence at all (D-26).
 *
 * Owns no animation clock of its own (plan 01-07): [scale] and [flashAmount] are already-animated
 * values a caller supplies during the completion celebration — [MotionSpecs]-sourced specs in
 * `CompletionCelebration` drive them. [flashAmount] blends the badge's own neutral background
 * toward [accent] (0 = no flash, 1 = full accent), the "brief flash" the achievement's first-ever
 * streak tick plays (D-22).
 */
@Composable
fun StreakFlameBadge(
    streak: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    flashAmount: Float = 0f,
) {
    if (streak <= 0) return

    val backgroundColor = lerp(
        MaterialTheme.colorScheme.surfaceContainerHighest,
        accent,
        flashAmount.coerceIn(0f, 1f),
    )

    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics { contentDescription = "Current streak: $streak" }
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(percent = 50),
            )
            .padding(horizontal = Spacing.xs, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.LocalFireDepartment,
            contentDescription = null, // decorative — the Row's own semantics carries the value
            tint = accent,
            modifier = Modifier.size(Spacing.flameBadgeSize),
        )
        Text(
            text = streak.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = accent,
        )
    }
}
