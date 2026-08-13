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
}
