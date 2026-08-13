package com.achievementfactory.domain.model

import com.achievementfactory.data.achievement.AccentSwatch

/**
 * The read-only shape backing the detail screen (TRACK-08, D-13) — the one place in this phase
 * [bestStreak] is exposed. Like [DailyAchievement], this is the only type that crosses from the
 * data layer into `ui/detail` (T-01-09); the Room entities never reach a composable directly.
 */
data class AchievementDetail(
    val id: Long,
    val name: String,
    val icon: String?,
    val accent: AccentSwatch,
    val target: Int,
    val todayCount: Int,
    val isDone: Boolean,
    val currentStreak: Int,
    val bestStreak: Int,
)
