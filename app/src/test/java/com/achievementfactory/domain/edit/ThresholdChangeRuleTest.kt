package com.achievementfactory.domain.edit

import com.achievementfactory.domain.tier.TierLadder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** ACH-04/ACH-05 as revised: the target (Gold) never goes down; Bronze and Silver are free. */
class ThresholdChangeRuleTest {

    private fun check(oldTarget: Int, oldLadder: TierLadder?, newTarget: Int, newLadder: TierLadder?) =
        ThresholdChangeRule.check(oldTarget, oldLadder, newTarget, newLadder)

    @Test
    fun plainTarget_mayRiseOrStay_butNotFall() {
        assertNull(check(3, null, 3, null))
        assertNull(check(3, null, 5, null))
        assertEquals(ThresholdChangeError.DoneLowered(3), check(3, null, 2, null))
    }

    @Test
    fun addingMedals_belowTheExistingTarget_isAllowed_whenTheTargetBecomesGold() {
        assertNull(check(10, null, 0, TierLadder(bronze = 3, silver = 6, gold = 10)))
        assertNull(check(10, null, 0, TierLadder(bronze = null, silver = null, gold = 12)))
        assertEquals(ThresholdChangeError.DoneLowered(10), check(10, null, 0, TierLadder(3, 6, 9)))
    }

    @Test
    fun bronzeAndSilver_mayBeLoweredRaisedAddedOrRemoved() {
        val old = TierLadder(bronze = 4, silver = 6, gold = 8) // stored target = 4 (the streak threshold)
        assertNull(check(4, old, 0, TierLadder(1, 2, 8)))
        assertNull(check(4, old, 0, TierLadder(null, 7, 8)))
        assertNull(check(4, old, 0, TierLadder(null, null, 8)))
        assertNull(check(4, old, 0, TierLadder(5, 7, 9)))
    }

    @Test
    fun goldMayRise_butNotFall() {
        val old = TierLadder(bronze = null, silver = 3, gold = 5)
        assertNull(check(3, old, 0, TierLadder(null, 3, 6)))
        assertEquals(ThresholdChangeError.DoneLowered(5), check(3, old, 0, TierLadder(null, 3, 4)))
    }

    @Test
    fun switchingMedalsOff_keepsComparingAgainstGold() {
        val old = TierLadder(bronze = 2, silver = 4, gold = 6)
        assertNull(check(2, old, 6, null))
        assertNull(check(2, old, 9, null))
        assertEquals(ThresholdChangeError.DoneLowered(6), check(2, old, 5, null))
    }
}
