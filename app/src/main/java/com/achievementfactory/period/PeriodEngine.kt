package com.achievementfactory.period

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * The single place all period math happens for DAILY, WEEKLY and MONTHLY periods.
 *
 * Everything here goes through `java.time` civil types exclusively — `LocalDate` and
 * `YearMonth` — never epoch-millis arithmetic, and week identity is always the `LocalDate` of
 * that week's Monday, never an ISO week-of-year number (PITFALLS 1, 2, 3; ARCHITECTURE.md "The
 * Period Model"). `DayOfWeek.MONDAY` is hardcoded, never derived from device locale.
 *
 * `previous`/`next` always re-derive the key by parsing it back to its underlying civil type,
 * stepping that type, and reformatting — the key string itself is never string-decremented,
 * so year-boundary weeks and variable month lengths are handled by the JDK, not hand-rolled
 * logic.
 */
class PeriodEngine(private val clock: AppClock) {

    fun keyFor(type: PeriodType, date: LocalDate): PeriodKey = when (type) {
        PeriodType.DAILY -> PeriodKey(date.toString())
        PeriodType.WEEKLY -> PeriodKey(mondayOf(date).toString())
        PeriodType.MONTHLY -> PeriodKey(YearMonth.from(date).toString())
    }

    fun current(type: PeriodType): PeriodKey = keyFor(type, clock.today())

    fun previous(type: PeriodType, key: PeriodKey): PeriodKey = when (type) {
        PeriodType.DAILY -> {
            val date = LocalDate.parse(key.value)
            keyFor(type, date.minusDays(1))
        }
        PeriodType.WEEKLY -> {
            val monday = LocalDate.parse(key.value)
            keyFor(type, monday.minusWeeks(1))
        }
        PeriodType.MONTHLY -> {
            val yearMonth = YearMonth.parse(key.value)
            keyFor(type, yearMonth.minusMonths(1).atDay(1))
        }
    }

    fun next(type: PeriodType, key: PeriodKey): PeriodKey = when (type) {
        PeriodType.DAILY -> {
            val date = LocalDate.parse(key.value)
            keyFor(type, date.plusDays(1))
        }
        PeriodType.WEEKLY -> {
            val monday = LocalDate.parse(key.value)
            keyFor(type, monday.plusWeeks(1))
        }
        PeriodType.MONTHLY -> {
            val yearMonth = YearMonth.parse(key.value)
            keyFor(type, yearMonth.plusMonths(1).atDay(1))
        }
    }

    fun startDateOf(type: PeriodType, key: PeriodKey): LocalDate = when (type) {
        PeriodType.DAILY -> LocalDate.parse(key.value)
        PeriodType.WEEKLY -> LocalDate.parse(key.value)
        PeriodType.MONTHLY -> YearMonth.parse(key.value).atDay(1)
    }

    private fun mondayOf(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
