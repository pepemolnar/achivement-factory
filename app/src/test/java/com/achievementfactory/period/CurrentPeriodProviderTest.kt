package com.achievementfactory.period

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

/**
 * A plain JVM test — [CurrentPeriodProvider] has no Android dependency, so no Robolectric is
 * needed here (unlike [com.achievementfactory.data.RolloverTest], which exercises it against a
 * real Room database).
 */
class CurrentPeriodProviderTest {

    private val currentDate = AtomicReference(LocalDate.of(2026, 8, 13))
    private val clock = object : AppClock {
        override fun today(): LocalDate = currentDate.get()
        override fun zone(): ZoneId = ZoneId.of("UTC")
        override fun epochDay(): Long = currentDate.get().toEpochDay()
    }
    private val periodEngine = PeriodEngine(clock)

    @Test
    fun refresh_withUnchangedDate_emitsNoNewItem() = runTest {
        val provider = CurrentPeriodProvider(periodEngine)

        provider.currentDailyKey.test {
            assertEquals(PeriodKey("2026-08-13"), awaitItem())

            provider.refresh() // same date — MutableStateFlow suppresses the reassignment

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun refresh_afterDateAdvances_emitsExactlyOneNewKey() = runTest {
        val provider = CurrentPeriodProvider(periodEngine)

        provider.currentDailyKey.test {
            assertEquals(PeriodKey("2026-08-13"), awaitItem())

            currentDate.set(LocalDate.of(2026, 8, 14))
            provider.refresh()

            assertEquals(PeriodKey("2026-08-14"), awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun refresh_acrossDstSpringForwardBoundary_emitsExactlyOneKeyChange() = runTest {
        currentDate.set(LocalDate.of(2026, 3, 7)) // the day before the 2026 US DST transition
        val provider = CurrentPeriodProvider(periodEngine)

        provider.currentDailyKey.test {
            assertEquals(PeriodKey("2026-03-07"), awaitItem())

            currentDate.set(LocalDate.of(2026, 3, 8)) // spring-forward date
            provider.refresh()

            assertEquals(PeriodKey("2026-03-08"), awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun weeklyKey_isThatWeeksMonday_andMovesOnlyWhenAMondayArrives() = runTest {
        currentDate.set(LocalDate.of(2026, 8, 15)) // Saturday
        val provider = CurrentPeriodProvider(periodEngine)

        provider.current(PeriodType.WEEKLY).test {
            assertEquals(PeriodKey("2026-08-10"), awaitItem())

            currentDate.set(LocalDate.of(2026, 8, 16)) // Sunday — same Monday-start week
            provider.refresh()
            expectNoEvents()

            currentDate.set(LocalDate.of(2026, 8, 17)) // Monday — a new week
            provider.refresh()
            assertEquals(PeriodKey("2026-08-17"), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun monthlyKey_movesOnlyWhenTheCalendarMonthChanges() = runTest {
        currentDate.set(LocalDate.of(2026, 1, 30))
        val provider = CurrentPeriodProvider(periodEngine)

        provider.current(PeriodType.MONTHLY).test {
            assertEquals(PeriodKey("2026-01"), awaitItem())

            currentDate.set(LocalDate.of(2026, 1, 31))
            provider.refresh()
            expectNoEvents()

            currentDate.set(LocalDate.of(2026, 2, 1))
            provider.refresh()
            assertEquals(PeriodKey("2026-02"), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun currentDailyKey_isTheSameFlowAsCurrentDaily() {
        val provider = CurrentPeriodProvider(periodEngine)
        assertEquals(provider.current(PeriodType.DAILY).value, provider.currentDailyKey.value)
    }
}
