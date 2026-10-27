package com.achievementfactory.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.achievementfactory.data.todo.TodoRepository
import com.achievementfactory.period.AppClock
import com.achievementfactory.period.CurrentPeriodProvider
import com.achievementfactory.period.PeriodEngine
import com.achievementfactory.period.PeriodType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

/** TODO-01 – TODO-06 against an in-memory Room database under Robolectric. */
@RunWith(RobolectricTestRunner::class)
class TodoRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var todos: TodoRepository
    private lateinit var provider: CurrentPeriodProvider

    // Wednesday 2026-08-12.
    private val currentDate = AtomicReference(LocalDate.of(2026, 8, 12))
    private val clock = object : AppClock {
        override fun today(): LocalDate = currentDate.get()
        override fun zone(): ZoneId = ZoneId.of("UTC")
        override fun epochDay(): Long = currentDate.get().toEpochDay()
    }

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        provider = CurrentPeriodProvider(PeriodEngine(clock))
        todos = TodoRepository(database, database.todoDao(), clock, provider)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun moveTo(date: LocalDate) {
        currentDate.set(date)
        provider.refresh()
    }

    @Test
    fun create_putsTheTodoInItsOwnTabOnly() = runBlocking {
        todos.create("Call mum", PeriodType.WEEKLY)

        assertEquals(listOf("Call mum"), todos.observeTodos(PeriodType.WEEKLY).first().map { it.title })
        assertTrue(todos.observeTodos(PeriodType.DAILY).first().isEmpty())
        assertTrue(todos.observeExpired().first().isEmpty())
    }

    @Test
    fun tickAndUntick() = runBlocking {
        val id = todos.create("Call mum", PeriodType.DAILY)

        assertTrue(todos.setDone(id, true))
        assertTrue(todos.observeTodos(PeriodType.DAILY).first().single().done)

        assertTrue(todos.setDone(id, false))
        assertFalse(todos.observeTodos(PeriodType.DAILY).first().single().done)
    }

    @Test
    fun anUnfinishedTodo_expiresIntoTheArchive_whenItsPeriodEnds() = runBlocking {
        todos.create("Call mum", PeriodType.DAILY)

        todos.observeTodos(PeriodType.DAILY).test {
            assertEquals(1, awaitItem().size)
            moveTo(LocalDate.of(2026, 8, 13))
            assertTrue("gone from the new day's tab", awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        val expired = todos.observeExpired().first().single()
        assertEquals("Call mum", expired.title)
        assertEquals("2026-08-12", expired.periodKey)
    }

    @Test
    fun aWeeklyTodo_staysAllWeek_andExpiresOnMonday() = runBlocking {
        todos.create("Clean the car", PeriodType.WEEKLY)

        moveTo(LocalDate.of(2026, 8, 16)) // Sunday
        assertEquals(1, todos.observeTodos(PeriodType.WEEKLY).first().size)
        assertTrue(todos.observeExpired().first().isEmpty())

        moveTo(LocalDate.of(2026, 8, 17)) // Monday
        assertTrue(todos.observeTodos(PeriodType.WEEKLY).first().isEmpty())
        assertEquals(1, todos.observeExpired().first().size)
    }

    @Test
    fun aFinishedTodo_neverShowsUpInTheArchive() = runBlocking {
        val id = todos.create("Call mum", PeriodType.DAILY)
        todos.setDone(id, true)

        moveTo(LocalDate.of(2026, 8, 20))

        assertTrue(todos.observeExpired().first().isEmpty())
        assertTrue(todos.observeTodos(PeriodType.DAILY).first().isEmpty())
    }

    @Test
    fun anExpiredTodo_cannotBeTickedAnymore() = runBlocking {
        val id = todos.create("Call mum", PeriodType.DAILY)
        moveTo(LocalDate.of(2026, 8, 13))

        assertFalse(todos.setDone(id, true))
        assertEquals(1, todos.observeExpired().first().size)
    }

    @Test
    fun restore_assignsItToTheChosenCurrentPeriod_unfinished() = runBlocking {
        val id = todos.create("Call mum", PeriodType.DAILY)
        moveTo(LocalDate.of(2026, 8, 14))

        assertTrue(todos.restore(id, PeriodType.MONTHLY))

        assertTrue(todos.observeExpired().first().isEmpty())
        val restored = todos.observeTodos(PeriodType.MONTHLY).first().single()
        assertEquals("Call mum", restored.title)
        assertFalse(restored.done)
    }

    @Test
    fun delete_removesItForGood() = runBlocking {
        val id = todos.create("Call mum", PeriodType.DAILY)

        assertTrue(todos.delete(id))

        assertTrue(todos.observeTodos(PeriodType.DAILY).first().isEmpty())
        assertFalse(todos.delete(id))
    }
}
