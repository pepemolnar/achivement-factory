package com.achievementfactory.domain.model

import com.achievementfactory.data.achievement.AccentSwatch

/**
 * The only type that crosses from the data layer into `ui/` for the Daily tab (T-01-09) —
 * [com.achievementfactory.data.achievement.AchievementEntity] and
 * [com.achievementfactory.data.progress.ProgressEntity] never reach a composable directly.
 */
data class DailyAchievement(
    val id: Long,
    val name: String,
    val icon: String?,
    val accent: AccentSwatch,
    val target: Int,
    val count: Int,
    val isDone: Boolean,
    val currentStreak: Int,
    val bestStreak: Int,
)
