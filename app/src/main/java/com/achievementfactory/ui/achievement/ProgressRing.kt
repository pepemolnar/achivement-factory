package com.achievementfactory.ui.achievement

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.achievementfactory.ui.theme.Spacing

/**
 * The accent-coloured progress ring around an achievement's icon (D-09) — not pips, not a bar.
 * Sweeps clockwise from 12 o'clock. Every sweep change (partial tap or the final, completing
 * tap alike) animates with the exact per-tap spec from 01-UI-SPEC.md's Motion section:
 * `tween(150ms, FastOutSlowInEasing)`. [closed] forces the sweep to a full circle regardless of
 * [progress]'s exact fraction, which is what the completed-card treatment (D-11) renders — this
 * plan renders that end state statically; the bouncier spring-driven ring-close *transition
 * into* it is plan 01-07's completion sequence, layered on top without changing this
 * composable's contract.
 */
@Composable
fun ProgressRing(
    progress: Float,
    accent: Color,
    closed: Boolean,
    modifier: Modifier = Modifier,
) {
    val strokeWidth = Spacing.ringStrokeWidth
    val diameter = Spacing.iconClusterSize + (Spacing.xs * 2) // 2dp outset on every side
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    val targetProgress = if (closed) 1f else progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "progressRingSweep",
    )

    Canvas(modifier = modifier.size(diameter)) {
        val strokePx = strokeWidth.toPx()
        val inset = strokePx / 2
        val arcSize = Size(size.width - strokePx, size.height - strokePx)
        val topLeft = Offset(inset, inset)

        // At progress = 0f only the neutral track is drawn.
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx),
        )

        if (animatedProgress > 0f) {
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx),
            )
        }
    }
}
