package com.achievementfactory.domain.edit

import com.achievementfactory.domain.tier.Tier
import com.achievementfactory.domain.tier.TierLadder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** ACH-04/ACH-05: thresholds can go up, never down — pure JUnit. */
class ThresholdChangeRuleTest {

    private fun check(oldTarget: Int, oldLadder: TierLadder?, newTarget: Int, newLadder: TierLadder?) =
        ThresholdChangeRule.check(oldTarget, oldLadder, newTarget, newLadder)

    @Test
    fun plainTarget_mayRiseOrStay_butNotFall() {
        assertNull(check(3, null, 3, null))
        assertNull(check(3, null, 5, null))
        assertEquals(ThresholdChangeError.TargetLowered, check(3, null, 2, null))
    }

    @Test
    fun addingALadder_isAllowedWhenItsLowestMedalIsAtLeastTheOldTarget() {
        assertNull(check(3, null, 1, TierLadder(bronze = 3, silver = 5, gold = 8)))
        assertNull(check(3, null, 1, TierLadder(bronze = null, silver = null, gold = 3)))
        assertEquals(
            ThresholdChangeError.StreakMadeEasier,
            check(3, null, 1, TierLadder(bronze = 2, silver = 5, gold = 8)),
        )
    }

    @Test
    fun removingALadder_isRefused() {
        assertEquals(ThresholdChangeError.LadderRemoved, check(2, TierLadder(null, 2, 4), 99, null))
    }

    @Test
    fun medals_mayRise_butNotFallOrDisappear() {
        val old = TierLadder(bronze = 2, silver = 4, gold = 6)
        assertNull(check(2, old, 0, TierLadder(3, 5, 9)))
        assertEquals(ThresholdChangeError.TierLowered(Tier.SILVER), check(2, old, 0, TierLadder(2, 3, 6)))
        assertEquals(ThresholdChangeError.TierRemoved(Tier.BRONZE), check(2, old, 0, TierLadder(null, 4, 6)))
    }

    @Test
    fun addingAMedalAboveTheLowest_isAllowed_butBelowItIsNot() {
        val goldOnly = TierLadder(bronze = null, silver = null, gold = 5)
        assertEquals(
            "adding Silver below Gold lowers the streak threshold",
            ThresholdChangeError.StreakMadeEasier,
            check(5, goldOnly, 0, TierLadder(null, 3, 5)),
        )
        assertNull("raising Gold and adding Silver at the old Gold count keeps the streak as hard", check(5, goldOnly, 0, TierLadder(null, 5, 8)))
    }
}
