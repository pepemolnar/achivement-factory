package com.achievementfactory.ui.celebration

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.achievementfactory.ui.theme.Spacing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The radial particle burst a tiered celebration throws out of the icon cluster (CELEB-01) —
 * stock Compose `Canvas`, no particle library (Konfetti stays reserved for Phase 5's full-tab
 * moment). A pure renderer: it owns no animation clock; [progress] (0 → 1) is driven by
 * [CompletionCelebration] through [com.achievementfactory.ui.theme.MotionSpecs.burst].
 *
 * Particles start at the ring's edge and travel out to [radius], shrinking and fading as they go.
 * [colors] are cycled across the particles, so a Gold burst can alternate the metal colour with
 * the achievement's accent. Nothing is drawn at `progress <= 0f` or `>= 1f`, and nothing at all
 * when [particleCount] is 0 — the no-ladder case.
 *
 * Meant to be placed with `Modifier.matchParentSize()` inside the icon-cluster `Box`, so it never
 * changes the card's layout; it draws past its own bounds on purpose.
 */
@Composable
fun CelebrationBurst(
    progress: Float,
    particleCount: Int,
    radius: Dp,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    if (particleCount <= 0 || progress <= 0f || progress >= 1f || colors.isEmpty()) return

    Canvas(modifier = modifier) {
        val eased = FastOutSlowInEasing.transform(progress)
        val startDistance = Spacing.iconClusterSize.toPx() / 2f
        val distance = startDistance + (radius.toPx() - startDistance).coerceAtLeast(0f) * eased
        val dotRadius = Spacing.burstParticleSize.toPx() / 2f * (1f - 0.5f * eased)
        val alpha = 1f - eased

        for (i in 0 until particleCount) {
            val angle = 2.0 * PI * i / particleCount - PI / 2.0
            drawCircle(
                color = colors[i % colors.size].copy(alpha = alpha),
                radius = dotRadius,
                center = Offset(
                    x = center.x + (distance * cos(angle)).toFloat(),
                    y = center.y + (distance * sin(angle)).toFloat(),
                ),
            )
        }
    }
}
