package com.achievementfactory.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.data.achievement.AchievementRepository
import com.achievementfactory.domain.edit.ThresholdChangeError
import com.achievementfactory.domain.maintenance.ReconcileStreaksUseCase
import com.achievementfactory.domain.model.UpdateResult
import com.achievementfactory.domain.streak.StreakCalculator
import com.achievementfactory.domain.tier.Tier
import com.achievementfactory.domain.tier.TierLadder
import com.achievementfactory.period.AppClock
import com.achievementfactory.period.CurrentPeriodProvider
import com.achievementfactory.period.PeriodEngine
import com.achievementfactory.period.PeriodType
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
import java.util.concurrent.atomic.AtomicReference

/**
 * Phase 3's lifecycle operations against an in-memory Room database under Robolectric: editing
 * (ACH-03 – ACH-05), archive/restore (ACH-06, ACH-07), permanent delete (ACH-08) and reorder
 * (ACH-09).
 */
@RunWith(RobolectricTestRunner::class)
class AchievementLifecycleRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: AchievementRepository

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

        val periodEngine = PeriodEngine(clock)
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
            currentPeriodProvider = CurrentPeriodProvider(periodEngine),
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private suspend fun create(name: String, target: Int = 1, periodType: PeriodType = PeriodType.DAILY, tiers: TierLadder? = null) =
        repository.create(name, null, AccentSwatch.SKY, target, periodType, tiers)

    private suspend fun dailyNames(): List<String> = repository.observePeriod(PeriodType.DAILY).first().map { it.name }

    // --- Order ---------------------------------------------------------------------------------

    @Test
    fun newAchievements_appendToTheEndOfTheirOwnTab() = runBlocking {
        val a = create("A")
        val w = create("W", periodType = PeriodType.WEEKLY)
        val b = create("B")

        assertEquals(listOf("A", "B"), dailyNames())
        assertEquals(0, database.achievementDao().getById(a)?.sortOrder)
        assertEquals(1, database.achievementDao().getById(b)?.sortOrder)
        assertEquals("positions are per tab", 0, database.achievementDao().getById(w)?.sortOrder)
    }

    @Test
    fun reorder_persistsTheNewOrder() = runBlocking {
        val a = create("A")
        val b = create("B")
        val c = create("C")

        repository.reorder(PeriodType.DAILY, listOf(c, a, b))

        assertEquals(listOf("C", "A", "B"), dailyNames())
    }

    @Test
    fun reorder_ignoresIdsFromAnotherTab() = runBlocking {
        val a = create("A")
        val w = create("W", periodType = PeriodType.WEEKLY)

        repository.reorder(PeriodType.DAILY, listOf(w, a))

        assertEquals("a keeps the position it was given in the list", 1, database.achievementDao().getById(a)?.sortOrder)
        assertEquals("the weekly achievement is left alone", 0, database.achievementDao().getById(w)?.sortOrder)
    }

    // --- Edit ----------------------------------------------------------------------------------

    @Test
    fun editingAppearance_keepsTheStreak() = runBlocking {
        val id = create("Read")
        repository.increment(id)

        val result = repository.update(id, "Read a book", "📚", AccentSwatch.PINK, target = 1, tiers = null)

        assertEquals(UpdateResult.Updated, result)
        val card = repository.observePeriod(PeriodType.DAILY).first().single()
        assertEquals("Read a book", card.name)
        assertEquals("📚", card.icon)
        assertEquals(AccentSwatch.PINK, card.accent)
        assertEquals(1, card.currentStreak)
        assertTrue(card.isDone)
    }

    @Test
    fun loweringTheTarget_isRefused_andNothingChanges() = runBlocking {
        val id = create("Read", target = 3)

        val result = repository.update(id, "Renamed", null, AccentSwatch.SKY, target = 2, tiers = null)

        assertEquals(UpdateResult.Refused(ThresholdChangeError.DoneLowered(3)), result)
        val entity = database.achievementDao().getById(id)!!
        assertEquals(3, entity.counterTarget)
        assertEquals("the whole edit is refused, not just the threshold", "Read", entity.name)
    }

    @Test
    fun raisingTheTargetPastTodaysCount_makesTodayNotDoneYet_andKeepsYesterdaysStreak() = runBlocking {
        val id = create("Read", target = 1)
        currentDate.set(LocalDate.of(2026, 8, 11))
        repository.increment(id)
        currentDate.set(LocalDate.of(2026, 8, 12))
        repository.increment(id)
        assertEquals(2, repository.observePeriod(PeriodType.DAILY).first().single().currentStreak)

        repository.update(id, "Read", null, AccentSwatch.SKY, target = 3, tiers = null)

        val card = repository.observePeriod(PeriodType.DAILY).first().single()
        assertFalse("today's count of 1 no longer meets the raised target", card.isDone)
        assertEquals("yesterday stays counted; today is just not done yet", 1, card.currentStreak)
        assertEquals(true, database.progressDao().getFor(id, "2026-08-11")?.done)
    }

    @Test
    fun raisingMedals_reSnapshotsTheCurrentPeriodsTier() = runBlocking {
        val id = create("Push-ups", tiers = TierLadder(bronze = 1, silver = 2, gold = 3))
        repeat(2) { repository.increment(id) } // Silver

        repository.update(id, "Push-ups", null, AccentSwatch.SKY, target = 1, tiers = TierLadder(1, 4, 6))

        val card = repository.observePeriod(PeriodType.DAILY).first().single()
        assertEquals(Tier.BRONZE, card.tierReached)
        assertEquals(6, card.target)
    }

    @Test
    fun loweringGold_isRefused() = runBlocking {
        val id = create("Push-ups", tiers = TierLadder(bronze = 1, silver = 2, gold = 3))

        val result = repository.update(id, "Push-ups", null, AccentSwatch.SKY, target = 1, tiers = TierLadder(null, 1, 2))

        assertEquals(UpdateResult.Refused(ThresholdChangeError.DoneLowered(3)), result)
    }

    @Test
    fun removingOrLoweringBronzeAndSilver_isAllowed_andKeepsGoldAsDone() = runBlocking {
        val id = create("Push-ups", tiers = TierLadder(bronze = 4, silver = 6, gold = 8))

        val result = repository.update(id, "Push-ups", null, AccentSwatch.SKY, target = 8, tiers = TierLadder(null, 2, 8))

        assertEquals(UpdateResult.Updated, result)
        val entity = database.achievementDao().getById(id)!!
        assertEquals("the streak threshold is now Silver", 2, entity.counterTarget)
        assertNull(entity.bronzeThreshold)
        assertEquals(8, entity.goldThreshold)
    }

    @Test
    fun addingMedalsBelowAPlainTarget_makesTheTargetGold() = runBlocking {
        val id = create("Read", target = 10)

        val result = repository.update(id, "Read", null, AccentSwatch.SKY, target = 10, tiers = TierLadder(3, 6, 10))

        assertEquals(UpdateResult.Updated, result)
        val card = repository.observePeriod(PeriodType.DAILY).first().single()
        assertEquals(10, card.target)
        assertEquals(TierLadder(3, 6, 10), card.tiers)
    }

    @Test
    fun loweringSilverMidPeriod_appliesToTheOpenPeriodAtOnce() = runBlocking {
        val id = create("Push-ups", tiers = TierLadder(bronze = null, silver = 4, gold = 8))
        repeat(2) { repository.increment(id) } // 2 taps: below Silver 4, no streak yet

        repository.update(id, "Push-ups", null, AccentSwatch.SKY, target = 8, tiers = TierLadder(null, 2, 8))

        val card = repository.observePeriod(PeriodType.DAILY).first().single()
        assertEquals(Tier.SILVER, card.tierReached)
        assertEquals(1, card.currentStreak)
        assertFalse("still not done: Gold is 8", card.isDone)
    }

    @Test
    fun getDefinition_returnsWhatTheFormNeeds() = runBlocking {
        val id = create("Run", periodType = PeriodType.WEEKLY, tiers = TierLadder(null, 2, 4))

        val definition = repository.getDefinition(id)!!

        assertEquals(PeriodType.WEEKLY, definition.periodType)
        assertEquals(TierLadder(null, 2, 4), definition.tiers)
        assertEquals(2, definition.target)
        assertFalse(definition.archived)
    }

    // --- Archive, restore, delete --------------------------------------------------------------

    @Test
    fun archive_removesItFromTheTab_keepsItsHistory_andListsItAsArchived() = runBlocking {
        val id = create("Read")
        repository.increment(id)

        assertTrue(repository.archive(id))

        assertTrue(dailyNames().isEmpty())
        assertEquals(listOf("Read"), repository.observeArchived().first().map { it.name })
        assertEquals(1, database.progressDao().countFor(id, "2026-08-12"))
    }

    @Test
    fun restore_putsItBackAtTheEndOfItsTab() = runBlocking {
        val a = create("A")
        create("B")
        repository.archive(a)

        assertTrue(repository.restore(a))

        assertEquals(listOf("B", "A"), dailyNames())
        assertTrue(repository.observeArchived().first().isEmpty())
    }

    @Test
    fun restore_afterMissedDays_showsTheStreakBroken_notFrozen() = runBlocking {
        val id = create("Read")
        currentDate.set(LocalDate.of(2026, 8, 10))
        repository.increment(id)
        repository.archive(id)

        currentDate.set(LocalDate.of(2026, 8, 13))
        repository.restore(id)

        assertEquals(0, repository.observePeriod(PeriodType.DAILY).first().single().currentStreak)
        assertEquals(1, database.achievementDao().getById(id)?.bestStreak)
    }

    @Test
    fun deletePermanently_refusesAnActiveAchievement() = runBlocking {
        val id = create("Read")

        assertFalse(repository.deletePermanently(id))
        assertEquals(listOf("Read"), dailyNames())
    }

    @Test
    fun deletePermanently_removesAnArchivedAchievementAndItsWholeHistory() = runBlocking {
        val id = create("Read")
        repository.increment(id)
        repository.archive(id)

        assertTrue(repository.deletePermanently(id))

        assertNull(database.achievementDao().getById(id))
        assertNull(database.progressDao().getFor(id, "2026-08-12"))
        assertTrue(repository.observeArchived().first().isEmpty())
    }
}
