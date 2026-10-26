package com.achievementfactory.domain.maintenance

import com.achievementfactory.data.achievement.AchievementDao
import com.achievementfactory.data.progress.ProgressDao
import com.achievementfactory.domain.streak.StreakCalculator
import com.achievementfactory.period.PeriodKey
import com.achievementfactory.period.PeriodType

/**
 * Idempotent reconciliation pass (ARCHITECTURE.md "Streak Computation", PITFALLS 4). The cached
 * streak columns on `achievements` are a read cache, never a source of truth — [runFor] rewrites
 * them from [ProgressDao.doneKeysFor]'s log via [StreakCalculator] whenever the cache is behind
 * [currentKey], so drift always self-repairs on the next pass.
 *
 * Neither [runFor] nor [runAll] resolves "what day is today" itself — both take [currentKey] as
 * a caller-supplied parameter, so [com.achievementfactory.period.CurrentPeriodProvider] stays
 * the single place that answers that question (PERIOD-04); call sites pass
 * `currentPeriodProvider.currentDailyKey.value` (or, on the write path, the same `todayKey`
 * they already resolved for their own SQL write).
 *
 * [runAll] runs at app open and on every [com.achievementfactory.period.PeriodWatcher] signal —
 * both are the same `onStart`/broadcast path (T-01-21's bounded walk, off the main thread on
 * `applicationScope`) — selecting only the active achievements of the matching recurrence unit
 * that [AchievementDao.staleAchievementIds] reports as behind
 * `currentKey` — not a rewrite of every row on every pass. [runFor]'s own
 * `streakCachedForPeriodKey == currentKey` guard then makes that pass a genuine no-op for any
 * achievement it did not need to touch, which is what keeps running it twice in a row
 * byte-identical to running it once.
 *
 * [com.achievementfactory.data.achievement.AchievementRepository.increment]/`.undo` call
 * `runFor(achievementId, todayKey, force = true)` after their own write. `force` exists because
 * the plain key-equality guard above is only a valid proxy for "nothing changed" across
 * app-open-to-app-open gaps, not within a single day: the very first `runAll()` at app open
 * already stamps every achievement's cache with today's key, so a same-day completion (or its
 * undo) would otherwise silently skip the recompute the guard was never meant to block — the
 * streak has to move in the same call as the tap that earned it, every time, not just the first
 * time that day.
 */
class ReconcileStreaksUseCase(
    private val achievementDao: AchievementDao,
    private val progressDao: ProgressDao,
    private val streakCalculator: StreakCalculator,
) {

    /**
     * Recomputes one achievement's cached streak. [currentKey] must be a key of [keyPeriodType]'s
     * recurrence unit, resolved from [com.achievementfactory.period.CurrentPeriodProvider.current]
     * for that same type — `PeriodWatcher` runs one [runAll] per [PeriodType], and the repository's
     * write paths pass the achievement's own type. The DAILY default is kept for the many call
     * sites (and tests) that deal only in daily achievements.
     *
     * An achievement whose own `periodType` does not match [keyPeriodType] is skipped without
     * being written. That guard is the point: the row's period type used to be discarded
     * entirely and [PeriodType.DAILY] passed unconditionally, so the moment Phase 2 turns on
     * WEEKLY/MONTHLY this would have walked Monday-anchored keys one *day* at a time and pinned
     * every weekly streak at 1 forever, silently and with a plausible-looking number. Leaving the
     * cache visibly stale is a much better failure than writing a confidently wrong streak.
     * Phase 2 resolves a `currentKey` per recurrence unit and calls this once per unit.
     */
    suspend fun runFor(
        achievementId: Long,
        currentKey: PeriodKey,
        force: Boolean = false,
        keyPeriodType: PeriodType = PeriodType.DAILY,
    ) {
        val achievement = achievementDao.getById(achievementId) ?: return
        if (!force && achievement.streakCachedForPeriodKey == currentKey.value) return

        // Tolerant of an unrecognized stored value (a hand-edited row, a renamed enum constant)
        // for the same reason: skip rather than throw out of a maintenance pass.
        val periodType = PeriodType.entries.firstOrNull { it.name == achievement.periodType }
        if (periodType != keyPeriodType) return

        val doneKeys = progressDao.doneKeysFor(achievementId).toSet()
        val snapshot = streakCalculator.calculate(
            doneKeys = doneKeys,
            currentKey = currentKey,
            periodType = periodType,
            storedBest = achievement.bestStreak,
        )
        achievementDao.updateStreakCache(
            id = achievementId,
            current = snapshot.current,
            best = snapshot.best,
            cachedForKey = currentKey.value,
        )
    }

    suspend fun runAll(currentKey: PeriodKey, keyPeriodType: PeriodType = PeriodType.DAILY) {
        achievementDao.staleAchievementIds(currentKey.value, keyPeriodType.name)
            .forEach { runFor(it, currentKey, keyPeriodType = keyPeriodType) }
    }
}
