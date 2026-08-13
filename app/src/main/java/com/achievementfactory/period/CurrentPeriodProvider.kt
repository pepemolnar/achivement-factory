package com.achievementfactory.period

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single source of "what day is it" every read flow hangs off (PERIOD-04). Seeded once from
 * [periodEngine] at construction; [refresh] recomputes the key and reassigns it —
 * [MutableStateFlow] already suppresses an assignment equal to its current value, so refreshing
 * on an unchanged day produces no downstream re-emission. No other read path may call
 * `periodEngine.current` directly (grep-gated).
 *
 * Write paths in [com.achievementfactory.data.achievement.AchievementRepository] also go through
 * here: they call [refresh] and then read [currentDailyKey], which still resolves the live clock
 * at the instant of the write while guaranteeing the read flows are keyed to the same day. Having
 * writes resolve the clock independently of this provider would open a window between real
 * midnight and the arrival of a date-change broadcast in which a write lands on the new day's key
 * while the UI is still joined against the old one.
 */
class CurrentPeriodProvider(private val periodEngine: PeriodEngine) {

    private val _currentDailyKey = MutableStateFlow(periodEngine.current(PeriodType.DAILY))
    val currentDailyKey: StateFlow<PeriodKey> = _currentDailyKey.asStateFlow()

    fun refresh() {
        _currentDailyKey.value = periodEngine.current(PeriodType.DAILY)
    }
}
