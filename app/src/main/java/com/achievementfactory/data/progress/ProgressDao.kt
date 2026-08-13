package com.achievementfactory.data.progress

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Counter mutation methods are deliberately **not** written here — plan 01-04 adds them as
 * atomic `UPDATE ... SET count = count + 1` statements per PITFALLS 9 (undo races a
 * read-modify-write increment). No read-modify-write helper is introduced in this DAO that
 * would tempt a later plan away from that.
 */
@Dao
interface ProgressDao {

    @Query("SELECT * FROM progress WHERE periodKey = :periodKey")
    fun observeForPeriod(periodKey: String): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress WHERE achievementId = :achievementId AND periodKey = :periodKey")
    suspend fun getFor(achievementId: Long, periodKey: String): ProgressEntity?

    @Query("SELECT * FROM progress WHERE achievementId = :id ORDER BY periodKey DESC")
    fun observeForAchievement(id: Long): Flow<List<ProgressEntity>>

    @Query(
        """INSERT OR IGNORE INTO progress (achievementId, periodKey, count, done, tierReached, updatedAtEpochDay)
           VALUES (:achievementId, :periodKey, 0, 0, NULL, :nowEpochDay)"""
    )
    suspend fun insertIfAbsent(achievementId: Long, periodKey: String, nowEpochDay: Long)

    @Query(
        """SELECT periodKey FROM progress
           WHERE achievementId = :achievementId AND done = 1
           ORDER BY periodKey DESC"""
    )
    suspend fun doneKeysFor(achievementId: Long): List<String>
}
