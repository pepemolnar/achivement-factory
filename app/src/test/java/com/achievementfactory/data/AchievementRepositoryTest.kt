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
import com.achievementfactory.period.PeriodEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

/**
 * Runs on the JVM via Robolectric against an in-memory Room database with a fixed [AppClock] —
 * the same device-free pattern as AppDatabaseTest.
 */
@RunWith(RobolectricTestRunner::class)
class AchievementRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: AchievementRepository

    private val today = LocalDate.of(2026, 8, 13)
    private val clock = object : AppClock {
        override fun today(): LocalDate = today
        override fun zone(): ZoneId = ZoneId.of("UTC")
        override fun epochDay(): Long = today.toEpochDay()
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
                periodEngine = periodEngine,
                streakCalculator = StreakCalculator(periodEngine),
            ),
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun sampleAchievement(name: String) = AchievementEntity(
        name = name,
        periodType = "DAILY",
        icon = null,
        accentSwatch = AccentSwatch.SKY.name,
        createdAtEpochDay = today.toEpochDay(),
    )

    private suspend fun insertProgress(achievementId: Long, periodKey: String, count: Int, done: Boolean) {
        database.progressDao().insertIfAbsent(achievementId, periodKey, today.toEpochDay())
        database.openHelper.writableDatabase.execSQL(
            "UPDATE progress SET count = $count, done = ${if (done) 1 else 0} " +
                "WHERE achievementId = $achievementId AND periodKey = '$periodKey'"
        )
    }

    @Test
    fun observeDaily_withNoProgressRows_emitsBothWithZeroCountNotDone() = runBlocking {
        database.achievementDao().insert(sampleAchievement("Read"))
        database.achievementDao().insert(sampleAchievement("Run"))

        val result = repository.observeDaily().first()

        assertEquals(2, result.size)
        assertTrue(result.all { it.count == 0 && !it.isDone })
    }

    @Test
    fun observeDaily_withPartialProgressRow_reportsPartialCountNotDone() = runBlocking {
        val id = database.achievementDao().insert(sampleAchievement("Read"))
        insertProgress(id, today.toString(), count = 2, done = false)

        val result = repository.observeDaily().first()

        assertEquals(2, result.single().count)
        assertFalse(result.single().isDone)
    }

    @Test
    fun observeDaily_withCompletedProgressRow_reportsDone() = runBlocking {
        val id = database.achievementDao().insert(sampleAchievement("Read"))
        insertProgress(id, today.toString(), count = 3, done = true)

        val result = repository.observeDaily().first()

        assertTrue(result.single().isDone)
    }

    @Test
    fun observeDaily_withProgressRowKeyedToYesterday_doesNotLeakIntoToday() = runBlocking {
        val id = database.achievementDao().insert(sampleAchievement("Read"))
        insertProgress(id, today.minusDays(1).toString(), count = 5, done = true)

        val result = repository.observeDaily().first()

        assertEquals(0, result.single().count)
        assertFalse(result.single().isDone)
    }

    @Test
    fun observeDaily_emitsAgain_whenAnAchievementIsInserted_withNoExplicitRefreshCall() = runBlocking {
        repository.observeDaily().test {
            assertEquals(0, awaitItem().size)

            repository.create("Read", null, AccentSwatch.SKY, target = 1)

            val afterInsert = awaitItem()
            assertEquals(1, afterInsert.size)
            assertEquals("Read", afterInsert.single().name)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun create_writesAnAchievementWithZeroedStreaksAndNoTierThresholds() = runBlocking {
        val id = repository.create("Read", "📖", AccentSwatch.EMERALD, target = 3)

        val entity = database.achievementDao().getById(id)
        assertEquals(0, entity?.currentStreak)
        assertEquals(0, entity?.bestStreak)
        assertNull(entity?.streakCachedForPeriodKey)
        assertNull(entity?.bronzeThreshold)
        assertNull(entity?.silverThreshold)
        assertNull(entity?.goldThreshold)
        assertEquals(today.toEpochDay(), entity?.createdAtEpochDay)
    }
}
