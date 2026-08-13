package com.achievementfactory.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset

/**
 * Every duration, spring and stage-offset value the completion celebration sequence uses
 * (01-UI-SPEC.md "Motion & Celebration Sequence") lives here, so no `tween(...)`/`spring(...)`
 * literal is ever written inline in `ui/achievement/` or `ui/celebration/` (D-21 through D-25).
 * [resolve]/[resolveDelay]/[resolvePlacement] are the accessors every spec and stage delay must
 * be read through — they substitute [reducedCollapse]/[reducedCollapsePlacement] (and collapse
 * delays to zero) when [rememberReducedMotion] reports the system's reduced-motion setting is on.
 * This is the single shared code path Phase 2's tier variants and Phase 5's full-tab moment are
 * required to reuse rather than reimplement (D-25).
 */
object MotionSpecs {

    /** Per-tap ring sweep (not a completion) — the same spec plan 01-04 established. */
    val ringSweep: AnimationSpec<Float> = tween(durationMillis = 150, easing = FastOutSlowInEasing)

    /** Stage 1 — the ring closes, starting at t=0. */
    val ringClose: AnimationSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

    /** Stage 2 — the checkmark grows and scale-pulses (1.0 → 1.15 → 1.0). */
    val checkMorph: AnimationSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

    /** Stage 3, ordinary tick — every streak update after the achievement's first ever flame. */
    val flameTick: AnimationSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

    /** Stage 3, first-ever appearance at streak 1 — visibly bouncier than [flameTick] (D-08, D-22). */
    val flameUnlock: AnimationSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)

    /** Stage 4 — the card's reorder into the Done section, gated on [CelebrationStage].SETTLED. */
    val cardSink: FiniteAnimationSpec<IntOffset> =
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)

    /** What every [Float]-valued spec above collapses to under reduced motion. */
    val reducedCollapse: AnimationSpec<Float> = tween(durationMillis = 90, easing = LinearEasing)

    /** What [cardSink] collapses to under reduced motion. */
    val reducedCollapsePlacement: FiniteAnimationSpec<IntOffset> =
        tween(durationMillis = 90, easing = LinearEasing)

    const val CHECK_MORPH_DELAY_MS = 250L
    const val FLAME_DELAY_MS = 400L

    /**
     * Hard ceiling on how long a just-completed achievement may be held out of the Done section
     * waiting for its celebration to report [CelebrationStage].SETTLED (D-06). The sequence itself
     * finishes well inside this — [FLAME_DELAY_MS] plus the flame spring's settle — so on the happy
     * path `onCelebrationSettled` always wins the race and this value is never reached.
     *
     * It exists because the settle callback is tied to the celebrating card's *composition*: an
     * `Activity` recreation mid-animation, the card scrolling out of the `LazyColumn`, or
     * navigating away all cancel the sequence before SETTLED. Without a ceiling the achievement
     * would stay pinned above the Done divider for the ViewModel's whole lifetime, with no way for
     * the user to clear it. List correctness must never depend on an animation completing.
     */
    const val CELEBRATION_MAX_HOLD_MS = 2_000L

    /** The one accessor every [Float]-valued celebration spec must be read through. */
    fun resolve(spec: AnimationSpec<Float>, reducedMotion: Boolean): AnimationSpec<Float> =
        if (reducedMotion) reducedCollapse else spec

    /** The one accessor [cardSink] must be read through. */
    fun resolvePlacement(spec: FiniteAnimationSpec<IntOffset>, reducedMotion: Boolean): FiniteAnimationSpec<IntOffset> =
        if (reducedMotion) reducedCollapsePlacement else spec

    /** Collapses a stage-start delay to zero under reduced motion; otherwise returns it unchanged. */
    fun resolveDelay(delayMs: Long, reducedMotion: Boolean): Long = if (reducedMotion) 0L else delayMs
}

/**
 * Reads the system "Remove animations" setting once per composition — `0f` on the animator
 * duration scale means it is on (01-UI-SPEC.md "Reduced motion"). This is the only place in the
 * codebase permitted to read that setting (D-25, grep-gated); every celebration composable calls
 * this once and threads the result through [MotionSpecs.resolve]/[MotionSpecs.resolveDelay]/
 * [MotionSpecs.resolvePlacement] rather than reading the system setting itself.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
}
