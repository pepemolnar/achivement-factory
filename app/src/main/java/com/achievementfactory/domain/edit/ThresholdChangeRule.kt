package com.achievementfactory.domain.edit

import com.achievementfactory.domain.tier.Tier
import com.achievementfactory.domain.tier.TierLadder

/**
 * Why an edit to an achievement's thresholds was refused (ACH-05): every case is a way the edit
 * would make the streak — or "done" — easier to reach than it was.
 */
sealed interface ThresholdChangeError {
    /** A plain counter target went down. */
    data object TargetLowered : ThresholdChangeError

    /** An existing medal's count went down. */
    data class TierLowered(val tier: Tier) : ThresholdChangeError

    /** An existing medal was removed from the ladder. */
    data class TierRemoved(val tier: Tier) : ThresholdChangeError

    /** The whole ladder was switched off. */
    data object LadderRemoved : ThresholdChangeError

    /** The count that keeps the streak would go down — e.g. adding a medal below the lowest one. */
    data object StreakMadeEasier : ThresholdChangeError
}

/**
 * The single rule behind ACH-04/ACH-05: an achievement's thresholds may be raised, and medals may
 * be added *above* the count that keeps the streak, but nothing may get easier. Pure Kotlin, so
 * the form can show the error before saving and the repository enforces the very same function
 * inside its write transaction.
 *
 * What "easier" means, precisely:
 * - with no ladder before or after, the target may not go down;
 * - adding a ladder to a plain achievement is allowed when its lowest medal (the streak
 *   threshold, TIER-06) is at least the old target — Gold is then necessarily higher still;
 * - an existing ladder can't be removed, and none of its medals can be removed or lowered;
 * - a medal may be added to an existing ladder only if the streak threshold does not drop, which
 *   in practice rules out adding a medal *below* the current lowest one.
 */
object ThresholdChangeRule {

    fun check(
        oldTarget: Int,
        oldLadder: TierLadder?,
        newTarget: Int,
        newLadder: TierLadder?,
    ): ThresholdChangeError? {
        if (oldLadder == null) {
            if (newLadder == null) {
                return if (newTarget < oldTarget) ThresholdChangeError.TargetLowered else null
            }
            return if (newLadder.streakThreshold < oldTarget) ThresholdChangeError.StreakMadeEasier else null
        }
        if (newLadder == null) return ThresholdChangeError.LadderRemoved

        for (tier in oldLadder.tiers) {
            val oldThreshold = oldLadder.thresholdOf(tier) ?: continue
            val newThreshold = newLadder.thresholdOf(tier) ?: return ThresholdChangeError.TierRemoved(tier)
            if (newThreshold < oldThreshold) return ThresholdChangeError.TierLowered(tier)
        }
        if (newLadder.streakThreshold < oldLadder.streakThreshold) return ThresholdChangeError.StreakMadeEasier
        return null
    }
}
