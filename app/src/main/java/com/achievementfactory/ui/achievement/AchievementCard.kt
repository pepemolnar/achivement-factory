package com.achievementfactory.ui.achievement

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.achievementfactory.domain.model.DailyAchievement
import com.achievementfactory.ui.theme.Spacing
import com.achievementfactory.ui.theme.color

/**
 * Resting card anatomy (D-01, D-07–D-11): neutral body, accent-only icon/ring cluster, name in
 * the Body role. No streak badge in this plan — every achievement starts at streak 0, and that
 * indicator arrives, hidden-at-zero, in plan 01-05.
 */
@Composable
fun AchievementCard(
    achievement: DailyAchievement,
    modifier: Modifier = Modifier,
) {
    val accentColor = achievement.accent.color()
    val progress = if (achievement.target > 0) {
        achievement.count.toFloat() / achievement.target.toFloat()
    } else {
        0f
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.cardMinHeight),
        shape = RoundedCornerShape(Spacing.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                ProgressRing(progress = progress.coerceIn(0f, 1f), accent = accentColor)
                AchievementGlyph(icon = achievement.icon, name = achievement.name, accent = accentColor)
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = achievement.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Renders the achievement's emoji glyph, or — when no icon is set — the uppercased first letter
 * of its name tinted with [accent] (D-18). Never a shared fallback symbol, never an empty ring
 * centre.
 */
@Composable
fun AchievementGlyph(icon: String?, name: String, accent: Color) {
    Box(
        modifier = Modifier.size(Spacing.iconClusterSize),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Text(text = icon, style = MaterialTheme.typography.bodyLarge)
        } else {
            val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            Text(
                text = letter,
                style = MaterialTheme.typography.bodyLarge,
                color = accent,
                textAlign = TextAlign.Center,
            )
        }
    }
}
