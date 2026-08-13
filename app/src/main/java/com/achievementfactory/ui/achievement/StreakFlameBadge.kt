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
 */
@Composable
fun StreakFlameBadge(streak: Int, accent: Color, modifier: Modifier = Modifier) {
    if (streak <= 0) return

    Row(
        modifier = modifier
            .semantics { contentDescription = "Current streak: $streak" }
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
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
