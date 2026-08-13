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
            achievementDao = database.achievementDao(),
            progressDao = database.progressDao(),
            periodEngine = periodEngine,
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

        repository.observeDaily().test {
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
            assertEquals(0, afterRollover?.todayCount)
            assertFalse(afterRollover?.isDone == true)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
