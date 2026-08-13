package com.achievementfactory.ui.achievement

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
 * Resting + completed card anatomy (D-01, D-02, D-07–D-11): neutral body, accent-only icon/ring
 * cluster, name in the Body role. The whole card is the tap target — no dedicated check button,
 * no stepper, no swipe gesture, so one tap is always exactly +1 regardless of the achievement's
 * target. [onLongPress] opens the detail screen; it is a no-op at every call site in this plan
 * and wired to real navigation in plan 01-06 (D-03) — the parameter exists now so that wiring is
 * a one-line change then, not a signature change. No streak badge in this plan — every
 * achievement starts at streak 0, and that indicator arrives, hidden-at-zero, in plan 01-05.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AchievementCard(
    achievement: DailyAchievement,
    onIncrement: () -> Unit,
    onLongPress: () -> Unit,
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
            .heightIn(min = Spacing.cardMinHeight)
            .combinedClickable(onClick = onIncrement, onLongClick = onLongPress),
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
                ProgressRing(
                    progress = progress.coerceIn(0f, 1f),
                    accent = accentColor,
                    closed = achievement.isDone,
                )
                if (achievement.isDone) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Completed",
                        tint = accentColor,
                        modifier = Modifier.size(Spacing.iconClusterSize),
                    )
                } else {
                    AchievementGlyph(icon = achievement.icon, name = achievement.name, accent = accentColor)
                }
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Column {
                Text(
                    text = achievement.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // Target-1 achievements read as a boolean tick, not a quota (PITFALLS "UX
                // Pitfalls") — no fraction is ever shown for them, on either resting or done cards.
                if (achievement.target > 1) {
                    Text(
                        text = "${achievement.count}/${achievement.target}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
