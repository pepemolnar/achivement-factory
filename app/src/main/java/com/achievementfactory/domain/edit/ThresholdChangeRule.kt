package com.achievementfactory.domain.edit

import com.achievementfactory.domain.tier.TierLadder

/**
 * Why an edit to an achievement's thresholds was refused (ACH-05, as revised by the owner on
 * 2026-10-26): the only thing an edit may never do is lower the count that makes the achievement
 * *done* — its target, which is Gold whenever medals are on.
 */
sealed interface ThresholdChangeError {
    /** The done threshold (the plain target, or Gold) would go below [previous]. */
    data class DoneLowered(val previous: Int) : ThresholdChangeError
}

/**
 * The single rule behind ACH-04/ACH-05. The owner's definition: "the target should always be the
 * gold; anything is allowed for silver and bronze which is lower than the target." So:
 *
 * - The done threshold — the plain target with no ladder, Gold with one (TIER-06) — may rise or
 *   stay, never fall. Switching medals on or off keeps comparing the same number: a plain target
 *   of 10 becomes Gold 10, and Gold 10 becomes a plain target of 10.
 * - Bronze and Silver are free: they can be added, raised, lowered or removed, as long as the
 *   ladder itself is valid (below Gold, strictly increasing — [TierLadder] enforces that).
 *
 * Lowering Bronze or Silver does make the streak cheaper to keep from then on; that is the
 * owner's explicit choice. Closed periods are never affected either way, because each one keeps
 * the result it was recorded with (TRACK-10).
 *
 * Pure Kotlin, so the form can explain a refusal before saving and the repository enforces the
 * very same function inside its write transaction.
 */
object ThresholdChangeRule {

    fun check(
        oldTarget: Int,
        oldLadder: TierLadder?,
        newTarget: Int,
        newLadder: TierLadder?,
    ): ThresholdChangeError? {
        val oldDone = oldLadder?.completeThreshold ?: oldTarget
        val newDone = newLadder?.completeThreshold ?: newTarget
        return if (newDone < oldDone) ThresholdChangeError.DoneLowered(oldDone) else null
    }
}
