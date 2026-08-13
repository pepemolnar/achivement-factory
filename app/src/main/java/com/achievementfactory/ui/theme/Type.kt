package com.achievementfactory.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Exactly 4 sizes, exactly 2 weights (Regular 400, SemiBold 600) — per 01-UI-SPEC.md
// "Typography" table. No other role or weight is used anywhere in this phase.

private val BodyRegular = FontWeight.Normal   // 400
private val SemiBold = FontWeight.W600        // 600

/** 16sp/400, line height 1.5 — achievement name, body copy, form input, empty-state text. */
val BodyStyle = TextStyle(
    fontWeight = BodyRegular,
    fontSize = 16.sp,
    lineHeight = 24.sp, // 16sp * 1.5
)

/** 12sp/600, line height 1.3 — flame badge number, streak captions, snackbar text, section header. */
val LabelStyle = TextStyle(
    fontWeight = SemiBold,
    fontSize = 12.sp,
    lineHeight = 15.6.sp, // 12sp * 1.3
)

/** 22sp/600, line height 1.2 — screen titles, detail-screen achievement name. */
val HeadingStyle = TextStyle(
    fontWeight = SemiBold,
    fontSize = 22.sp,
    lineHeight = 26.4.sp, // 22sp * 1.2
)

/** 28sp/600, line height 1.2 — the best-streak numeral, the one large number in this phase. */
val DisplayStyle = TextStyle(
    fontWeight = SemiBold,
    fontSize = 28.sp,
    lineHeight = 33.6.sp, // 28sp * 1.2
)

/**
 * Maps the four UI-SPEC roles onto Material3's baseline [Typography] slots. Only the slots
 * this app actually uses are overridden; everything else falls back to Material3 defaults
 * (Roboto Flex, system default — see UI-SPEC "Font").
 */
val AppTypography = Typography(
    bodyLarge = BodyStyle,
    labelLarge = LabelStyle,
    headlineSmall = HeadingStyle,
    displaySmall = DisplayStyle,
)
