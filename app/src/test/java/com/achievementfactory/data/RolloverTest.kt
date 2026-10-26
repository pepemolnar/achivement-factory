package com.achievementfactory.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.data.achievement.AchievementEntity
import com.achievementfactory.data.achievement.AchievementRepository
import com.achievementfactory.domain.maintenance.ReconcileStreaksUseCase
import com.achievementfactory.domain.streak.StreakCalculator
import com.achievementfactory.period.AppClock
import com.achievementfactory.period.CurrentPeriodProvider
import com.achievementfactory.period.PeriodEngine
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
import com.achievementfactory.period.PeriodType

/**
 * Proves PERIOD-04 end to end against a real (in-memory) Room database: an already-collected
 * `observeDaily()` flow re-emits when [CurrentPeriodProvider.refresh] moves the day forward,
 * with no re-subscription and no navigation from the caller. Also proves the previous day's
 * progress row is left completely untouched, matching D-28's immutability guarantee.
 */
@RunWith(RobolectricTestRunner::class)
class RolloverTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: AchievementRepository
    private lateinit var currentPeriodProvider: CurrentPeriodProvider

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
        currentPeriodProvider = CurrentPeriodProvider(periodEngine)
        repository = AchievementRepository(
            database = database,
            achievementDao = database.achievementDao(),
            progressDao = database.progressDao(),
            clock = clock,
            reconcileStreaks = ReconcileStreaksUseCase(
                achievementDao = database.achievementDao(),
                progressDao = database.progressDao(),
                streakCalculator = StreakCalculator(periodEngine),
            ),
            currentPeriodProvider = currentPeriodProvider,
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private suspend fun createAchievement(target: Int = 1): Long {
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

    /** Marks a progress row done directly via SQL — isolates this test from streak-cache writes. */
    private suspend fun markDone(achievementId: Long, periodKey: String, count: Int) {
        database.progressDao().insertIfAbsent(achievementId, periodKey, currentDate.get().toEpochDay())
        database.openHelper.writableDatabase.execSQL(
            "UPDATE progress SET count = $count, done = 1 " +
                "WHERE achievementId = $achievementId AND periodKey = '$periodKey'"
        )
    }

    @Test
    fun observeDaily_alreadyCollected_reEmitsNotDone_afterClockAdvancesAndRefreshIsCalled() = runBlocking {
        val id = createAchievement(target = 1)
        val today = currentDate.get().toString()
        markDone(id, today, count = 1)

        repository.observePeriod(PeriodType.DAILY).test {
            val beforeRollover = awaitItem()
            assertEquals(1, beforeRollover.single().count)
            assertTrue(beforeRollover.single().isDone)

            currentDate.set(currentDate.get().plusDays(1))
            currentPeriodProvider.refresh()

            val afterRollover = awaitItem()
            assertEquals(0, afterRollover.single().count)
            assertFalse(afterRollover.single().isDone)

            cancelAndIgnoreRemainingEvents()
        }

        val yesterdayRow = database.progressDao().getFor(id, today)
        assertEquals(1, yesterdayRow?.count)
        assertEquals(true, yesterdayRow?.done)
    }

    /**
     * The divergence window WR-02 is about: real midnight has passed but no `ACTION_DATE_CHANGED`
     * broadcast or process resume has called `refresh()` yet, so the read flows are still joined
     * against yesterday's key. A write that resolved the clock independently of
     * [CurrentPeriodProvider] would key its progress row to the *new* day while the collected
     * `observeDaily` was still showing the *old* one — the tap would visibly do nothing, and the
     * undo token would name a row the user cannot see. Note that no `refresh()` is called here:
     * the write itself is what has to move the provider.
     */
    @Test
    fun increment_afterMidnightWithNoRefreshSignal_isVisibleToTheAlreadyCollectedReadFlow() = runBlocking {
        val id = createAchievement(target = 1)

        repository.observePeriod(PeriodType.DAILY).test {
            assertEquals(0, awaitItem().single().count)

            // Midnight passes. Nothing has told the provider yet.
            currentDate.set(currentDate.get().plusDays(1))

            val outcome = repository.increment(id)!!
            assertEquals(1, outcome.newCount)

            val afterIncrement = awaitItem()
            assertEquals("the tap must be visible on the day it was written to", 1, afterIncrement.single().count)
            assertTrue(afterIncrement.single().isDone)

            cancelAndIgnoreRemainingEvents()
        }

        // ...and it landed on the new day, not the old one — the D-28 immutability guarantee.
        assertEquals(1, database.progressDao().getFor(id, currentDate.get().toString())?.count)
        assertEquals(currentDate.get().toString(), outcomeKeyProbe(id))
    }

    /** The single period key this achievement has any progress row for. */
    private suspend fun outcomeKeyProbe(achievementId: Long): String? =
        database.progressDao().doneKeysFor(achievementId).singleOrNull()

    @Test
    fun observeDetail_alreadyCollected_reEmitsNotDone_afterClockAdvancesAndRefreshIsCalled() = runBlocking {
        val id = createAchievement(target = 1)
        val today = currentDate.get().toString()
        markDone(id, today, count = 1)

        repository.observeDetail(id).test {
            val beforeRollover = awaitItem()
            assertTrue(beforeRollover?.isDone == true)

            currentDate.set(currentDate.get().plusDays(1))
            currentPeriodProvider.refresh()

            val afterRollover = awaitItem()
            assertEquals(0, afterRollover?.periodCount)
            assertFalse(afterRollover?.isDone == true)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
