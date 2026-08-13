package com.achievementfactory.period

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The single boundary where "now" enters this app's civil-date world.
 *
 * Every period-math consumer takes an [AppClock] by constructor instead of reading the wall
 * clock directly, so tests can inject a fixed date/zone instead of depending on real time
 * (PITFALLS 1, 6). Per D-27, the device clock is trusted outright here: there is no
 * monotonic-clock cross-check, no tamper detection, and no suspicious-jump heuristic anywhere
 * in this package or its callers.
 */
interface AppClock {
    /** The current civil date in [zone]. */
    fun today(): LocalDate

    /** The zone this clock resolves civil dates in. */
    fun zone(): ZoneId

    /** Epoch-day count for [today] — an audit-friendly `Long`, never used for period math. */
    fun epochDay(): Long
}

/**
 * The real, wall-clock-backed [AppClock]. Converts from [Instant] to a civil [LocalDate]
 * exactly once, at this boundary — every consumer downstream operates on [LocalDate]/[java.time.YearMonth]
 * only (PITFALLS 1).
 */
class SystemAppClock : AppClock {
    override fun today(): LocalDate = Instant.now().atZone(zone()).toLocalDate()

    override fun zone(): ZoneId = ZoneId.systemDefault()

    override fun epochDay(): Long = today().toEpochDay()
}
