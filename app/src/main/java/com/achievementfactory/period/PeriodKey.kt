package com.achievementfactory.period

/**
 * A normalized, zero-padded, lexicographically chronological period identity string.
 *
 * Formats (see PeriodEngine for how these are derived):
 * - [PeriodType.DAILY]   -> ISO date, e.g. `"2026-08-12"`
 * - [PeriodType.WEEKLY]  -> ISO date of that week's Monday, e.g. `"2026-08-10"`
 * - [PeriodType.MONTHLY] -> `"yyyy-MM"`, e.g. `"2026-08"`
 *
 * All three sort correctly as plain strings, which is what lets SQL `ORDER BY periodKey` and
 * simple string equality do the right thing with no date parsing inside SQLite.
 */
@JvmInline
value class PeriodKey(val value: String) {
    override fun toString(): String = value
}
