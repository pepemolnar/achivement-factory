package com.achievementfactory.domain.streak

import com.achievementfactory.period.PeriodEngine
import com.achievementfactory.period.PeriodKey
import com.achievementfactory.period.PeriodType

/**
 * Derives the current and best streak from an achievement's completion log with a bounded
 * backward walk (ARCHITECTURE.md "Streak Computation") — the log is always the source of truth,
 * never an incrementally-maintained counter (PITFALLS Anti-Pattern 2). This is what makes the
 * result correct after any absence, however long: there is no "catch up from where we left off"
 * bookkeeping to get wrong, only a fresh walk over whatever the log actually says (PERIOD-05).
 *
 * Pure Kotlin — no Android import, no Room import, no clock read — so every case here is
 * exercised by fast JVM tests against a fabricated log, never a real device or a real day.
 */
class StreakCalculator(private val periodEngine: PeriodEngine) {

    /**
     * Walks backward from [currentKey]: if [currentKey] itself is in [doneKeys] the walk starts
     * there (an achievement completed in the current, still-open period counts immediately —
     * this is what makes a tap move the number in the same interaction as the tap); otherwise
     * the walk starts at the period immediately before [currentKey], so an open-but-incomplete
     * period neither counts toward the streak nor breaks it. From that starting point, each step
     * moves to `periodEngine.previous(periodType, ...)`; the walk counts and continues while the
     * stepped-to key is present in [doneKeys], and stops — with no grace — at the first key that
     * isn't. [StreakSnapshot.best] is `maxOf(storedBest, current)`, so it never decreases even
     * when [current] resets to 0.
     */
    fun calculate(
        doneKeys: Set<String>,
        currentKey: PeriodKey,
        periodType: PeriodType,
        storedBest: Int,
    ): StreakSnapshot {
        var cursor = if (currentKey.value in doneKeys) currentKey else periodEngine.previous(periodType, currentKey)
        var current = 0
        while (cursor.value in doneKeys) {
            current++
            cursor = periodEngine.previous(periodType, cursor)
        }
        return StreakSnapshot(current = current, best = maxOf(storedBest, current))
    }
}
