package com.achievementfactory.data.achievement

import com.achievementfactory.data.progress.ProgressDao
import com.achievementfactory.domain.model.DailyAchievement
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
}
