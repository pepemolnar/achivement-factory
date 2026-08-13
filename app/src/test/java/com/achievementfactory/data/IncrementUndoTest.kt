package com.achievementfactory.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.data.achievement.AchievementEntity
import com.achievementfactory.data.achievement.AchievementRepository
import com.achievementfactory.domain.maintenance.ReconcileStreaksUseCase
import com.achievementfactory.domain.model.UndoToken
import com.achievementfactory.domain.streak.StreakCalculator
import com.achievementfactory.period.AppClock
import com.achievementfactory.period.CurrentPeriodProvider
import com.achievementfactory.period.PeriodEngine
import com.achievementfactory.period.PeriodKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

/**
 * Covers PITFALLS 9 end to end: atomic increment, compare-and-set undo, and the D-28 guarantee
 * that a backward clock move cannot touch a day that isn't the one the clock currently reports
 * as today. Runs under Robolectric against an in-memory Room database with an injectable
 * [AppClock], the same device-free pattern as AppDatabaseTest/AchievementRepositoryTest.
 */
@RunWith(RobolectricTestRunner::class)
class IncrementUndoTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: AchievementRepository

    private val currentDate = AtomicReference(LocalDate.of(2026, 8, 13))
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

        val periodEngine = PeriodEngine(clock)
        repository = AchievementRepository(
            achievementDao = database.achievementDao(),
            progressDao = database.progressDao(),
            periodEngine = periodEngine,
            clock = clock,
            reconcileStreaks = ReconcileStreaksUseCase(
                achievementDao = database.achievementDao(),
                progressDao = database.progressDao(),
                streakCalculator = StreakCalculator(periodEngine),
            ),
            currentPeriodProvider = CurrentPeriodProvider(periodEngine),
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private suspend fun createAchievement(target: Int): Long {
        val entity = AchievementEntity(
            name = "Read",
            periodType = "DAILY",
            icon = null,
            accentSwatch = AccentSwatch.SKY.name,
            counterTarget = target,
            createdAtEpochDay = currentDate.get().toEpochDay(),
        )
        return database.achievementDao().insert(entity)
    }

    private suspend fun countToday(achievementId: Long): Int? =
        database.progressDao().countFor(achievementId, currentDate.get().toString())

    @Test
    fun increment_withNoExistingRow_createsRowAtCountOne() = runBlocking {
        val id = createAchievement(target = 3)

        val outcome = repository.increment(id)

        assertEquals(1, outcome.newCount)
        assertFalse(outcome.isDone)
        assertFalse(outcome.justCompleted)
    }

    @Test
    fun increment_targetOne_completesOnFirstTap() = runBlocking {
        val id = createAchievement(target = 1)

        val outcome = repository.increment(id)

        assertTrue(outcome.isDone)
        assertTrue(outcome.justCompleted)
    }

    @Test
    fun increment_targetThree_completesOnThirdTapOnly() = runBlocking {
        val id = createAchievement(target = 3)

        repository.increment(id)
        val second = repository.increment(id)
        assertEquals(2, second.newCount)
        assertFalse(second.isDone)
        assertFalse(second.justCompleted)

        val third = repository.increment(id)
        assertEquals(3, third.newCount)
        assertTrue(third.isDone)
        assertTrue(third.justCompleted)
    }

    @Test
    fun increment_pastTarget_reportsDoneButNotJustCompletedAgain() = runBlocking {
        val id = createAchievement(target = 3)
        repository.increment(id)
        repository.increment(id)
        repository.increment(id)

        val fourth = repository.increment(id)

        assertTrue(fourth.isDone)
        assertFalse(fourth.justCompleted)
    }

    @Test
    fun fiftyConcurrentIncrements_produceExactlyFifty() = runBlocking {
        val id = createAchievement(target = 1000)

        val jobs = (1..50).map {
            async(Dispatchers.IO) { repository.increment(id) }
        }
        jobs.awaitAll()

        assertEquals(50, countToday(id))
    }

    @Test
    fun undo_withTokenFromPrecedingIncrement_decrementsByOneAndRecomputesDone() = runBlocking {
        val id = createAchievement(target = 1)
        val outcome = repository.increment(id)

        val result = repository.undo(outcome.undoToken)

        assertTrue(result)
        assertEquals(0, countToday(id))
        assertEquals(false, database.progressDao().getFor(id, currentDate.get().toString())?.done)
    }

    @Test
    fun undo_withStaleToken_changesNothingAndReturnsFalse() = runBlocking {
        val id = createAchievement(target = 5)
        val first = repository.increment(id)
        repository.increment(id) // count now 2 — first.undoToken (expects 1) is now stale

        val result = repository.undo(first.undoToken)

        assertFalse(result)
        assertEquals(2, countToday(id))
    }

    @Test
    fun undo_appliedTwiceWithSameToken_appliesOnce() = runBlocking {
        val id = createAchievement(target = 5)
        val outcome = repository.increment(id)

        val firstUndo = repository.undo(outcome.undoToken)
        val secondUndo = repository.undo(outcome.undoToken)

        assertTrue(firstUndo)
        assertFalse(secondUndo)
        assertEquals(0, countToday(id))
    }

    @Test
    fun undo_neverDrivesCountBelowZero() = runBlocking {
        val id = createAchievement(target = 1)
        val outcome = repository.increment(id)
        repository.undo(outcome.undoToken) // count is now 0

        // A token that (incorrectly) still claims the count was 1 must be rejected — the
        // `count > 0` floor in ProgressDao.undoIfUnchanged is what makes this structural.
        val wouldGoNegative = UndoToken(
            achievementId = id,
            periodKey = PeriodKey(currentDate.get().toString()),
            expectedCount = 1,
        )
        val result = repository.undo(wouldGoNegative)

        assertFalse(result)
        assertEquals(0, countToday(id))
    }

    @Test
    fun clockMovedBackward_previousDaysRowIsNeverTouchedByIncrementOrUndo() = runBlocking {
        val id = createAchievement(target = 1)

        currentDate.set(LocalDate.of(2026, 8, 11))
        val day1Outcome = repository.increment(id)
        assertTrue(day1Outcome.isDone)

        currentDate.set(LocalDate.of(2026, 8, 12))
        repository.increment(id)

        currentDate.set(LocalDate.of(2026, 8, 13))
        repository.increment(id)

        // Move the clock backward onto 08-12, a day that already has a recorded, done row.
        // Per D-27's trust-the-clock model this day legitimately becomes "today" again and is
        // writable — but 08-11 (a *different* previous day) must stay completely untouched by
        // both this increment and the stale-relative-to-now undo attempt below.
        currentDate.set(LocalDate.of(2026, 8, 12))
        repository.increment(id)
        val undoResult = repository.undo(day1Outcome.undoToken)

        assertFalse(undoResult)
        val day1Row = database.progressDao().getFor(id, "2026-08-11")
        assertEquals(1, day1Row?.count)
        assertEquals(true, day1Row?.done)
    }
}
