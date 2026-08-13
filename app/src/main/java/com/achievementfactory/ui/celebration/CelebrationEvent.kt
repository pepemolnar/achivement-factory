package com.achievementfactory.ui.celebration

/**
 * One-shot completion events (CELEB-04) — travel on a zero-replay `SharedFlow` that drops an
 * emission outright when nothing is subscribed (see `DailyViewModel.celebrations`), never
 * persisted, never folded into `DailyUiState` or any other `StateFlow`. Deriving "should this
 * celebrate" from state that survives rotation or process restoration is the exact trap
 * PITFALLS 8 describes; a `CelebrationEvent` only ever exists for the collectors attached at the
 * instant it was emitted, and an event emitted while the screen is stopped is gone rather than
 * queued for the collector that attaches after the screen restarts.
 *
 * Sealed rather than a data class because Phase 2 adds tier-variant cases alongside [Completed]
 * (CELEB-01, CELEB-02) without touching this file's existing case.
 */
sealed interface CelebrationEvent {

    /**
     * Emitted exactly once per completing tap — the exact edge
     * [com.achievementfactory.domain.model.IncrementOutcome.justCompleted] reports, never
     * inferred from a card's current `isDone` snapshot. [newStreak] and [isFirstFlame] are read
     * from that same call's own [com.achievementfactory.domain.model.IncrementOutcome], so they
     * can never disagree with a separately-collected `DailyUiState` that may still be catching
     * up to the same write.
     */
    data class Completed(
        val achievementId: Long,
        val newStreak: Int,
        val isFirstFlame: Boolean,
    ) : CelebrationEvent
}
