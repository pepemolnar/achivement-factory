package com.achievementfactory.period

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Fixed [AppClock] test double — every test here injects an explicit date/zone instead of
 * reading the wall clock, so DST/year-boundary/locale scenarios can be fabricated and are
 * fully reproducible (PITFALLS 1, 4).
 */
private class FixedAppClock(
    private val date: LocalDate,
    private val zoneId: ZoneId = ZoneId.of("America/New_York"),
) : AppClock {
    override fun today(): LocalDate = date
    override fun zone(): ZoneId = zoneId
    override fun epochDay(): Long = date.toEpochDay()
}

class PeriodEngineTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    // --- DST ---------------------------------------------------------------------------

    @Test
    fun dailyKey_advancesByExactlyOneCalendarDay_acrossDstSpringForward() {
        // America/New_York: DST spring-forward in 2026 is Sunday 2026-03-08 (clocks skip 2am-3am).
        val engine = PeriodEngine(FixedAppClock(LocalDate.of(2026, 3, 8)))
        val beforeKey = engine.keyFor(PeriodType.DAILY, LocalDate.of(2026, 3, 7))
        val onKey = engine.keyFor(PeriodType.DAILY, LocalDate.of(2026, 3, 8))
        val afterKey = engine.keyFor(PeriodType.DAILY, LocalDate.of(2026, 3, 9))

        assertEquals(PeriodKey("2026-03-07"), beforeKey)
        assertEquals(PeriodKey("2026-03-08"), onKey)
        assertEquals(PeriodKey("2026-03-09"), afterKey)
        assertEquals(onKey, engine.next(PeriodType.DAILY, beforeKey))
        assertEquals(afterKey, engine.next(PeriodType.DAILY, onKey))
    }

    @Test
    fun dailyKey_advancesByExactlyOneCalendarDay_acrossDstFallBack() {
        // America/New_York: DST fall-back in 2026 is Sunday 2026-11-01 (clocks repeat 1am-2am).
        val engine = PeriodEngine(FixedAppClock(LocalDate.of(2026, 11, 1)))
        val beforeKey = engine.keyFor(PeriodType.DAILY, LocalDate.of(2026, 10, 31))
        val onKey = engine.keyFor(PeriodType.DAILY, LocalDate.of(2026, 11, 1))
        val afterKey = engine.keyFor(PeriodType.DAILY, LocalDate.of(2026, 11, 2))

        assertEquals(PeriodKey("2026-10-31"), beforeKey)
        assertEquals(PeriodKey("2026-11-01"), onKey)
        assertEquals(PeriodKey("2026-11-02"), afterKey)
        assertEquals(onKey, engine.next(PeriodType.DAILY, beforeKey))
        assertEquals(afterKey, engine.next(PeriodType.DAILY, onKey))
    }

    // --- Year boundary week ---------------------------------------------------------------

    @Test
    fun weeklyKeys_advanceBySevenDays_acrossYearBoundary_noRepeatOrSkip() {
        // 2026 is a 53-ISO-week year — the case ISO week numbers get wrong. Walk daily dates
        // from 2026-12-28 through 2027-01-04 and assert the derived week key only ever changes
        // in exact 7-day steps, with no repeat and no skip.
        val engine = PeriodEngine(FixedAppClock(LocalDate.of(2026, 12, 28)))
        var date = LocalDate.of(2026, 12, 28)
        val seenKeys = mutableListOf<PeriodKey>()
        while (!date.isAfter(LocalDate.of(2027, 1, 4))) {
            val key = engine.keyFor(PeriodType.WEEKLY, date)
            if (seenKeys.isEmpty() || seenKeys.last() != key) {
                seenKeys.add(key)
            }
            date = date.plusDays(1)
        }

        // Dec 28 2026 (Mon) - Jan 3 2027 (Sun) is one week (Monday 2026-12-28);
        // Jan 4 2027 (Mon) starts the next week (Monday 2027-01-04).
        assertEquals(listOf(PeriodKey("2026-12-28"), PeriodKey("2027-01-04")), seenKeys)
    }

    @Test
    fun weeklyKey_forJan1_2027_isMondayOfContainingWeek_dec28_2026() {
        val engine = PeriodEngine(FixedAppClock(LocalDate.of(2027, 1, 1)))
        val key = engine.keyFor(PeriodType.WEEKLY, LocalDate.of(2027, 1, 1))
        assertEquals(PeriodKey("2026-12-28"), key)
    }

    // --- Month lengths -----------------------------------------------------------------

    @Test
    fun monthlyKey_acrossVariable28_30_31DayMonths() {
        val engine = PeriodEngine(FixedAppClock(LocalDate.of(2026, 1, 1)))

        // 31-day month (January)
        assertEquals(PeriodKey("2026-01"), engine.keyFor(PeriodType.MONTHLY, LocalDate.of(2026, 1, 31)))
        // 30-day month (April)
        assertEquals(PeriodKey("2026-04"), engine.keyFor(PeriodType.MONTHLY, LocalDate.of(2026, 4, 30)))
        // 28-day month, non-leap February 2026
        assertEquals(PeriodKey("2026-02"), engine.keyFor(PeriodType.MONTHLY, LocalDate.of(2026, 2, 28)))

        assertEquals(PeriodKey("2026-02"), engine.next(PeriodType.MONTHLY, PeriodKey("2026-01")))
        assertEquals(PeriodKey("2026-05"), engine.next(PeriodType.MONTHLY, PeriodKey("2026-04")))
    }

    @Test
    fun monthlyKey_acrossFebruaryInLeapYear() {
        val engine = PeriodEngine(FixedAppClock(LocalDate.of(2028, 2, 29)))

        assertEquals(PeriodKey("2028-02"), engine.keyFor(PeriodType.MONTHLY, LocalDate.of(2028, 2, 29)))
        assertEquals(PeriodKey("2028-03"), engine.next(PeriodType.MONTHLY, PeriodKey("2028-02")))
        assertEquals(PeriodKey("2028-02"), engine.previous(PeriodType.MONTHLY, PeriodKey("2028-03")))
        assertEquals(
            YearMonth.of(2028, 2).atDay(1),
            engine.startDateOf(PeriodType.MONTHLY, PeriodKey("2028-02")),
        )
    }

    // --- previous/next round trip --------------------------------------------------------

    @Test
    fun previousAndNext_roundTrip_forDaily() {
        val engine = PeriodEngine(FixedAppClock(LocalDate.of(2026, 8, 12)))
        val key = engine.keyFor(PeriodType.DAILY, LocalDate.of(2026, 8, 12))
        val prev = engine.previous(PeriodType.DAILY, key)
        val next = engine.next(PeriodType.DAILY, key)

        assertEquals(PeriodKey("2026-08-11"), prev)
        assertEquals(PeriodKey("2026-08-13"), next)
        assertEquals(key, engine.next(PeriodType.DAILY, prev))
        assertEquals(key, engine.previous(PeriodType.DAILY, next))
    }

    @Test
    fun previousAndNext_roundTrip_forWeekly() {
        val engine = PeriodEngine(FixedAppClock(LocalDate.of(2026, 8, 12)))
        val key = engine.keyFor(PeriodType.WEEKLY, LocalDate.of(2026, 8, 12))
        val prev = engine.previous(PeriodType.WEEKLY, key)
        val next = engine.next(PeriodType.WEEKLY, key)

        assertEquals(PeriodKey("2026-08-03"), prev)
        assertEquals(PeriodKey("2026-08-17"), next)
        assertEquals(key, engine.next(PeriodType.WEEKLY, prev))
        assertEquals(key, engine.previous(PeriodType.WEEKLY, next))
    }

    @Test
    fun previousAndNext_roundTrip_forMonthly() {
        val engine = PeriodEngine(FixedAppClock(LocalDate.of(2026, 8, 12)))
        val key = engine.keyFor(PeriodType.MONTHLY, LocalDate.of(2026, 8, 12))
        val prev = engine.previous(PeriodType.MONTHLY, key)
        val next = engine.next(PeriodType.MONTHLY, key)

        assertEquals(PeriodKey("2026-07"), prev)
        assertEquals(PeriodKey("2026-09"), next)
        assertEquals(key, engine.next(PeriodType.MONTHLY, prev))
        assertEquals(key, engine.previous(PeriodType.MONTHLY, next))
    }

    // --- Locale independence -------------------------------------------------------------

    @Test
    fun weeklyMondayRule_isIdenticalUnderUsAndUkLocales() {
        val engine = PeriodEngine(FixedAppClock(LocalDate.of(2026, 8, 12)))
        val date = LocalDate.of(2026, 8, 12) // a Wednesday

        Locale.setDefault(Locale.US)
        val keyUnderUs = engine.keyFor(PeriodType.WEEKLY, date)

        Locale.setDefault(Locale.UK)
        val keyUnderUk = engine.keyFor(PeriodType.WEEKLY, date)

        assertEquals(PeriodKey("2026-08-10"), keyUnderUs)
        assertEquals(keyUnderUs, keyUnderUk)
        // Sanity: the US locale's native week-start (Sunday) would have produced a different
        // Monday-vs-Sunday-start key had any locale-derived week API leaked in — assert this
        // is NOT the case by confirming the same fixed Monday-anchored key both times.
        assertNotEquals(PeriodKey("2026-08-09"), keyUnderUs)
    }
}
