package com.achievementfactory.data.todo

import androidx.room.withTransaction
import com.achievementfactory.data.AppDatabase
import com.achievementfactory.domain.model.ExpiredTodo
import com.achievementfactory.domain.model.TodoItem
import com.achievementfactory.period.AppClock
import com.achievementfactory.period.CurrentPeriodProvider
import com.achievementfactory.period.PeriodType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * The read/write entry point for todos (TODO-01 – TODO-06), mirroring `AchievementRepository`:
 * [TodoEntity] never crosses into `ui/`, every "which period is it" goes through
 * [CurrentPeriodProvider], and writes refresh it first so a write and the visible list always
 * agree on the period.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodoRepository(
    private val database: AppDatabase,
    private val todoDao: TodoDao,
    private val clock: AppClock,
    private val currentPeriodProvider: CurrentPeriodProvider,
) {

    /**
     * A tab's todos for its current period (NAV-02). Re-subscribes when the period rolls over, at
     * which point unfinished ones simply stop matching and appear in [observeExpired] instead —
     * that *is* the move to the archive (TODO-04); nothing is written when a period ends.
     */
    fun observeTodos(periodType: PeriodType): Flow<List<TodoItem>> =
        currentPeriodProvider.current(periodType).flatMapLatest { key ->
            todoDao.observeForPeriod(periodType.name, key.value).map { rows -> rows.mapNotNull { it.toItem() } }
        }

    /** Unfinished todos from periods that have ended, for the Archived screen (TODO-04, NAV-03). */
    fun observeExpired(): Flow<List<ExpiredTodo>> =
        combine(
            currentPeriodProvider.current(PeriodType.DAILY),
            currentPeriodProvider.current(PeriodType.WEEKLY),
            currentPeriodProvider.current(PeriodType.MONTHLY),
        ) { daily, weekly, monthly -> Triple(daily, weekly, monthly) }
            .flatMapLatest { (daily, weekly, monthly) ->
                todoDao.observeExpired(daily.value, weekly.value, monthly.value).map { rows ->
                    rows.mapNotNull { row ->
                        val type = typeOf(row.periodType) ?: return@mapNotNull null
                        ExpiredTodo(row.id, row.title, type, row.periodKey)
                    }
                }
            }

    /** Creates a todo in the current period of [periodType] (TODO-01). Returns its id. */
    suspend fun create(title: String, periodType: PeriodType): Long {
        currentPeriodProvider.refresh()
        return todoDao.insert(
            TodoEntity(
                title = title,
                periodType = periodType.name,
                periodKey = currentPeriodProvider.current(periodType).value.value,
                createdAtEpochDay = clock.epochDay(),
            ),
        )
    }

    /**
     * Ticks or unticks a todo (TODO-03). Only a todo in its current period can change — returns
     * false, touching nothing, for one whose period has ended or that no longer exists.
     */
    suspend fun setDone(id: Long, done: Boolean): Boolean {
        currentPeriodProvider.refresh()
        return database.withTransaction<Boolean> {
            val todo = todoDao.getById(id) ?: return@withTransaction false
            val type = typeOf(todo.periodType) ?: return@withTransaction false
            val currentKey = currentPeriodProvider.current(type).value.value
            todoDao.setDone(id, done, if (done) clock.epochDay() else null, currentKey) > 0
        }
    }

    /**
     * Brings an expired todo back by assigning it to the current period of [periodType] — today,
     * this week or this month (TODO-05). It comes back unfinished.
     */
    suspend fun restore(id: Long, periodType: PeriodType): Boolean {
        currentPeriodProvider.refresh()
        val key = currentPeriodProvider.current(periodType).value.value
        return todoDao.reassign(id, periodType.name, key) > 0
    }

    /** Deletes a todo for good (TODO-06); the UI always confirms first. */
    suspend fun delete(id: Long): Boolean = todoDao.delete(id) > 0

    private fun TodoEntity.toItem(): TodoItem? {
        val type = typeOf(periodType) ?: return null
        return TodoItem(id = id, title = title, periodType = type, done = done)
    }

    /** Tolerant parse — an unrecognized stored value is skipped rather than thrown out of a flow. */
    private fun typeOf(name: String): PeriodType? = PeriodType.entries.firstOrNull { it.name == name }
}
