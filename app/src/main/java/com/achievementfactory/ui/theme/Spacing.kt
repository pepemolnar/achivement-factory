package com.achievementfactory.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Named dp tokens from 01-UI-SPEC.md "Spacing Scale" (all multiples of 4) plus the
 * component-dimension constants from the "Shape & Elevation" table. Component dimensions are
 * exempt from the 4-multiple rule per the spec's own stated exceptions (FAB diameter, ring
 * stroke width).
 */
object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp
    val xxxl: Dp = 64.dp

    val cardCornerRadius: Dp = 16.dp
    val cardMinHeight: Dp = 72.dp
    val iconClusterSize: Dp = 40.dp
    val ringStrokeWidth: Dp = 3.dp
    val flameBadgeSize: Dp = 16.dp
    val burstParticleSize: Dp = 6.dp
    val tierBadgeIconSize: Dp = 14.dp
}
