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
     */
    @Query(
        """SELECT id FROM achievements
           WHERE streakCachedForPeriodKey IS NULL OR streakCachedForPeriodKey != :currentKey"""
    )
    suspend fun staleAchievementIds(currentKey: String): List<Long>
}
