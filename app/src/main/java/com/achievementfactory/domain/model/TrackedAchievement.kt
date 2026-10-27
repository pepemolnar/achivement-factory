package com.achievementfactory.domain.model

import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.domain.tier.Tier
import com.achievementfactory.domain.tier.TierLadder
import com.achievementfactory.period.PeriodType

/**
 * The only type that crosses from the data layer into `ui/` for a period tab (T-01-09) —
 * [com.achievementfactory.data.achievement.AchievementEntity] and
 * [com.achievementfactory.data.progress.ProgressEntity] never reach a composable directly.
 *
 * [count], [isDone] and [tierReached] describe the *current* period of [periodType] only.
 * [tierReached] is read from the progress row's snapshotted column, never recomputed from
 * [tiers], for the same immutability reason [isDone] is (TRACK-10). [tiers] is null for an
 * achievement with no ladder; when present, [target] is [TierLadder.completeThreshold] (Gold) and
 * [isDone] means Gold was reached — reaching only the lowest tier keeps the streak alive but does
 * not make the card done (TIER-06).
 */
data class TrackedAchievement(
    val id: Long,
    val name: String,
    val icon: String?,
    val accent: AccentSwatch,
    val target: Int,
    val count: Int,
    val isDone: Boolean,
    val currentStreak: Int,
    val bestStreak: Int,
    val periodType: PeriodType = PeriodType.DAILY,
    val tiers: TierLadder? = null,
    val tierReached: Tier? = null,
)
