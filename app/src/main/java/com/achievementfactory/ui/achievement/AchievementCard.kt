package com.achievementfactory.ui.achievement

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.achievementfactory.domain.model.TrackedAchievement
import com.achievementfactory.domain.tier.Tier
import com.achievementfactory.ui.celebration.CelebrationBurst
import com.achievementfactory.ui.celebration.CelebrationEvent
import com.achievementfactory.ui.celebration.CelebrationStage
import com.achievementfactory.ui.celebration.CelebrationVisualState
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
 * streak 0 (D-07, D-08). For a laddered achievement the card is done only at Gold; Bronze and
 * Silver keep it in the outstanding list with its ring still open and a medal badge showing.
 *
 * Under the name, a laddered achievement shows its progress toward the *next* tier and a
 * [TierBadge] for the tier reached this period (TIER-07); a no-ladder achievement keeps the
 * Phase 1 `count/target` text (hidden for target 1).
 *
 * [celebration] is non-null for exactly the duration of this achievement's one-shot event
 * (CELEB-04) — while present, [CompletionCelebration] owns the ring/checkmark/flame/badge/burst
 * animation clock entirely. [onCelebrationSettled] is called with that same event, so a caller
 * can tell an event that settled apart from one that has since been replaced by a newer one; it
 * fires on whichever comes first: the sequence's final stage completing, or this card leaving
 * composition (or switching to a different event) with the celebration still running. It is
 * required to be idempotent for that reason.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AchievementCard(
    achievement: TrackedAchievement,
    onIncrement: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    celebration: CelebrationEvent? = null,
    onCelebrationSettled: (CelebrationEvent) -> Unit = {},
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

    // A celebration that leaves composition before reaching SETTLED — the card scrolled out of
    // the LazyColumn, the Activity was recreated, the user navigated away or switched tabs, or a
    // newer event for this card replaced it — never fires its [onStage] settle callback, so the
    // caller has to be told here instead. Two things depend on it: the card is released from the
    // ViewModel's held-back set (otherwise a done achievement stays pinned above the Done
    // divider), and the event is dropped so scrolling back into view does not restart the whole
    // sequence on an already-completed card.
    if (celebration != null) {
        DisposableEffect(celebration) {
            onDispose { onCelebrationSettled(celebration) }
        }
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
        if (celebration != null) {
            CompletionCelebration(
                event = celebration,
                accent = accentColor,
                onStage = { stage -> if (stage == CelebrationStage.SETTLED) onCelebrationSettled(celebration) },
            ) { visual ->
                CardBody(
                    achievement = achievement,
                    accentColor = accentColor,
                    animatedRestingProgress = animatedRestingProgress,
                    celebration = celebration,
                    visual = visual,
                )
            }
        } else {
            CardBody(
                achievement = achievement,
                accentColor = accentColor,
                animatedRestingProgress = animatedRestingProgress,
                celebration = null,
                visual = null,
            )
        }
    }
}

/**
 * The card's single layout, shared by the resting and celebrating paths so that the two can
 * never drift apart: [visual] is null outside a celebration, in which case every value falls back
 * to the static resting/done rendering (D-11).
 */
@Composable
private fun CardBody(
    achievement: TrackedAchievement,
    accentColor: Color,
    animatedRestingProgress: Float,
    celebration: CelebrationEvent?,
    visual: CelebrationVisualState?,
) {
    // During a celebration the event's own tier is authoritative — it is the value from the very
    // write that triggered it, whereas the list state may still be catching up to that write.
    val shownTier: Tier? = celebration?.tier ?: achievement.tierReached

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
            if (visual != null && celebration != null) {
                val burstColors = listOfNotNull(celebration.tier?.color(), accentColor)
                CelebrationBurst(
                    progress = visual.burstProgress,
                    particleCount = MotionSpecs.burstParticleCount(celebration.tier),
                    radius = MotionSpecs.burstRadius(celebration.tier),
                    colors = burstColors,
                    modifier = Modifier.matchParentSize(),
                )
                // A medal below Gold does not complete the card: keep the open resting ring and the
                // glyph, and let only the pulse, badge and burst play over them.
                val isUpgrade = celebration is CelebrationEvent.TierUpgraded
                val ringProgress = if (isUpgrade) animatedRestingProgress else visual.ringProgress
                val checkmarkProgress = if (isUpgrade) 0f else visual.checkmarkProgress
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.scale(visual.checkScale),
                ) {
                    ProgressRing(
                        progress = ringProgress,
                        accent = accentColor,
                        checkmarkProgress = checkmarkProgress,
                    )
                    if (checkmarkProgress < 1f) {
                        Box(modifier = Modifier.graphicsLayer { alpha = 1f - checkmarkProgress }) {
                            AchievementGlyph(icon = achievement.icon, name = achievement.name, accent = accentColor)
                        }
                    }
                }
                val streak = if (celebration.ticksStreak) celebration.newStreak else achievement.currentStreak
                StreakFlameBadge(
                    streak = streak,
                    accent = accentColor,
                    scale = visual.flameScale,
                    flashAmount = visual.flameFlash,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = Spacing.xs, y = -Spacing.xs),
                )
            } else if (achievement.isDone) {
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
            } else {
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
        Spacer(modifier = Modifier.width(Spacing.sm))
        Column {
            Text(
                text = achievement.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val progressText = progressLabel(achievement)
            if (progressText != null || shownTier != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (progressText != null) {
                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (shownTier != null) {
                        TierBadge(tier = shownTier, scale = visual?.tierBadgeScale ?: 1f)
                    }
                }
            }
        }
    }
}

/**
 * The small progress text under the name, or null when there is nothing worth showing.
 *
 * Target-1 achievements read as a boolean tick, not a quota (PITFALLS "UX Pitfalls") — no
 * fraction is ever shown for them, on either resting or done cards. A laddered achievement shows
 * its count against the *next* tier's threshold, so the number always points at the next medal;
 * once Gold is reached it shows the count against Gold. A Gold-only ladder of 1 is a boolean tick
 * like any other target-1 achievement.
 */
internal fun progressLabel(achievement: TrackedAchievement): String? {
    val ladder = achievement.tiers
    if (ladder != null) {
        if (ladder.gold <= 1) return null
        val nextThreshold = ladder.nextTierAbove(achievement.count)?.second ?: ladder.gold
        return "${achievement.count}/$nextThreshold"
    }
    return if (achievement.target > 1) "${achievement.count}/${achievement.target}" else null
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
