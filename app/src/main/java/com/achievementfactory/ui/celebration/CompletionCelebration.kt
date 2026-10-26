package com.achievementfactory.ui.celebration

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.achievementfactory.ui.theme.MotionSpecs
import com.achievementfactory.ui.theme.rememberReducedMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The animated values [CompletionCelebration]'s `content` renders with at any point during the
 * sequence. The defaults are the settled "nothing playing" state — the same values a completed
 * card renders statically outside a celebration (D-11's "reached by animation rather than by a
 * swap" describes the *transition into* this state, not a different resting state).
 *
 * [burstProgress] runs 0 → 1 across the radial burst; at either end nothing is drawn, so the
 * resting value 0 and the finished value 1 both mean "no particles". [tierBadgeScale] drives the
 * tier badge's pop; 1 is its resting size.
 */
data class CelebrationVisualState(
    val ringProgress: Float = 1f,
    val checkmarkProgress: Float = 1f,
    val checkScale: Float = 1f,
    val flameScale: Float = 1f,
    val flameFlash: Float = 0f,
    val burstProgress: Float = 0f,
    val tierBadgeScale: Float = 1f,
)

/**
 * Drives a card's celebration sequence off one coroutine, sequenced rather than simultaneous.
 *
 * For [CelebrationEvent.Completed] (D-21): the ring closes at t=0, the checkmark grows and pulses
 * at [MotionSpecs.CHECK_MORPH_DELAY_MS] — alongside it the tier badge pops in and the radial
 * burst fires — and the streak flame reacts at [MotionSpecs.FLAME_DELAY_MS], with the
 * achievement's first-ever flame playing the bouncier unlock treatment (D-08, D-22).
 *
 * For [CelebrationEvent.TierUpgraded] (CELEB-02): the card is already done, so the ring and check
 * start settled and the flame is left alone — only the check pulse, the badge pop (into the new
 * tier) and the burst play, starting at [MotionSpecs.TIER_UPGRADE_DELAY_MS].
 *
 * Intensity is never a separate code path (CELEB-01): every peak, particle count and duration is
 * read from [MotionSpecs]'s tier table using [CelebrationEvent.tier], so Bronze, Silver and Gold
 * run this exact sequence with escalating numbers, and a no-ladder achievement (`tier == null`)
 * plays precisely the Phase 1 sequence with no burst at all.
 *
 * [CelebrationStage.SETTLED] fires once every stage's own animation has actually finished, never
 * a fixed guess (D-06). [onStage] is what a caller gates the Done-section sink on;
 * [onCelebrationEvent] is the Phase 5 attachment point (D-24) — a no-op by default, invoked with
 * the same stages [onStage] receives, so wiring a later effect there touches no code in this file.
 *
 * Every spec goes through [MotionSpecs.resolve]/[MotionSpecs.resolveDelay], the single accessors
 * that collapse everything through the reduced-motion path when the system setting is on (D-25).
 */
@Composable
fun CompletionCelebration(
    event: CelebrationEvent?,
    accent: Color,
    onStage: (CelebrationStage) -> Unit,
    onCelebrationEvent: (CelebrationStage) -> Unit = {},
    content: @Composable (CelebrationVisualState) -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val completed = event as? CelebrationEvent.Completed
    val startsSettled = event is CelebrationEvent.TierUpgraded

    val ringProgress = remember(event) { Animatable(if (startsSettled) 1f else 0f) }
    val checkmarkProgress = remember(event) { Animatable(if (startsSettled) 1f else 0f) }
    val checkScale = remember(event) { Animatable(1f) }
    val flameScale = remember(event) { Animatable(if (completed?.isFirstFlame == true) 0f else 1f) }
    val flameFlash = remember(event) { Animatable(0f) }
    val burstProgress = remember(event) { Animatable(0f) }
    val tierBadgeScale = remember(event) { Animatable(if (event?.tier != null) 0f else 1f) }

    LaunchedEffect(event) {
        if (event == null) return@LaunchedEffect

        val tier = event.tier
        val ringSpec = MotionSpecs.resolve(MotionSpecs.ringClose, reducedMotion)
        val checkSpec = MotionSpecs.resolve(MotionSpecs.checkMorph, reducedMotion)
        val burstSpec = MotionSpecs.resolve(MotionSpecs.burst(tier), reducedMotion)
        val badgeSpec = MotionSpecs.resolve(MotionSpecs.tierBadgePop(tier), reducedMotion)
        val pulsePeak = MotionSpecs.checkPulsePeak(tier)
        val accentDelay = MotionSpecs.resolveDelay(
            if (startsSettled) MotionSpecs.TIER_UPGRADE_DELAY_MS else MotionSpecs.CHECK_MORPH_DELAY_MS,
            reducedMotion,
        )
        val flameDelay = MotionSpecs.resolveDelay(MotionSpecs.FLAME_DELAY_MS, reducedMotion)

        coroutineScope {
            if (!startsSettled) {
                launch {
                    ringProgress.animateTo(1f, ringSpec)
                    onStage(CelebrationStage.RING_CLOSED)
                    onCelebrationEvent(CelebrationStage.RING_CLOSED)
                }
            }
            launch {
                delay(accentDelay)
                if (tier != null) {
                    launch {
                        tierBadgeScale.animateTo(MotionSpecs.TIER_BADGE_POP_PEAK, badgeSpec)
                        tierBadgeScale.animateTo(1f, badgeSpec)
                    }
                }
                if (MotionSpecs.burstParticleCount(tier) > 0) {
                    launch { burstProgress.animateTo(1f, burstSpec) }
                }
                checkmarkProgress.animateTo(1f, checkSpec)
                checkScale.animateTo(pulsePeak, checkSpec)
                checkScale.animateTo(1f, checkSpec)
                onStage(CelebrationStage.CHECK_MORPHED)
                onCelebrationEvent(CelebrationStage.CHECK_MORPHED)
            }
            if (completed != null) {
                launch {
                    delay(flameDelay)
                    if (completed.isFirstFlame) {
                        val unlockSpec = MotionSpecs.resolve(MotionSpecs.flameUnlock, reducedMotion)
                        launch {
                            flameFlash.animateTo(1f, unlockSpec)
                            flameFlash.animateTo(0f, unlockSpec)
                        }
                        flameScale.animateTo(1.3f, unlockSpec)
                        flameScale.animateTo(1f, unlockSpec)
                    } else {
                        val tickSpec = MotionSpecs.resolve(MotionSpecs.flameTick, reducedMotion)
                        flameScale.animateTo(1.15f, tickSpec)
                        flameScale.animateTo(1f, tickSpec)
                    }
                    onStage(CelebrationStage.FLAME_TICKED)
                    onCelebrationEvent(CelebrationStage.FLAME_TICKED)
                }
            }
        }

        onStage(CelebrationStage.SETTLED)
        onCelebrationEvent(CelebrationStage.SETTLED)
    }

    content(
        CelebrationVisualState(
            ringProgress = ringProgress.value,
            checkmarkProgress = checkmarkProgress.value,
            checkScale = checkScale.value,
            flameScale = flameScale.value,
            flameFlash = flameFlash.value,
            burstProgress = burstProgress.value,
            tierBadgeScale = tierBadgeScale.value,
        ),
    )
}
