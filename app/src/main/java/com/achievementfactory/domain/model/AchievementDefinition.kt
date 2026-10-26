package com.achievementfactory.domain.model

import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.domain.tier.TierLadder
import com.achievementfactory.period.PeriodType

/**
 * An achievement's editable definition — what the edit form loads and what
 * [com.achievementfactory.domain.edit.ThresholdChangeRule] compares against. [target] is the
 * stored counter target: the plain target with no ladder, the streak threshold with one.
 */
data class AchievementDefinition(
    val id: Long,
    val name: String,
    val icon: String?,
    val accent: AccentSwatch,
    val periodType: PeriodType,
    val target: Int,
    val tiers: TierLadder?,
    val archived: Boolean,
)

/** One row of the Archived screen (ACH-06 – ACH-08). */
data class ArchivedAchievement(
    val id: Long,
    val name: String,
    val icon: String?,
    val accent: AccentSwatch,
    val periodType: PeriodType,
    val bestStreak: Int,
)

/** The outcome of [com.achievementfactory.data.achievement.AchievementRepository.update]. */
sealed interface UpdateResult {
    data object Updated : UpdateResult
    data object NotFound : UpdateResult
    data class Refused(val error: com.achievementfactory.domain.edit.ThresholdChangeError) : UpdateResult
}
