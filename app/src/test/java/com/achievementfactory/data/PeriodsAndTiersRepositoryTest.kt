package com.achievementfactory.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.data.achievement.AchievementRepository
import com.achievementfactory.domain.maintenance.ReconcileStreaksUseCase
import com.achievementfactory.domain.streak.StreakCalculator
import com.achievementfactory.domain.tier.Tier
import com.achievementfactory.domain.tier.TierLadder
import com.achievementfactory.period.AppClock
import com.achievementfactory.period.CurrentPeriodProvider
import com.achievementfactory.period.PeriodEngine
import com.achievementfactory.period.PeriodKey
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
 * Phase 2's data-layer behaviour, end to end against an in-memory Room database under
 * Robolectric: weekly/monthly achievements key their progress to the Monday / calendar month
 * (PERIOD-02, PERIOD-03), their streaks walk in their own unit, and a tier ladder is done only at
 * Gold while its lowest tier keeps the streak (TIER-06), with the highest tier reached snapshotted
 * per period (TRACK-09) and reported as one-shot edges on
 * [com.achievementfactory.domain.model.IncrementOutcome].
 */
@RunWith(RobolectricTestRunner::class)
class PeriodsAndTiersRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: AchievementRepository
    private lateinit var currentPeriodProvider: CurrentPeriodProvider

    // Wednesday 2026-08-12 — mid-week and mid-month, so period boundaries are clearly distinct.
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

    private suspend fun create(
        periodType: PeriodType = PeriodType.DAILY,
        target: Int = 1,
        tiers: TierLadder? = null,
    ): Long = repository.create("Item", null, AccentSwatch.SKY, target, periodType, tiers)

    // --- Periods -------------------------------------------------------------------------------

    @Test
    fun weeklyIncrement_isKeyedToThatWeeksMonday() = runBlocking {
        val id = create(PeriodType.WEEKLY)

        val outcome = repository.increment(id)!!

        assertEquals(PeriodKey("2026-08-10"), outcome.undoToken.periodKey)
        assertEquals(1, database.progressDao().countFor(id, "2026-08-10"))
    }

    @Test
    fun monthlyIncrement_isKeyedToTheCalendarMonth() = runBlocking {
        val id = create(PeriodType.MONTHLY)

        val outcome = repository.increment(id)!!

        assertEquals(PeriodKey("2026-08"), outcome.undoToken.periodKey)
        assertEquals(1, database.progressDao().countFor(id, "2026-08"))
    }

    @Test
    fun observePeriod_onlyReturnsAchievementsOfThatPeriodType() = runBlocking {
        create(PeriodType.DAILY)
        val weeklyId = create(PeriodType.WEEKLY)
        create(PeriodType.MONTHLY)

        val weekly = repository.observePeriod(PeriodType.WEEKLY).first()

        assertEquals(listOf(weeklyId), weekly.map { it.id })
        assertEquals(PeriodType.WEEKLY, weekly.single().periodType)
    }

    /**
     * A weekly key is textually the daily key of that week's Monday. The join is by achievement
     * id, so a daily achievement completed on a Monday must never make a weekly card look done.
     */
    @Test
    fun dailyProgressOnAMonday_neverLeaksIntoAWeeklyCard() = runBlocking {
        currentDate.set(LocalDate.of(2026, 8, 10)) // Monday
        currentPeriodProvider.refresh()
        val dailyId = create(PeriodType.DAILY)
        val weeklyId = create(PeriodType.WEEKLY, target = 3)

        repository.increment(dailyId)

        val weekly = repository.observePeriod(PeriodType.WEEKLY).first().single { it.id == weeklyId }
        assertEquals(0, weekly.count)
        assertFalse(weekly.isDone)
    }

    @Test
    fun weeklyCard_staysDoneAllWeek_andResetsWhenMondayArrives() = runBlocking {
        val id = create(PeriodType.WEEKLY)
        repository.increment(id)

        repository.observePeriod(PeriodType.WEEKLY).test {
            assertTrue(awaitItem().single().isDone)

            currentDate.set(LocalDate.of(2026, 8, 16)) // Sunday, same week
            currentPeriodProvider.refresh()
            expectNoEvents()

            currentDate.set(LocalDate.of(2026, 8, 17)) // Monday
            currentPeriodProvider.refresh()
            val nextWeek = awaitItem().single()
            assertFalse(nextWeek.isDone)
            assertEquals(0, nextWeek.count)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun weeklyStreak_countsConsecutiveWeeks_notDays() = runBlocking {
        val id = create(PeriodType.WEEKLY)

        currentDate.set(LocalDate.of(2026, 7, 29)) // week of 07-27
        assertEquals(1, repository.increment(id)!!.newStreak)

        currentDate.set(LocalDate.of(2026, 8, 7)) // week of 08-03, nine days later
        assertEquals(2, repository.increment(id)!!.newStreak)

        currentDate.set(LocalDate.of(2026, 8, 10)) // week of 08-10, the very next Monday
        assertEquals(3, repository.increment(id)!!.newStreak)
    }

    @Test
    fun monthlyStreak_breaksAfterASkippedMonth() = runBlocking {
        val id = create(PeriodType.MONTHLY)

        currentDate.set(LocalDate.of(2026, 5, 31))
        repository.increment(id)
        currentDate.set(LocalDate.of(2026, 6, 1))
        assertEquals(2, repository.increment(id)!!.newStreak)

        currentDate.set(LocalDate.of(2026, 8, 12)) // July skipped
        assertEquals(1, repository.increment(id)!!.newStreak)
    }

    @Test
    fun undo_ofAWeeklyTokenAfterTheWeekEnded_isRefused() = runBlocking {
        val id = create(PeriodType.WEEKLY, target = 5)
        val outcome = repository.increment(id)!!

        currentDate.set(LocalDate.of(2026, 8, 17)) // next Monday
        assertFalse(repository.undo(outcome.undoToken))
        assertEquals(1, database.progressDao().countFor(id, "2026-08-10"))
    }

    @Test
    fun undo_ofAWeeklyTokenLaterInTheSameWeek_isApplied() = runBlocking {
        val id = create(PeriodType.WEEKLY, target = 5)
        val outcome = repository.increment(id)!!

        currentDate.set(LocalDate.of(2026, 8, 15)) // Saturday, same week
        assertTrue(repository.undo(outcome.undoToken))
        assertEquals(0, database.progressDao().countFor(id, "2026-08-10"))
    }

    @Test
    fun observeDetail_ofAWeeklyAchievement_reportsThisWeeksCount() = runBlocking {
        val id = create(PeriodType.WEEKLY, target = 3)
        repository.increment(id)
        repository.increment(id)

        val detail = repository.observeDetail(id).first()!!

        assertEquals(PeriodType.WEEKLY, detail.periodType)
        assertEquals(2, detail.periodCount)
        assertFalse(detail.isDone)
    }

    // --- Tiers ---------------------------------------------------------------------------------

    @Test
    fun create_withALadder_storesTheLowestTierAsTheStreakThreshold() = runBlocking {
        val id = create(target = 99, tiers = TierLadder(bronze = null, silver = 2, gold = 4))

        val entity = database.achievementDao().getById(id)!!
        assertEquals(2, entity.counterTarget)
        assertNull(entity.bronzeThreshold)
        assertEquals(2, entity.silverThreshold)
        assertEquals(4, entity.goldThreshold)
    }

    @Test
    fun increments_walkTheLadder_streakAtTheLowestTier_upgradesBelowGold_completionAtGold() = runBlocking {
        val id = create(tiers = TierLadder(bronze = 2, silver = 3, gold = 5))

        val first = repository.increment(id)!!
        assertNull(first.tierReached)
        assertFalse(first.justCompleted)
        assertFalse(first.tierUpgraded)
        assertFalse(first.streakSecured)

        val bronze = repository.increment(id)!!
        assertEquals(Tier.BRONZE, bronze.tierReached)
        assertTrue("the lowest tier secures the streak", bronze.streakSecured)
        assertEquals(1, bronze.newStreak)
        assertTrue("Bronze is a medal upgrade", bronze.tierUpgraded)
        assertFalse("Bronze is not done", bronze.justCompleted)
        assertFalse(bronze.isDone)

        val silver = repository.increment(id)!!
        assertEquals(Tier.SILVER, silver.tierReached)
        assertTrue(silver.tierUpgraded)
        assertFalse(silver.streakSecured)
        assertFalse(silver.justCompleted)

        val stillSilver = repository.increment(id)!!
        assertEquals(Tier.SILVER, stillSilver.tierReached)
        assertFalse(stillSilver.tierUpgraded)

        val gold = repository.increment(id)!!
        assertEquals(Tier.GOLD, gold.tierReached)
        assertTrue("Gold is the completion edge", gold.justCompleted)
        assertTrue(gold.isDone)
        assertFalse("the completing tap is not also an upgrade", gold.tierUpgraded)
        assertFalse("the streak was already secured at Bronze", gold.streakSecured)
        assertEquals(1, gold.newStreak)

        val pastGold = repository.increment(id)!!
        assertEquals(Tier.GOLD, pastGold.tierReached)
        assertFalse(pastGold.tierUpgraded)
        assertFalse(pastGold.justCompleted)
    }

    @Test
    fun goldOnlyLadder_completesAndSecuresTheStreakOnTheSameTap() = runBlocking {
        val id = create(tiers = TierLadder(bronze = null, silver = null, gold = 2))

        repository.increment(id)
        val gold = repository.increment(id)!!

        assertTrue(gold.justCompleted)
        assertTrue(gold.streakSecured)
        assertFalse(gold.tierUpgraded)
        assertEquals(Tier.GOLD, gold.tierReached)
    }

    @Test
    fun tierReached_isSnapshottedOnTheProgressRow_andSurfacedOnTheCard() = runBlocking {
        val id = create(tiers = TierLadder(bronze = 1, silver = 2, gold = 3))
        repository.increment(id)
        repository.increment(id)

        assertEquals("SILVER", database.progressDao().getFor(id, "2026-08-12")?.tierReached)
        val card = repository.observePeriod(PeriodType.DAILY).first().single()
        assertEquals(Tier.SILVER, card.tierReached)
        assertEquals(TierLadder(1, 2, 3), card.tiers)
    }

    @Test
    fun ladderedCard_isNotDoneBelowGold_butItsStreakAlreadyCounts() = runBlocking {
        val id = create(tiers = TierLadder(bronze = 1, silver = 2, gold = 3))
        repository.increment(id) // Bronze

        val card = repository.observePeriod(PeriodType.DAILY).first().single()
        assertFalse("only Gold makes the card done", card.isDone)
        assertEquals(1, card.currentStreak)
        assertEquals("the ring fills toward Gold", 3, card.target)
    }

    @Test
    fun ladderedCard_isDoneAtGold() = runBlocking {
        val id = create(tiers = TierLadder(bronze = 1, silver = 2, gold = 3))
        repeat(3) { repository.increment(id) }

        val card = repository.observePeriod(PeriodType.DAILY).first().single()
        assertTrue(card.isDone)
        assertEquals(Tier.GOLD, card.tierReached)
        assertTrue(repository.observeDetail(id).first()!!.isDone)
    }

    @Test
    fun undoingGold_takesTheCardOutOfDone_butKeepsTheStreak() = runBlocking {
        val id = create(tiers = TierLadder(bronze = 1, silver = 2, gold = 3))
        repository.increment(id)
        repository.increment(id)
        val gold = repository.increment(id)!!

        assertTrue(repository.undo(gold.undoToken))

        val card = repository.observePeriod(PeriodType.DAILY).first().single()
        assertFalse(card.isDone)
        assertEquals(Tier.SILVER, card.tierReached)
        assertEquals(1, card.currentStreak)
    }

    @Test
    fun ladderStreak_keepsGoingOnDaysThatOnlyReachedTheLowestTier() = runBlocking {
        val id = create(tiers = TierLadder(bronze = 1, silver = 2, gold = 3))

        currentDate.set(LocalDate.of(2026, 8, 10))
        repeat(3) { repository.increment(id) } // Gold
        currentDate.set(LocalDate.of(2026, 8, 11))
        repository.increment(id) // Bronze only
        currentDate.set(LocalDate.of(2026, 8, 12))
        val today = repository.increment(id)!! // Bronze

        assertEquals(3, today.newStreak)
    }

    @Test
    fun undo_lowersTheSnapshottedTierWithTheCount() = runBlocking {
        val id = create(tiers = TierLadder(bronze = 1, silver = 2, gold = 3))
        repository.increment(id)
        val silver = repository.increment(id)!!

        assertTrue(repository.undo(silver.undoToken))

        val row = database.progressDao().getFor(id, "2026-08-12")!!
        assertEquals(1, row.count)
        assertEquals("BRONZE", row.tierReached)
        assertTrue(row.done)
    }

    @Test
    fun achievementWithNoLadder_neverSnapshotsATier() = runBlocking {
        val id = create(target = 1)
        repository.increment(id)
        repository.increment(id)

        assertNull(database.progressDao().getFor(id, "2026-08-12")?.tierReached)
        assertNull(repository.observePeriod(PeriodType.DAILY).first().single().tierReached)
    }

    @Test
    fun aClosedPeriodsTier_isNotRewrittenByTheNextPeriodsTaps() = runBlocking {
        val id = create(tiers = TierLadder(bronze = 1, silver = 2, gold = 3))
        repository.increment(id)
        repository.increment(id)
        repository.increment(id) // GOLD on 08-12

        currentDate.set(LocalDate.of(2026, 8, 13))
        repository.increment(id) // BRONZE on 08-13

        assertEquals("GOLD", database.progressDao().getFor(id, "2026-08-12")?.tierReached)
        assertEquals("BRONZE", database.progressDao().getFor(id, "2026-08-13")?.tierReached)
    }
}
