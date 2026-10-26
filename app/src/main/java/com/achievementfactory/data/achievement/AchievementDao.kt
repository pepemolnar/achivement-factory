package com.achievementfactory.data.achievement

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {

    /** A tab's achievements in their user-chosen order (ACH-09); ties fall back to creation order. */
    @Query(
        """SELECT * FROM achievements WHERE archived = 0 AND periodType = :periodType
           ORDER BY sortOrder ASC, createdAtEpochDay ASC, id ASC"""
    )
    fun observeActive(periodType: String): Flow<List<AchievementEntity>>

    /** Every archived achievement, for the Archived screen — grouped by period, then by name. */
    @Query("SELECT * FROM achievements WHERE archived = 1 ORDER BY periodType ASC, name COLLATE NOCASE ASC")
    fun observeArchived(): Flow<List<AchievementEntity>>

    /** The highest position currently used in a tab, or null when the tab is empty. */
    @Query("SELECT MAX(sortOrder) FROM achievements WHERE archived = 0 AND periodType = :periodType")
    suspend fun maxSortOrder(periodType: String): Int?

    @Query("UPDATE achievements SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    /** Cosmetic fields only (ACH-03) — never touches thresholds, period or the streak cache. */
    @Query("UPDATE achievements SET name = :name, icon = :icon, accentSwatch = :accentSwatch WHERE id = :id")
    suspend fun updateAppearance(id: Long, name: String, icon: String?, accentSwatch: String)

    /**
     * Thresholds only (ACH-04). The repository has already checked that nothing got lower — this
     * query enforces nothing on its own, so it must only ever be called from
     * [AchievementRepository.update].
     */
    @Query(
        """UPDATE achievements
           SET counterTarget = :counterTarget, bronzeThreshold = :bronze,
               silverThreshold = :silver, goldThreshold = :gold
           WHERE id = :id"""
    )
    suspend fun updateThresholds(id: Long, counterTarget: Int, bronze: Int?, silver: Int?, gold: Int?)

    /**
     * Archives or restores (ACH-06, ACH-07), also setting the position to use while active. The
     * streak cache is invalidated (`streakCachedForPeriodKey = NULL`) both ways, so the next
     * reconciliation recomputes it from the log rather than trusting a value frozen at archive time.
     */
    @Query(
        """UPDATE achievements
           SET archived = :archived, sortOrder = :sortOrder, streakCachedForPeriodKey = NULL
           WHERE id = :id"""
    )
    suspend fun setArchived(id: Long, archived: Boolean, sortOrder: Int)

    /**
     * Permanent delete (ACH-08) — the `archived = 1` clause makes "only archived achievements can
     * be deleted" a database-level guarantee, not just a UI one. Returns the number of rows
     * deleted (0 or 1). Progress rows go with it via the `CASCADE` foreign key.
     */
    @Query("DELETE FROM achievements WHERE id = :id AND archived = 1")
    suspend fun deleteIfArchived(id: Long): Int

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
