package com.achievementfactory.domain.model

import com.achievementfactory.period.PeriodKey

/**
 * Identifies exactly which increment an undo call reverses (PITFALLS 9). [expectedCount] is the
 * resulting count the repository's increment call observed immediately after producing it —
 * undo is a compare-and-set against this value at the database layer, not a blind decrement, so
 * a stale token (one taken before a further increment landed) or one replayed after midnight is
 * safely rejected rather than corrupting a different mutation.
 */
data class UndoToken(
    val achievementId: Long,
    val periodKey: PeriodKey,
    val expectedCount: Int,
)
