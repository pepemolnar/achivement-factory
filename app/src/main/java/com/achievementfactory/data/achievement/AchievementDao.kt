package com.achievementfactory.data.achievement

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {

    @Query("SELECT * FROM achievements WHERE archived = 0 AND periodType = :periodType ORDER BY createdAtEpochDay ASC")
    fun observeActive(periodType: String): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements WHERE id = :id")
    fun observeById(id: Long): Flow<AchievementEntity?>

    @Query("SELECT * FROM achievements WHERE id = :id")
    suspend fun getById(id: Long): AchievementEntity?

    @Insert
    suspend fun insert(entity: AchievementEntity): Long

    @Query(
        """UPDATE achievements
           SET currentStreak = :current, bestStreak = :best, streakCachedForPeriodKey = :cachedForKey
           WHERE id = :id"""
    )
    suspend fun updateStreakCache(id: Long, current: Int, best: Int, cachedForKey: String)

    /**
     * Ids of achievements whose cached streak was not last computed for [currentKey] — the set
     * [ReconcileStreaksUseCase.runAll] actually needs to touch, rather than rewriting every row
     * on every app open.
     *
     * Filtered by [periodType] because [currentKey] only means anything relative to one: a
     * Monday-anchored weekly key and a calendar-day daily key are not comparable, so selecting
     * rows of a different recurrence unit would hand the calculator a key from the wrong
     * dimension. Filtered by `archived = 0` because an archived achievement is not accruing a
     * streak and rewriting its cache on every pass is pure churn.
     */
    @Query(
        """SELECT id FROM achievements
           WHERE periodType = :periodType
             AND archived = 0
             AND (streakCachedForPeriodKey IS NULL OR streakCachedForPeriodKey != :currentKey)"""
    )
    suspend fun staleAchievementIds(currentKey: String, periodType: String): List<Long>
}
