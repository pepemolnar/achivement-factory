package com.achievementfactory.data.achievement

import com.achievementfactory.data.progress.ProgressDao
import com.achievementfactory.domain.model.DailyAchievement
import com.achievementfactory.domain.model.IncrementOutcome
import com.achievementfactory.domain.model.UndoToken
import com.achievementfactory.period.AppClock
import com.achievementfactory.period.PeriodEngine
import com.achievementfactory.period.PeriodType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The single read/write entry point between ViewModels and the DAOs (ARCHITECTURE.md
 * "Pattern 1"). [DailyAchievement] is the only type this repository exposes across the `ui/`
 * boundary — [AchievementEntity]/[com.achievementfactory.data.progress.ProgressEntity] never
 * cross it (T-01-09).
 */
class AchievementRepository(
    private val achievementDao: AchievementDao,
    private val progressDao: ProgressDao,
    private val periodEngine: PeriodEngine,
    private val clock: AppClock,
) {

    /**
     * Reactively joins today's active daily achievements with today's progress rows. Today's
     * period key is resolved once, in Kotlin, at the moment this flow is built — SQLite is
     * never asked to compute what day it is (PITFALLS 1). Achievements with no progress row for
     * today map to `count = 0, isDone = false`; `isDone` is always read from the progress row's
     * snapshotted `done` column, never recomputed from the achievement's *current*
     * `counterTarget`, so a closed period's result stays immutable even if the achievement is
     * edited later (TRACK-10, D-28).
     */
    fun observeDaily(): Flow<List<DailyAchievement>> {
        val todayKey = periodEngine.current(PeriodType.DAILY)
        return combine(
            achievementDao.observeActive(PeriodType.DAILY.name),
            progressDao.observeForPeriod(todayKey.value),
        ) { achievements, progressRows ->
            val progressByAchievementId = progressRows.associateBy { it.achievementId }
            achievements.map { achievement ->
                val progress = progressByAchievementId[achievement.id]
                DailyAchievement(
                    id = achievement.id,
                    name = achievement.name,
                    icon = achievement.icon,
                    accent = AccentSwatch.valueOf(achievement.accentSwatch),
                    target = achievement.counterTarget,
                    count = progress?.count ?: 0,
                    isDone = progress?.done ?: false,
                    currentStreak = achievement.currentStreak,
                    bestStreak = achievement.bestStreak,
                )
            }
        }
    }

    /** Creates a new daily achievement, zeroed streaks and no tiers configured. Returns its id. */
    suspend fun create(name: String, icon: String?, accent: AccentSwatch, target: Int): Long {
        val entity = AchievementEntity(
            name = name,
            periodType = PeriodType.DAILY.name,
            icon = icon,
            accentSwatch = accent.name,
            counterTarget = target,
            bronzeThreshold = null,
            silverThreshold = null,
            goldThreshold = null,
            createdAtEpochDay = clock.epochDay(),
            currentStreak = 0,
            bestStreak = 0,
            streakCachedForPeriodKey = null,
        )
        return achievementDao.insert(entity)
    }

    /**
     * Logs one tap of progress against **today only** — this method resolves
     * `periodEngine.current(PeriodType.DAILY)` itself and never accepts a period key from its
     * caller, which is what makes TRACK-05/TRACK-10 structural rather than a runtime check
     * someone could forget (T-01-14). Row creation, the atomic counter update, and the
     * read-back of the resulting count all happen inside
     * [ProgressDao.incrementAndRead]'s single transaction (T-01-15), so [IncrementOutcome.newCount]
     * always matches exactly what *this* call produced, even under concurrent taps.
     *
     * [IncrementOutcome.justCompleted] is derived from [IncrementOutcome.newCount] alone
     * (`previousCount = newCount - 1`) rather than from a separate "before" read, so there is no
     * second read that could race a concurrent increment and report the wrong transition.
     */
    suspend fun increment(achievementId: Long): IncrementOutcome {
        val target = achievementDao.getById(achievementId)?.counterTarget
            ?: error("Cannot increment unknown achievement id=$achievementId")
        val todayKey = periodEngine.current(PeriodType.DAILY)
        val newCount = progressDao.incrementAndRead(achievementId, todayKey.value, target, clock.epochDay())
        val isDone = newCount >= target
        val previousCount = newCount - 1
        val justCompleted = isDone && previousCount < target
        return IncrementOutcome(
            newCount = newCount,
            isDone = isDone,
            justCompleted = justCompleted,
            undoToken = UndoToken(achievementId, todayKey, newCount),
        )
    }

    /**
     * Reverses the specific increment identified by [token] via a compare-and-set at the
     * database layer (see [ProgressDao.undoIfUnchanged]). Returns `false` — without touching any
     * row — when [token]'s period key is not today's: a token that survived past midnight is
     * refused rather than applied to a new day (T-01-16). This, combined with [increment] never
     * naming any period other than today, is the whole mechanism behind D-28: a backward clock
     * move cannot reopen a day that isn't the one the trusted clock currently reports as today.
     */
    suspend fun undo(token: UndoToken): Boolean {
        val todayKey = periodEngine.current(PeriodType.DAILY)
        if (token.periodKey != todayKey) return false
        val target = achievementDao.getById(token.achievementId)?.counterTarget ?: return false
        val affected = progressDao.undoIfUnchanged(
            achievementId = token.achievementId,
            periodKey = todayKey.value,
            expectedCount = token.expectedCount,
            target = target,
            epochDay = clock.epochDay(),
        )
        return affected > 0
    }
}
