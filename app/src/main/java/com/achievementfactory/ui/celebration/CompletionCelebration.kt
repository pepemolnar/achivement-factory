package com.achievementfactory.ui.celebration

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
 */
data class CelebrationVisualState(
    val ringProgress: Float = 1f,
    val checkmarkProgress: Float = 1f,
    val checkScale: Float = 1f,
    val flameScale: Float = 1f,
    val flameFlash: Float = 0f,
)

/**
 * Drives the four-stage completion sequence (D-21) off one coroutine, sequenced rather than
 * simultaneous: the ring closes at t=0, the checkmark grows and pulses at
 * [MotionSpecs.CHECK_MORPH_DELAY_MS], the streak flame reacts at [MotionSpecs.FLAME_DELAY_MS] —
 * the achievement's first-ever flame plays the bouncier unlock treatment (D-08, D-22) — then
 * [CelebrationStage.SETTLED] fires once every stage's own animation has actually finished, never
 * a fixed guess (D-06). [onStage] is what a caller (`AchievementCard`/`DailyScreen`) gates the
 * Done-section sink on; [onCelebrationEvent] is the Phase 5 attachment point (D-24) — a no-op by
 * default, invoked with the same stages [onStage] receives, so wiring a later effect there
 * touches no code in this file.
 *
 * Neither [content]'s `ProgressRing` nor `StreakFlameBadge` own any animation clock of their
 * own — every value they render comes from the [CelebrationVisualState] this composable computes,
 * every spec is read from [MotionSpecs], and [MotionSpecs.resolve]/[MotionSpecs.resolveDelay] are
 * the single accessors that collapse everything through the reduced-motion path when the system
 * setting is on (D-25).
 */
@Composable
fun CompletionCelebration(
    event: CelebrationEvent.Completed?,
    accent: Color,
    onStage: (CelebrationStage) -> Unit,
    onCelebrationEvent: (CelebrationStage) -> Unit = {},
    content: @Composable (CelebrationVisualState) -> Unit,
) {
    val reducedMotion = rememberReducedMotion()

    val ringProgress = remember(event) { Animatable(0f) }
    val checkmarkProgress = remember(event) { Animatable(0f) }
    val checkScale = remember(event) { Animatable(1f) }
    val flameScale = remember(event) { Animatable(if (event?.isFirstFlame == true) 0f else 1f) }
    val flameFlash = remember(event) { Animatable(0f) }

    LaunchedEffect(event) {
        if (event == null) return@LaunchedEffect

        val ringSpec = MotionSpecs.resolve(MotionSpecs.ringClose, reducedMotion)
        val checkSpec = MotionSpecs.resolve(MotionSpecs.checkMorph, reducedMotion)
        val checkDelay = MotionSpecs.resolveDelay(MotionSpecs.CHECK_MORPH_DELAY_MS, reducedMotion)
        val flameDelay = MotionSpecs.resolveDelay(MotionSpecs.FLAME_DELAY_MS, reducedMotion)

        coroutineScope {
            launch {
                ringProgress.animateTo(1f, ringSpec)
                onStage(CelebrationStage.RING_CLOSED)
                onCelebrationEvent(CelebrationStage.RING_CLOSED)
            }
            launch {
                delay(checkDelay)
                checkmarkProgress.animateTo(1f, checkSpec)
                checkScale.animateTo(1.15f, checkSpec)
                checkScale.animateTo(1f, checkSpec)
                onStage(CelebrationStage.CHECK_MORPHED)
                onCelebrationEvent(CelebrationStage.CHECK_MORPHED)
            }
            launch {
                delay(flameDelay)
                if (event.isFirstFlame) {
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
        ),
    )
}
