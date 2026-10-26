package com.achievementfactory.ui.celebration

import com.achievementfactory.domain.tier.Tier

/**
 * One-shot celebration events (CELEB-04) — travel on a zero-replay `SharedFlow` that drops an
 * emission outright when nothing is subscribed (see `PeriodViewModel.celebrations`), never
 * persisted, never folded into a `PeriodUiState` or any other `StateFlow`. Deriving "should this
 * celebrate" from state that survives rotation or process restoration is the exact trap
 * PITFALLS 8 describes; a `CelebrationEvent` only ever exists for the collectors attached at the
 * instant it was emitted.
 *
 * [tier] drives intensity (CELEB-01) through [com.achievementfactory.ui.theme.MotionSpecs]'s tier
 * table; `null` is an achievement with no ladder and plays the Phase 1 treatment unchanged.
 */
sealed interface CelebrationEvent {

    val achievementId: Long
    val tier: Tier?

    /** True when this same tap made the period count toward the streak — the flame reacts. */
    val ticksStreak: Boolean

    /** The streak after this tap; only meaningful when [ticksStreak]. */
    val newStreak: Int

    /** The achievement's first-ever flame (streak 0 → 1): plays the bouncier unlock (D-08, D-22). */
    val isFirstFlame: Boolean

    /**
     * Emitted exactly once per completing tap — the exact edge
     * [com.achievementfactory.domain.model.IncrementOutcome.justCompleted] reports, never
     * inferred from a card's current `isDone` snapshot. [newStreak], [isFirstFlame] and [tier]
     * are read from that same call's own outcome, so they can never disagree with a
     * separately-collected UI state that may still be catching up to the same write. For a
     * laddered achievement completion means Gold, so [tier] is [Tier.GOLD]; [ticksStreak] is then
     * false unless Gold is the only tier, because the lowest medal already kept the streak.
     */
    data class Completed(
        override val achievementId: Long,
        override val newStreak: Int,
        override val isFirstFlame: Boolean,
        override val tier: Tier? = null,
        override val ticksStreak: Boolean = true,
    ) : CelebrationEvent

    /**
     * Emitted exactly once on the tap that reaches a new medal *below Gold* within the same period
     * (CELEB-02) — the edge `IncrementOutcome.tierUpgraded` reports. The card is not done yet, so
     * its ring stays open; the medal pops and the burst fires. On the lowest medal the streak is
     * secured on this same tap, so [ticksStreak] is true and the flame reacts too. Never emitted on
     * the completing (Gold) tap — that is [Completed]'s edge — so the two never double up.
     */
    data class TierUpgraded(
        override val achievementId: Long,
        override val tier: Tier,
        override val ticksStreak: Boolean = false,
        override val newStreak: Int = 0,
        override val isFirstFlame: Boolean = false,
    ) : CelebrationEvent
}
