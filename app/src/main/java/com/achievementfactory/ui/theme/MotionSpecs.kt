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
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.achievementfactory.domain.tier.Tier

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

    // --- Tier scaling (CELEB-01, CELEB-02) ----------------------------------------------------
    //
    // A celebration's intensity is a pure function of the tier it celebrates, resolved here so
    // that escalation is a matter of *spec*, never a new code path: `CompletionCelebration` runs
    // the same sequence for every tier and only reads different numbers out of this table. Every
    // spec below still goes through [resolve]/[resolveDelay], so each tier inherits reduced-motion
    // handling for free (D-25). `null` means "no ladder" and plays exactly the Phase 1 treatment.

    /** Peak of the checkmark's scale pulse (1.0 → peak → 1.0). The no-ladder value is Phase 1's. */
    fun checkPulsePeak(tier: Tier?): Float = when (tier) {
        null -> 1.15f
        Tier.BRONZE -> 1.18f
        Tier.SILVER -> 1.25f
        Tier.GOLD -> 1.35f
    }

    /** How many particles the radial burst throws; 0 means no burst (the no-ladder default). */
    fun burstParticleCount(tier: Tier?): Int = when (tier) {
        null -> 0
        Tier.BRONZE -> 6
        Tier.SILVER -> 10
        Tier.GOLD -> 16
    }

    /**
     * How far, from the icon centre, the burst's particles travel. Kept inside the card's 72dp
     * minimum height so even Gold reads as a burst rather than being clipped flat by the card.
     */
    fun burstRadius(tier: Tier?): Dp = when (tier) {
        null -> 0.dp
        Tier.BRONZE -> 26.dp
        Tier.SILVER -> 32.dp
        Tier.GOLD -> 38.dp
    }

    /** The burst's travel-and-fade, linear progress 0 → 1 (easing is applied when drawing). */
    fun burst(tier: Tier?): AnimationSpec<Float> = when (tier) {
        null, Tier.BRONZE -> tween(durationMillis = 450, easing = LinearEasing)
        Tier.SILVER -> tween(durationMillis = 550, easing = LinearEasing)
        Tier.GOLD -> tween(durationMillis = 700, easing = LinearEasing)
    }

    /** The tier badge's pop-in (0 → overshoot → 1). Gold is the bounciest. */
    fun tierBadgePop(tier: Tier?): AnimationSpec<Float> = when (tier) {
        Tier.GOLD -> spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow)
        Tier.SILVER -> spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        null, Tier.BRONZE -> spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    }

    /** How far the tier badge overshoots on its pop before settling back to 1.0. */
    const val TIER_BADGE_POP_PEAK = 1.3f

    /**
     * Stage offset for a mid-period tier *upgrade* (CELEB-02): the card is already done, so there
     * is no ring to close — the badge and burst start at once and the check pulses alongside.
     */
    const val TIER_UPGRADE_DELAY_MS = 0L

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
 * Reads the system "Remove animations" setting — `0f` on the animator duration scale means it is
 * on (01-UI-SPEC.md "Reduced motion"). This is the only place in the codebase permitted to read
 * that setting (D-25, grep-gated); every celebration composable calls this once and threads the
 * result through [MotionSpecs.resolve]/[MotionSpecs.resolveDelay]/[MotionSpecs.resolvePlacement]
 * rather than reading the system setting itself.
 *
 * The `remember` is load-bearing, not just naming convention. `CompletionCelebration` calls this
 * from a composable body that reads five `Animatable.value`s, so that body recomposes on every
 * animation frame — without the cache each of those frames would perform a `ContentResolver`
 * lookup on the main thread, and `DailyList` would do the same on every list recomposition.
 * Keyed on the resolver so a different `Context` re-reads.
 *
 * The value is therefore sampled once per composition and does not react live to the setting
 * being toggled while a screen is open. That is deliberate: mid-animation spec swaps are worse
 * than a stale read, and the setting is re-sampled the next time the screen is composed. If live
 * reaction is ever wanted, back it with a `ContentObserver` in a `produceState`/`DisposableEffect`
 * rather than by removing the cache and polling every frame.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(
            resolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}
