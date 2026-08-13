package com.achievementfactory.ui.achievement

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.achievementfactory.ui.theme.Spacing

/**
 * The accent-coloured progress ring around an achievement's icon (D-09) — not pips, not a bar.
 * Sweeps clockwise from 12 o'clock. A pure renderer (plan 01-07): it owns no animation clock of
 * its own — every caller supplies an already-animated [progress] (and, during the completion
 * celebration, [checkmarkProgress]), driven by `MotionSpecs`-sourced specs in
 * `AchievementCard`/`CompletionCelebration`. When [checkmarkProgress] is above 0f, a checkmark is
 * drawn as a partial `Path` on this *same* `Canvas` — growing continuously out of the ring rather
 * than swapped in as a separate icon (D-11).
 */
@Composable
fun ProgressRing(
    progress: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    checkmarkProgress: Float = 0f,
) {
    val strokeWidth = Spacing.ringStrokeWidth
    val diameter = Spacing.iconClusterSize + (Spacing.xs * 2) // 2dp outset on every side
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val clampedProgress = progress.coerceIn(0f, 1f)

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

        if (clampedProgress > 0f) {
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * clampedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx),
            )
        }

        if (checkmarkProgress > 0f) {
            drawGrowingCheckmark(progress = checkmarkProgress.coerceIn(0f, 1f), color = accent, strokePx = strokePx)
        }
    }
}

/**
 * A two-segment checkmark polyline inscribed inside the ring, trimmed to [progress] of its own
 * length via `PathMeasure` — the shape grows, it is never faded or swapped in (D-11).
 */
private fun DrawScope.drawGrowingCheckmark(progress: Float, color: Color, strokePx: Float) {
    val w = size.width
    val h = size.height
    val full = Path().apply {
        moveTo(w * 0.28f, h * 0.53f)
        lineTo(w * 0.42f, h * 0.68f)
        lineTo(w * 0.74f, h * 0.32f)
    }
    val measure = PathMeasure().apply { setPath(full, forceClosed = false) }
    val trimmed = Path()
    measure.getSegment(0f, measure.length * progress, trimmed, startWithMoveTo = true)
    drawPath(
        path = trimmed,
        color = color,
        style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}
