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

    /**
     * Emitted exactly once per completing tap — the exact edge
     * [com.achievementfactory.domain.model.IncrementOutcome.justCompleted] reports, never
     * inferred from a card's current `isDone` snapshot. [newStreak], [isFirstFlame] and [tier]
     * are read from that same call's own outcome, so they can never disagree with a
     * separately-collected UI state that may still be catching up to the same write. For a laddered
     * achievement [tier] is its lowest configured tier — the one whose threshold means "done".
     */
    data class Completed(
        override val achievementId: Long,
        val newStreak: Int,
        val isFirstFlame: Boolean,
        override val tier: Tier? = null,
    ) : CelebrationEvent

    /**
     * Emitted exactly once on the tap that crosses from one tier into a higher one within the
     * same period (CELEB-02) — the edge `IncrementOutcome.tierUpgraded` reports. Never emitted on
     * the completing tap itself (that is [Completed]'s edge), so the two never double up.
     */
    data class TierUpgraded(
        override val achievementId: Long,
        override val tier: Tier,
    ) : CelebrationEvent
}
