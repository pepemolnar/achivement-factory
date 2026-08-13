package com.achievementfactory.domain.maintenance

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.achievementfactory.data.AppDatabase
import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.data.achievement.AchievementEntity
import com.achievementfactory.data.achievement.AchievementRepository
import com.achievementfactory.domain.streak.StreakCalculator
import com.achievementfactory.period.AppClock
import com.achievementfactory.period.CurrentPeriodProvider
import com.achievementfactory.period.PeriodEngine
import com.achievementfactory.period.PeriodKey
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs on the JVM via Robolectric against an in-memory Room database with an injectable
 * [AppClock] — the same device-free pattern as IncrementUndoTest/AchievementRepositoryTest.
 * Covers the reconciliation pass's idempotence (PITFALLS 4), that best streak only ever holds
 * steady or rises (never falls back down), and that completing/undoing today's achievement moves
 * the cached streak in the same call as the write that caused it.
 */
@RunWith(RobolectricTestRunner::class)
class ReconcileStreaksUseCaseTest {

    private lateinit var database: AppDatabase
    private lateinit var reconcileStreaks: ReconcileStreaksUseCase
    private lateinit var repository: AchievementRepository
    private lateinit var periodEngine: PeriodEngine

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

        periodEngine = PeriodEngine(clock)
        reconcileStreaks = ReconcileStreaksUseCase(
            achievementDao = database.achievementDao(),
            progressDao = database.progressDao(),
            streakCalculator = StreakCalculator(periodEngine),
        )
        repository = AchievementRepository(
            achievementDao = database.achievementDao(),
            progressDao = database.progressDao(),
            periodEngine = periodEngine,
            clock = clock,
            reconcileStreaks = reconcileStreaks,
            currentPeriodProvider = CurrentPeriodProvider(periodEngine),
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

    private suspend fun markDone(achievementId: Long, date: LocalDate) {
        database.progressDao().insertIfAbsent(achievementId, date.toString(), date.toEpochDay())
        database.openHelper.writableDatabase.execSQL(
            "UPDATE progress SET count = 1, done = 1 WHERE achievementId = $achievementId AND periodKey = '$date'"
        )
    }

    private val todayKey: PeriodKey get() = PeriodKey(currentDate.get().toString())

    @Test
    fun runFor_whenCacheAlreadyMatchesToday_performsNoWrite() = runBlocking {
        val id = createAchievement()
        markDone(id, currentDate.get())
        reconcileStreaks.runFor(id, todayKey) // stale cache (null) — this pass writes it

        val afterFirstRun = database.achievementDao().getById(id)
        reconcileStreaks.runFor(id, todayKey) // cache now matches today — must be a genuine no-op
        val afterSecondRun = database.achievementDao().getById(id)

        assertEquals(afterFirstRun, afterSecondRun)
    }

    @Test
    fun runFor_withStaleCache_rewritesCurrentAndLeavesBestAtHistoricalHigh() = runBlocking {
        val id = createAchievement()
        markDone(id, currentDate.get().minusDays(1))
        markDone(id, currentDate.get().minusDays(2))
        database.achievementDao().updateStreakCache(id, current = 0, best = 50, cachedForKey = "stale-key")

        reconcileStreaks.runFor(id, todayKey)

        val entity = database.achievementDao().getById(id)
        assertEquals(2, entity?.currentStreak)
        assertEquals(50, entity?.bestStreak)
        assertEquals(currentDate.get().toString(), entity?.streakCachedForPeriodKey)
    }

    @Test
    fun runAll_twiceInARow_producesByteIdenticalRows() = runBlocking {
        val id1 = createAchievement()
        val id2 = createAchievement()
        markDone(id1, currentDate.get())
        markDone(id2, currentDate.get().minusDays(3))

        reconcileStreaks.runAll(todayKey)
        val afterFirst = listOf(database.achievementDao().getById(id1), database.achievementDao().getById(id2))

        reconcileStreaks.runAll(todayKey)
        val afterSecond = listOf(database.achievementDao().getById(id1), database.achievementDao().getById(id2))

        assertEquals(afterFirst, afterSecond)
    }

    @Test
    fun completingToday_raisesCurrentStreakImmediately_undoLowersItAgain_bestStaysAtHighWaterMark() = runBlocking {
        val id = createAchievement(target = 1)
        markDone(id, currentDate.get().minusDays(1))
        markDone(id, currentDate.get().minusDays(2))
        // Simulate an app-open runAll() that already reconciled this achievement for *today*
        // before today was done — this is exactly the same-day state that motivates `force`.
        database.achievementDao().updateStreakCache(
            id,
            current = 2,
            best = 10,
            cachedForKey = currentDate.get().toString(),
        )

        val outcome = repository.increment(id)

        val afterIncrement = database.achievementDao().getById(id)
        assertTrue(outcome.isDone)
        assertEquals(3, afterIncrement?.currentStreak)
        assertEquals(10, afterIncrement?.bestStreak)

        repository.undo(outcome.undoToken)

        val afterUndo = database.achievementDao().getById(id)
        assertEquals(2, afterUndo?.currentStreak)
        assertEquals(10, afterUndo?.bestStreak)
    }
}
