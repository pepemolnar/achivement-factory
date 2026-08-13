package com.achievementfactory.ui.achievement

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.achievementfactory.ui.theme.Spacing

/**
 * The accent-coloured progress ring around an achievement's icon (D-09) — not pips, not a bar.
 * Sweeps clockwise from 12 o'clock. Takes a plain [Float] and animates nothing in this plan —
 * plan 01-04 animates the per-tap sweep and plan 01-07 animates the completion close, both by
 * supplying a different value over time, not by changing this composable's contract.
 */
@Composable
fun ProgressRing(
    progress: Float,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val strokeWidth = Spacing.ringStrokeWidth
    val diameter = Spacing.iconClusterSize + (Spacing.xs * 2) // 2dp outset on every side
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

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

        if (progress > 0f) {
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx),
            )
        }
    }
}
