package com.achievementfactory.ui.celebration

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.achievementfactory.domain.tier.Tier
import com.achievementfactory.period.PeriodType
import com.achievementfactory.ui.haptics.CelebrationHaptics
import com.achievementfactory.ui.haptics.play
import com.achievementfactory.ui.theme.MotionSpecs
import com.achievementfactory.ui.theme.Spacing
import com.achievementfactory.ui.theme.color
import com.achievementfactory.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit

/**
 * The full-tab moment (CELEB-03): drawn over the whole tab when its last item is cleared —
 * visibly a different order of event from any single card's celebration. Two confetti bursts from
 * the lower corners plus a centred banner that pops in, then the overlay fades and calls
 * [onFinished]. It also plays the strongest haptic pattern in the app (CELEB-05).
 *
 * [token] identifies one occurrence; a new token restarts the sequence, the same token never
 * replays it. The caller holds it only for as long as this is on screen, so like every other
 * celebration it can't come back after rotation or a tab switch.
 *
 * Under reduced motion (CELEB-06) there is no confetti and no pop: the banner simply appears for
 * [MotionSpecs.FULL_TAB_REDUCED_DURATION_MS], and its fade collapses through
 * [MotionSpecs.resolve] like every other spec. The haptic pattern still plays — it isn't motion.
 */
@Composable
fun FullTabCelebration(
    token: Any,
    periodType: PeriodType,
    onFinished: () -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val haptics = LocalHapticFeedback.current
    val bannerScale = remember(token) { Animatable(if (reducedMotion) 1f else 0f) }
    val alpha = remember(token) { Animatable(1f) }

    // Metal colours plus the app accent: the full-tab burst reads as "every medal at once".
    val confettiColors = listOf(
        Tier.GOLD.color(),
        Tier.SILVER.color(),
        Tier.BRONZE.color(),
        MaterialTheme.colorScheme.primary,
    ).map { it.toArgb() }

    LaunchedEffect(token) {
        delay(MotionSpecs.resolveDelay(MotionSpecs.FULL_TAB_DELAY_MS, reducedMotion))
        launch { haptics.play(CelebrationHaptics.fullTab) }
        if (!reducedMotion) {
            bannerScale.animateTo(1f, MotionSpecs.fullTabBanner)
        }
        delay(
            if (reducedMotion) MotionSpecs.FULL_TAB_REDUCED_DURATION_MS else MotionSpecs.FULL_TAB_DURATION_MS,
        )
        alpha.animateTo(0f, MotionSpecs.resolve(MotionSpecs.fullTabFade, reducedMotion))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value },
        contentAlignment = Alignment.Center,
    ) {
        if (!reducedMotion) {
            KonfettiView(
                modifier = Modifier.fillMaxSize(),
                parties = remember(token) { fullTabParties(confettiColors) },
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = bannerScale.value
                    scaleY = bannerScale.value
                }
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(Spacing.cardCornerRadius),
                )
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(text = "All clear!", style = MaterialTheme.typography.displaySmall)
            Text(
                text = when (periodType) {
                    PeriodType.DAILY -> "Everything done for today."
                    PeriodType.WEEKLY -> "Everything done this week."
                    PeriodType.MONTHLY -> "Everything done this month."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Two upward bursts from the lower corners, angled toward the middle. */
private fun fullTabParties(colors: List<Int>): List<Party> {
    val emitter = Emitter(duration = MotionSpecs.FULL_TAB_EMIT_MS, TimeUnit.MILLISECONDS)
        .max(MotionSpecs.FULL_TAB_PARTICLES_PER_BURST)
    return listOf(
        Party(
            angle = 300, // up and to the right
            spread = 50,
            speed = 30f,
            maxSpeed = 55f,
            damping = 0.9f,
            colors = colors,
            position = Position.Relative(0.0, 0.9),
            emitter = emitter,
        ),
        Party(
            angle = 240, // up and to the left
            spread = 50,
            speed = 30f,
            maxSpeed = 55f,
            damping = 0.9f,
            colors = colors,
            position = Position.Relative(1.0, 0.9),
            emitter = emitter,
        ),
    )
}
