package com.achievementfactory.domain.model

import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.domain.tier.Tier
import com.achievementfactory.domain.tier.TierLadder
import com.achievementfactory.period.PeriodType

/**
 * The read-only shape backing the detail screen (TRACK-08, D-13) — the one place [bestStreak]
 * is exposed. Like [TrackedAchievement], this is the only type that crosses from the data layer
 * into `ui/detail` (T-01-09); the Room entities never reach a composable directly.
 *
 * [periodCount], [isDone] and [tierReached] describe the current period of [periodType] — today,
 * this week or this month.
 */
data class AchievementDetail(
    val id: Long,
    val name: String,
    val icon: String?,
    val accent: AccentSwatch,
    val target: Int,
    val periodCount: Int,
    val isDone: Boolean,
    val currentStreak: Int,
    val bestStreak: Int,
    val periodType: PeriodType = PeriodType.DAILY,
    val tiers: TierLadder? = null,
    val tierReached: Tier? = null,
)
