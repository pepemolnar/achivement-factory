package com.achievementfactory.domain.tier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JUnit — covers TIER-04 (downward fill), TIER-05 (strictly increasing), TIER-06 (done threshold). */
class TierLadderTest {

    private fun errorOf(bronze: Int?, silver: Int?, gold: Int?): TierLadderError? =
        (TierLadder.validate(bronze, silver, gold) as? TierLadderValidation.Invalid)?.error

    @Test
    fun goldOnly_isValid_andGoldIsTheDoneThreshold() {
        val ladder = TierLadder(bronze = null, silver = null, gold = 5)
        assertEquals(5, ladder.doneThreshold)
        assertEquals(Tier.GOLD, ladder.lowestTier)
        assertEquals(listOf(Tier.GOLD), ladder.tiers)
    }

    @Test
    fun silverAndGold_isValid_andSilverIsTheDoneThreshold() {
        val ladder = TierLadder(bronze = null, silver = 3, gold = 5)
        assertEquals(3, ladder.doneThreshold)
        assertEquals(Tier.SILVER, ladder.lowestTier)
    }

    @Test
    fun fullLadder_isValid_andBronzeIsTheDoneThreshold() {
        val ladder = TierLadder(bronze = 1, silver = 3, gold = 5)
        assertEquals(1, ladder.doneThreshold)
        assertEquals(listOf(Tier.BRONZE, Tier.SILVER, Tier.GOLD), ladder.tiers)
    }

    @Test
    fun missingGold_isRejected_whateverElseIsSet() {
        assertEquals(TierLadderError.MISSING_GOLD, errorOf(null, null, null))
        assertEquals(TierLadderError.MISSING_GOLD, errorOf(1, 2, null))
        assertEquals(TierLadderError.MISSING_GOLD, errorOf(null, 2, null))
    }

    @Test
    fun bronzeWithoutSilver_isRejectedAsSkippingALevel() {
        assertEquals(TierLadderError.SKIPPED_LEVEL, errorOf(1, null, 5))
    }

    @Test
    fun equalOrDecreasingThresholds_areRejected() {
        assertEquals(TierLadderError.NOT_INCREASING, errorOf(null, 5, 5))
        assertEquals(TierLadderError.NOT_INCREASING, errorOf(3, 3, 5))
        assertEquals(TierLadderError.NOT_INCREASING, errorOf(4, 2, 5))
        assertEquals(TierLadderError.NOT_INCREASING, errorOf(null, 7, 5))
    }

    @Test
    fun outOfRangeThresholds_areRejected() {
        assertEquals(TierLadderError.OUT_OF_RANGE, errorOf(0, 2, 5))
        assertEquals(TierLadderError.OUT_OF_RANGE, errorOf(null, null, 1000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun constructor_refusesAnInvalidLadder() {
        TierLadder(bronze = 1, silver = null, gold = 5)
    }

    @Test
    fun tierFor_returnsTheHighestTierReached() {
        val ladder = TierLadder(bronze = 2, silver = 4, gold = 6)
        assertNull(ladder.tierFor(0))
        assertNull(ladder.tierFor(1))
        assertEquals(Tier.BRONZE, ladder.tierFor(2))
        assertEquals(Tier.BRONZE, ladder.tierFor(3))
        assertEquals(Tier.SILVER, ladder.tierFor(4))
        assertEquals(Tier.GOLD, ladder.tierFor(6))
        assertEquals(Tier.GOLD, ladder.tierFor(60))
    }

    @Test
    fun tierFor_onAGoldOnlyLadder_jumpsStraightFromNothingToGold() {
        val ladder = TierLadder(bronze = null, silver = null, gold = 3)
        assertNull(ladder.tierFor(2))
        assertEquals(Tier.GOLD, ladder.tierFor(3))
    }

    @Test
    fun nextTierAbove_pointsAtTheNextUnreachedThreshold_andIsNullAtGold() {
        val ladder = TierLadder(bronze = 2, silver = 4, gold = 6)
        assertEquals(Tier.BRONZE to 2, ladder.nextTierAbove(0))
        assertEquals(Tier.SILVER to 4, ladder.nextTierAbove(2))
        assertEquals(Tier.GOLD to 6, ladder.nextTierAbove(5))
        assertNull(ladder.nextTierAbove(6))
    }

    @Test
    fun fromStoredColumns_isTolerant_ofNoLadderAndOfAnInvalidStoredCombination() {
        assertNull(TierLadder.fromStoredColumns(null, null, null))
        assertNull(TierLadder.fromStoredColumns(1, null, 5))
        assertTrue(TierLadder.fromStoredColumns(null, 2, 5) != null)
    }

    @Test
    fun fromStored_parsesTheSnapshottedColumn_andToleratesGarbage() {
        assertEquals(Tier.SILVER, Tier.fromStored("SILVER"))
        assertNull(Tier.fromStored(null))
        assertNull(Tier.fromStored("PLATINUM"))
    }
}
