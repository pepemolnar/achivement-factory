package com.achievementfactory.ui.achievement

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.achievementfactory.domain.model.DailyAchievement
import com.achievementfactory.ui.celebration.CelebrationEvent
import com.achievementfactory.ui.celebration.CelebrationStage
import com.achievementfactory.ui.celebration.CompletionCelebration
import com.achievementfactory.ui.theme.MotionSpecs
import com.achievementfactory.ui.theme.Spacing
import com.achievementfactory.ui.theme.color

/**
 * Resting + completed card anatomy (D-01, D-02, D-07–D-11): neutral body, accent-only icon/ring
 * cluster, name in the Body role. The whole card is the tap target — no dedicated check button,
 * no stepper, no swipe gesture, so one tap is always exactly +1 regardless of the achievement's
 * target. [onLongPress] opens the detail screen (D-03). [StreakFlameBadge] anchors to the
 * top-end corner of the icon/ring cluster, overlapping it by ~4dp, and composes nothing at
 * streak 0 (D-07, D-08).
 *
 * [celebration] is non-null for exactly the duration of this achievement's one-shot completion
 * event (CELEB-04) — while present, [CompletionCelebration] owns the ring/checkmark/flame
 * animation clock entirely (plan 01-07); [onCelebrationSettled] fires once, when the sequence's
 * final stage completes, and is what the caller uses to release the card into the Done section
 * (D-06) — never a fixed delay. Outside a celebration, the card falls back to the same static
 * resting/done rendering plan 01-04 already established, reached this time by the animation
 * rather than a swap (D-11).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AchievementCard(
    achievement: DailyAchievement,
    onIncrement: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    celebration: CelebrationEvent.Completed? = null,
    onCelebrationSettled: () -> Unit = {},
) {
    val accentColor = achievement.accent.color()
    val restingProgress = if (achievement.target > 0) {
        achievement.count.toFloat() / achievement.target.toFloat()
    } else {
        0f
    }
    val animatedRestingProgress by animateFloatAsState(
        targetValue = restingProgress.coerceIn(0f, 1f),
        animationSpec = MotionSpecs.ringSweep,
        label = "progressRingSweep",
    )

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
            Box(
                contentAlignment = Alignment.Center,
                modifier = if (celebration != null) {
                    Modifier.testTag("achievement-celebrating-${achievement.id}")
                } else {
                    Modifier
                },
            ) {
                when {
                    celebration != null -> CompletionCelebration(
                        event = celebration,
                        accent = accentColor,
                        onStage = { stage -> if (stage == CelebrationStage.SETTLED) onCelebrationSettled() },
                    ) { visual ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.scale(visual.checkScale),
                        ) {
                            ProgressRing(
                                progress = visual.ringProgress,
                                accent = accentColor,
                                checkmarkProgress = visual.checkmarkProgress,
                            )
                            if (visual.checkmarkProgress < 1f) {
                                Box(modifier = Modifier.graphicsLayer { alpha = 1f - visual.checkmarkProgress }) {
                                    AchievementGlyph(icon = achievement.icon, name = achievement.name, accent = accentColor)
                                }
                            }
                        }
                        StreakFlameBadge(
                            streak = celebration.newStreak,
                            accent = accentColor,
                            scale = visual.flameScale,
                            flashAmount = visual.flameFlash,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = Spacing.xs, y = -Spacing.xs),
                        )
                    }

                    achievement.isDone -> {
                        ProgressRing(progress = 1f, accent = accentColor)
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Completed",
                            tint = accentColor,
                            modifier = Modifier.size(Spacing.iconClusterSize),
                        )
                        StreakFlameBadge(
                            streak = achievement.currentStreak,
                            accent = accentColor,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = Spacing.xs, y = -Spacing.xs),
                        )
                    }

                    else -> {
                        ProgressRing(progress = animatedRestingProgress, accent = accentColor)
                        AchievementGlyph(icon = achievement.icon, name = achievement.name, accent = accentColor)
                        StreakFlameBadge(
                            streak = achievement.currentStreak,
                            accent = accentColor,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = Spacing.xs, y = -Spacing.xs),
                        )
                    }
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
