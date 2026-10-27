package com.achievementfactory.ui.haptics

import com.achievementfactory.domain.tier.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CELEB-05: haptics escalate with the tier and peak at the full-tab moment — pure JUnit. */
class CelebrationHapticsTest {

    private fun strength(pattern: List<HapticStep>): Int = pattern.sumOf { it.haptic.ordinal + 1 }

    @Test
    fun patternsEscalate_fromATap_throughTheTiers_toTheFullTab() {
        val ladder = listOf(
            CelebrationHaptics.increment,
            CelebrationHaptics.forTier(Tier.BRONZE),
            CelebrationHaptics.forTier(Tier.SILVER),
            CelebrationHaptics.forTier(Tier.GOLD),
            CelebrationHaptics.fullTab,
        )
        ladder.zipWithNext().forEach { (lower, higher) ->
            assertTrue("each step must be stronger than the one before", strength(higher) > strength(lower))
        }
    }

    @Test
    fun noLadder_feelsLikeBronze() {
        assertEquals(CelebrationHaptics.forTier(Tier.BRONZE), CelebrationHaptics.forTier(null))
    }

    @Test
    fun everyPattern_startsImmediately_andHasNoNegativeDelays() {
        val all = listOf(
            CelebrationHaptics.increment,
            CelebrationHaptics.todoTicked,
            CelebrationHaptics.todoUnticked,
            CelebrationHaptics.fullTab,
        ) + listOf(null, Tier.BRONZE, Tier.SILVER, Tier.GOLD).map { CelebrationHaptics.forTier(it) }
        all.forEach { pattern ->
            assertEquals(0L, pattern.first().delayMs)
            assertTrue(pattern.all { it.delayMs >= 0 })
        }
    }
}
