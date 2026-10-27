package com.achievementfactory.period

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single source of "which period is it" every read flow hangs off (PERIOD-04) — one
 * [StateFlow] per [PeriodType]. All three are seeded once from [periodEngine] at construction;
 * [refresh] recomputes every key and reassigns it — [MutableStateFlow] already suppresses an
 * assignment equal to its current value, so refreshing on an unchanged day produces no
 * downstream re-emission, and a day change mid-week moves only the daily key. No other read path
 * may call `periodEngine.current` directly (grep-gated).
 *
 * Write paths in [com.achievementfactory.data.achievement.AchievementRepository] also go through
 * here: they call [refresh] and then read [current] for the achievement's own period type, which
 * still resolves the live clock at the instant of the write while guaranteeing the read flows are
 * keyed to the same period. Having writes resolve the clock independently of this provider would
 * open a window between real midnight and the arrival of a date-change broadcast in which a write
 * lands on the new period's key while the UI is still joined against the old one.
 */
class CurrentPeriodProvider(private val periodEngine: PeriodEngine) {

    private val keys: Map<PeriodType, MutableStateFlow<PeriodKey>> =
        PeriodType.entries.associateWith { MutableStateFlow(periodEngine.current(it)) }

    /** The current key for [type] — the only sanctioned "what period is it" read. */
    fun current(type: PeriodType): StateFlow<PeriodKey> = keys.getValue(type).asStateFlow()

    val currentDailyKey: StateFlow<PeriodKey> get() = current(PeriodType.DAILY)

    fun refresh() {
        keys.forEach { (type, flow) -> flow.value = periodEngine.current(type) }
    }
}
