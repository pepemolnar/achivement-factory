package com.achievementfactory.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.data.achievement.AchievementEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Runs on the JVM via Robolectric against an in-memory Room database — no device/emulator
 * needed, so `./gradlew :app:testDebugUnitTest` is a complete, device-free correctness gate
 * (SKELETON.md "Test strategy").
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseTest {

    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun sampleAchievement(name: String = "Read") = AchievementEntity(
        name = name,
        periodType = "DAILY",
        icon = null,
        accentSwatch = AccentSwatch.SKY.name,
        createdAtEpochDay = 20000L,
    )

    @Test
    fun insertedAchievement_isReturnedByObserveActive() = runBlocking {
        val id = database.achievementDao().insert(sampleAchievement())

        val active = database.achievementDao().observeActive("DAILY").first()

        assertEquals(1, active.size)
        assertEquals(id, active.first().id)
        assertEquals("Read", active.first().name)
    }

    @Test
    fun secondProgressInsert_withSameAchievementAndPeriodKey_doesNotProduceASecondRow() = runBlocking {
        val achievementId = database.achievementDao().insert(sampleAchievement())

        database.progressDao().insertIfAbsent(achievementId, "2026-08-12", nowEpochDay = 20000L)
        database.progressDao().insertIfAbsent(achievementId, "2026-08-12", nowEpochDay = 20000L)

        val rows = database.progressDao().observeForPeriod("2026-08-12").first()
        assertEquals(1, rows.size)
    }

    @Test
    fun deletingAnAchievement_cascadesAwayItsProgressRows() = runBlocking {
        val achievementId = database.achievementDao().insert(sampleAchievement())
        database.progressDao().insertIfAbsent(achievementId, "2026-08-12", nowEpochDay = 20000L)

        // No DAO delete method exists yet (not needed until Phase 3's archive/delete flow) —
        // exercise the CASCADE constraint directly via a raw statement against the in-memory DB.
        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM achievements WHERE id = $achievementId"
        )

        val remainingProgress = database.progressDao().observeForPeriod("2026-08-12").first()
        assertTrue(remainingProgress.isEmpty())
        val remainingAchievement = database.achievementDao().getById(achievementId)
        assertNull(remainingAchievement)
    }

    @Test
    fun progressRow_snapshotsDoneAndTierAtWriteTime() = runBlocking {
        val achievementId = database.achievementDao().insert(sampleAchievement())
        database.progressDao().insertIfAbsent(achievementId, "2026-08-12", nowEpochDay = 20000L)

        val row = database.progressDao().getFor(achievementId, "2026-08-12")
        assertEquals(false, row?.done)
        assertNull(row?.tierReached)
    }

    @Test
    fun updateStreakCache_persistsCurrentAndBestStreak() = runBlocking {
        val achievementId = database.achievementDao().insert(sampleAchievement())

        database.achievementDao().updateStreakCache(achievementId, current = 3, best = 5, cachedForKey = "2026-08-12")

        val achievement = database.achievementDao().getById(achievementId)
        assertEquals(3, achievement?.currentStreak)
        assertEquals(5, achievement?.bestStreak)
        assertEquals("2026-08-12", achievement?.streakCachedForPeriodKey)
    }
}
