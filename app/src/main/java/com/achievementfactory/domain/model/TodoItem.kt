package com.achievementfactory.domain.model

import com.achievementfactory.period.PeriodType

/** A todo in its tab's current period (TODO-02) — no counter, tier or streak, by design. */
data class TodoItem(
    val id: Long,
    val title: String,
    val periodType: PeriodType,
    val done: Boolean,
)

/** An unfinished todo whose period has ended (TODO-04), shown on the Archived screen. */
data class ExpiredTodo(
    val id: Long,
    val title: String,
    val periodType: PeriodType,
    /** The period it was assigned to, as stored (a date, a week's Monday, or `yyyy-MM`). */
    val periodKey: String,
)
