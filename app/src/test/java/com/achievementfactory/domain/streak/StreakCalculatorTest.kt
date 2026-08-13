package com.achievementfactory.domain.streak

import com.achievementfactory.period.AppClock
import com.achievementfactory.period.PeriodEngine
import com.achievementfactory.period.PeriodKey
import com.achievementfactory.period.PeriodType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure-JVM coverage for [StreakCalculator] — no Robolectric, no Android dependency, matching the
 * calculator's own "no Android import" contract. [PeriodEngine] only needs an [AppClock] to
 * construct; `previous(...)` never reads it, so a fixed stub clock is enough here.
 */
class StreakCalculatorTest {

    private val clock = object : AppClock {
        override fun today(): LocalDate = LocalDate.of(2026, 8, 13)
        override fun zone(): ZoneId = ZoneId.of("UTC")
        override fun epochDay(): Long = today().toEpochDay()
    }
    private val periodEngine = PeriodEngine(clock)
    private val calculator = StreakCalculator(periodEngine)

    private fun dailyKey(date: LocalDate): PeriodKey = periodEngine.keyFor(PeriodType.DAILY, date)

    /** The DAILY period-key strings for [count] consecutive days ending at [endInclusive]. */
    private fun consecutiveDailyKeys(endInclusive: LocalDate, count: Int): Set<String> =
        (0 until count).map { dailyKey(endInclusive.minusDays(it.toLong())).value }.toSet()

    @Test
    fun noDonePeriods_yieldsCurrentZeroAndBestZero() {
        val today = dailyKey(LocalDate.of(2026, 8, 13))

        val snapshot = calculator.calculate(
            doneKeys = emptySet(),
            currentKey = today,
            periodType = PeriodType.DAILY,
            storedBest = 0,
        )

        assertEquals(0, snapshot.current)
        assertEquals(0, snapshot.best)
    }

    @Test
    fun todayDoneWithNothingBefore_yieldsCurrentOne() {
        val todayDate = LocalDate.of(2026, 8, 13)
        val today = dailyKey(todayDate)
        val doneKeys = setOf(today.value)

        val snapshot = calculator.calculate(doneKeys, today, PeriodType.DAILY, storedBest = 0)

        assertEquals(1, snapshot.current)
    }

    @Test
    fun todayDonePlusFourPrecedingDays_yieldsCurrentFive() {
        val todayDate = LocalDate.of(2026, 8, 13)
        val today = dailyKey(todayDate)
        val doneKeys = consecutiveDailyKeys(todayDate, count = 5)

        val snapshot = calculator.calculate(doneKeys, today, PeriodType.DAILY, storedBest = 0)

        assertEquals(5, snapshot.current)
    }

    @Test
    fun todayNotDoneButPreviousThreeDaysDone_yieldsCurrentThree() {
        val todayDate = LocalDate.of(2026, 8, 13)
        val today = dailyKey(todayDate)
        // Yesterday, the day before, and the day before that — today itself stays out of doneKeys.
        val doneKeys = consecutiveDailyKeys(todayDate.minusDays(1), count = 3)

        val snapshot = calculator.calculate(doneKeys, today, PeriodType.DAILY, storedBest = 0)

        assertEquals(3, snapshot.current)
    }

    @Test
    fun todayAndYesterdayBothMissed_withThreeDaysBeforeThoseDone_yieldsCurrentZero() {
        val todayDate = LocalDate.of(2026, 8, 13)
        val today = dailyKey(todayDate)
        // 08-10, 08-09, 08-08 done; 08-12 (yesterday) and 08-13 (today) are both gaps.
        val doneKeys = consecutiveDailyKeys(todayDate.minusDays(3), count = 3)

        val snapshot = calculator.calculate(doneKeys, today, PeriodType.DAILY, storedBest = 0)

        assertEquals(0, snapshot.current)
    }

    @Test
    fun fortyDayGapBetweenTwoRuns_countsOnlyTheRunEndingAtToday() {
        val todayDate = LocalDate.of(2026, 8, 13)
        val today = dailyKey(todayDate)
        val recentRun = consecutiveDailyKeys(todayDate, count = 6)
        val staleRunFortyDaysBack = consecutiveDailyKeys(todayDate.minusDays(40), count = 10)
        val doneKeys = recentRun + staleRunFortyDaysBack

        val snapshot = calculator.calculate(doneKeys, today, PeriodType.DAILY, storedBest = 0)

        assertEquals(6, snapshot.current)
    }

    @Test
    fun streakAcrossYearBoundary_countsEveryDayWithNoDiscontinuity() {
        val start = LocalDate.of(2026, 12, 28)
        val end = LocalDate.of(2027, 1, 4) // 8 consecutive days inclusive
        val today = dailyKey(end)
        val doneKeys = consecutiveDailyKeys(end, count = 8)
        check(doneKeys.contains(dailyKey(start).value)) { "fixture must cover the full range" }

        val snapshot = calculator.calculate(doneKeys, today, PeriodType.DAILY, storedBest = 0)

        assertEquals(8, snapshot.current)
    }

    @Test
    fun bestStreak_isMaxOfStoredBestAndComputedCurrent() {
        val todayDate = LocalDate.of(2026, 8, 13)
        val today = dailyKey(todayDate)
        val doneKeys = consecutiveDailyKeys(todayDate, count = 3)

        val snapshot = calculator.calculate(doneKeys, today, PeriodType.DAILY, storedBest = 20)

        assertEquals(3, snapshot.current)
        assertEquals(20, snapshot.best)
    }

    @Test
    fun bestStreak_neverDecreases_whenCurrentResetsToZero() {
        val todayDate = LocalDate.of(2026, 8, 13)
        val today = dailyKey(todayDate)

        val snapshot = calculator.calculate(
            doneKeys = emptySet(),
            currentKey = today,
            periodType = PeriodType.DAILY,
            storedBest = 45,
        )

        assertEquals(0, snapshot.current)
        assertEquals(45, snapshot.best)
    }
}
