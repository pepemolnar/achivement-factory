package com.achievementfactory.data.progress

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Counter mutation is atomic SQL only (PITFALLS 9): [incrementAtomic] and [undoIfUnchanged] are
 * each a single `UPDATE ... SET count = ...` statement, never a Kotlin-side read followed by a
 * write. [undoIfUnchanged]'s `count = :expectedCount` clause is a compare-and-set that reverses
 * one *specific* increment rather than blindly decrementing — if a different increment landed
 * in between, zero rows match and the call is a no-op. No read-modify-write helper is
 * introduced in this DAO, and none should be added.
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

    /** Single-statement atomic increment (PITFALLS 9) — never preceded by a Kotlin-side read. */
    @Query(
        """UPDATE progress
           SET count = count + 1, done = ((count + 1) >= :target), updatedAtEpochDay = :epochDay
           WHERE achievementId = :achievementId AND periodKey = :periodKey"""
    )
    suspend fun incrementAtomic(achievementId: Long, periodKey: String, target: Int, epochDay: Long): Int

    /**
     * Compare-and-set undo: only applies when [expectedCount] still matches the live row, and
     * the `count > 0` guard means it can never drive the count below 0. A stale token (one taken
     * before a further increment landed) matches zero rows and is silently a no-op.
     */
    @Query(
        """UPDATE progress
           SET count = count - 1, done = ((count - 1) >= :target), updatedAtEpochDay = :epochDay
           WHERE achievementId = :achievementId AND periodKey = :periodKey
             AND count = :expectedCount AND count > 0"""
    )
    suspend fun undoIfUnchanged(
        achievementId: Long,
        periodKey: String,
        expectedCount: Int,
        target: Int,
        epochDay: Long,
    ): Int

    @Query("SELECT count FROM progress WHERE achievementId = :achievementId AND periodKey = :periodKey")
    suspend fun countFor(achievementId: Long, periodKey: String): Int?

    /**
     * Row creation, the atomic increment, and the read-back of the resulting count as one unit
     * (T-01-15) — this is what lets the repository's increment call report a count that matches
     * exactly what *this* call produced, even under concurrent taps, since SQLite serializes
     * write transactions and this whole method runs inside a single one.
     */
    @Transaction
    suspend fun incrementAndRead(achievementId: Long, periodKey: String, target: Int, epochDay: Long): Int {
        insertIfAbsent(achievementId, periodKey, epochDay)
        incrementAtomic(achievementId, periodKey, target, epochDay)
        return countFor(achievementId, periodKey) ?: 0
    }
}
