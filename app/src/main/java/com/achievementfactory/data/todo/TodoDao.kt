package com.achievementfactory.data.todo

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    /** A tab's todos for its current period — unfinished first, then by creation. */
    @Query(
        """SELECT * FROM todos WHERE periodType = :periodType AND periodKey = :periodKey
           ORDER BY done ASC, createdAtEpochDay ASC, id ASC"""
    )
    fun observeForPeriod(periodType: String, periodKey: String): Flow<List<TodoEntity>>

    /**
     * Expired todos (TODO-04): unfinished, and assigned to a period that is no longer the current
     * one for their type. Takes all three current keys because each type is compared against its
     * own. `!=` rather than `<` on purpose — a todo whose period is not current is not in any tab,
     * so it must be in the archive, whichever way the clock moved.
     */
    @Query(
        """SELECT * FROM todos
           WHERE done = 0 AND (
               (periodType = 'DAILY' AND periodKey != :dailyKey)
               OR (periodType = 'WEEKLY' AND periodKey != :weeklyKey)
               OR (periodType = 'MONTHLY' AND periodKey != :monthlyKey)
           )
           ORDER BY periodKey DESC, id DESC"""
    )
    fun observeExpired(dailyKey: String, weeklyKey: String, monthlyKey: String): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun getById(id: Long): TodoEntity?

    @Insert
    suspend fun insert(entity: TodoEntity): Long

    /**
     * Ticks or unticks (TODO-03) — only while the todo's period is still the current one, so a
     * closed period's result can never be rewritten (the same rule as achievements, TRACK-10).
     * Returns the number of rows changed.
     */
    @Query(
        """UPDATE todos SET done = :done, completedAtEpochDay = :completedAtEpochDay
           WHERE id = :id AND periodKey = :currentKey"""
    )
    suspend fun setDone(id: Long, done: Boolean, completedAtEpochDay: Long?, currentKey: String): Int

    /** Restores an expired todo into a current period (TODO-05), unfinished. */
    @Query(
        """UPDATE todos SET periodType = :periodType, periodKey = :periodKey, done = 0, completedAtEpochDay = NULL
           WHERE id = :id"""
    )
    suspend fun reassign(id: Long, periodType: String, periodKey: String): Int

    /** Permanent delete (TODO-06). */
    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun delete(id: Long): Int
}
