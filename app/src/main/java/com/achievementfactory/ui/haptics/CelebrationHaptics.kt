package com.achievementfactory.ui.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.achievementfactory.domain.tier.Tier
import kotlinx.coroutines.delay

/**
 * The haptic vocabulary of the app (CELEB-05), from lightest to strongest. Kept as our own enum —
 * mapped to Compose's [HapticFeedbackType] only at the edge, in [toCompose] — so the escalation
 * rules below are plain data that JVM tests can check.
 */
enum class Haptic { TICK, TOGGLE_ON, TOGGLE_OFF, THRESHOLD, CONFIRM, HEAVY }

/** One pulse of a pattern: wait [delayMs] after the previous pulse, then play [haptic]. */
data class HapticStep(val delayMs: Long, val haptic: Haptic)

/**
 * Tier-matched haptic patterns (CELEB-05). They escalate by adding pulses, never by switching to a
 * different mechanism: every pulse is a system haptic played through the view, which needs no
 * permission at all — this app declares none (LocalOnlyBoundaryTest), so the platform `Vibrator`
 * and its custom waveforms, which require `VIBRATE`, are deliberately not used. Haptics also follow
 * the user's own "touch feedback" system setting automatically, which a raw vibrator would not.
 *
 * Every delay here is a celebration timing value; it lives in this table rather than inline, the
 * same rule `MotionSpecs` applies to animation timings.
 */
object CelebrationHaptics {

    /** A single +1 tap that doesn't complete anything — the lightest tick. */
    val increment: List<HapticStep> = listOf(HapticStep(0, Haptic.TICK))

    /** Ticking / unticking a todo. */
    val todoTicked: List<HapticStep> = listOf(HapticStep(0, Haptic.TOGGLE_ON))
    val todoUnticked: List<HapticStep> = listOf(HapticStep(0, Haptic.TOGGLE_OFF))

    /**
     * A completion or medal, by tier: no ladder and Bronze are one confirm; Silver adds a
     * threshold pulse; Gold adds a third, heavier one.
     */
    fun forTier(tier: Tier?): List<HapticStep> = when (tier) {
        null, Tier.BRONZE -> listOf(HapticStep(0, Haptic.CONFIRM))
        Tier.SILVER -> listOf(HapticStep(0, Haptic.CONFIRM), HapticStep(90, Haptic.THRESHOLD))
        Tier.GOLD -> listOf(
            HapticStep(0, Haptic.CONFIRM),
            HapticStep(90, Haptic.THRESHOLD),
            HapticStep(110, Haptic.HEAVY),
        )
    }

    /** The whole tab cleared (CELEB-03) — the longest, strongest pattern in the app. */
    val fullTab: List<HapticStep> = listOf(
        HapticStep(0, Haptic.HEAVY),
        HapticStep(120, Haptic.CONFIRM),
        HapticStep(100, Haptic.CONFIRM),
        HapticStep(140, Haptic.HEAVY),
    )
}

/** Plays [pattern] on [this] haptic channel, pulse by pulse. Suspends for the pattern's length. */
suspend fun HapticFeedback.play(pattern: List<HapticStep>) {
    pattern.forEach { step ->
        if (step.delayMs > 0) delay(step.delayMs)
        performHapticFeedback(step.haptic.toCompose())
    }
}

/** Plays [pattern] once per distinct [key] — the composable form, for one-shot celebration events. */
@Composable
fun PlayHaptics(key: Any, pattern: List<HapticStep>) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(key) { haptics.play(pattern) }
}

private fun Haptic.toCompose(): HapticFeedbackType = when (this) {
    Haptic.TICK -> HapticFeedbackType.SegmentTick
    Haptic.TOGGLE_ON -> HapticFeedbackType.ToggleOn
    Haptic.TOGGLE_OFF -> HapticFeedbackType.ToggleOff
    Haptic.THRESHOLD -> HapticFeedbackType.GestureThresholdActivate
    Haptic.CONFIRM -> HapticFeedbackType.Confirm
    Haptic.HEAVY -> HapticFeedbackType.LongPress
}
