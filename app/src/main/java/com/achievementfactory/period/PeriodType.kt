package com.achievementfactory.period

/**
 * The three recurrence units an achievement (or todo) can be filed under.
 *
 * All three are fully implemented and tested from Phase 1 even though only [DAILY] is
 * reachable from any screen in this phase — the stored key format must be settled before any
 * row is ever written, and Phase 2 turns on [WEEKLY]/[MONTHLY] in the UI without touching this
 * package (see SKELETON.md).
 */
enum class PeriodType {
    DAILY,
    WEEKLY,
    MONTHLY,
}
