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
 * [runAll] runs once at app open (T-01-21's bounded walk, off the main thread on
 * `applicationScope`) and again on every [com.achievementfactory.period.PeriodWatcher] signal,
 * selecting only the achievements [AchievementDao.staleAchievementIds] reports as behind
 * [currentKey] — not a rewrite of every row on every pass. [runFor]'s own
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

    suspend fun runFor(achievementId: Long, currentKey: PeriodKey, force: Boolean = false) {
        val achievement = achievementDao.getById(achievementId) ?: return
        if (!force && achievement.streakCachedForPeriodKey == currentKey.value) return

        val doneKeys = progressDao.doneKeysFor(achievementId).toSet()
        val snapshot = streakCalculator.calculate(
            doneKeys = doneKeys,
            currentKey = currentKey,
            periodType = PeriodType.DAILY,
            storedBest = achievement.bestStreak,
        )
        achievementDao.updateStreakCache(
            id = achievementId,
            current = snapshot.current,
            best = snapshot.best,
            cachedForKey = currentKey.value,
        )
    }

    suspend fun runAll(currentKey: PeriodKey) {
        achievementDao.staleAchievementIds(currentKey.value).forEach { runFor(it, currentKey) }
    }
}
