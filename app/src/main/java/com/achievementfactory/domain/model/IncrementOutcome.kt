package com.achievementfactory.domain.model

/**
 * The result of a single atomic increment (PITFALLS 9). [justCompleted] is true only on the
 * exact increment that crosses the achievement's target — a repeat tap after the target is
 * already met still reports [isDone] = true but [justCompleted] = false, so completion is
 * announced once per period, not once per tap. [newStreak] is the achievement's own cached
 * streak immediately after this call's forced reconciliation (see
 * [com.achievementfactory.data.achievement.AchievementRepository.increment]) — the edge plan
 * 01-07's one-shot completion event reads `isFirstFlame` off, rather than re-deriving it from a
 * separately-collected `DailyUiState` snapshot that could race the same write.
 */
data class IncrementOutcome(
    val newCount: Int,
    val isDone: Boolean,
    val justCompleted: Boolean,
    val newStreak: Int,
    val undoToken: UndoToken,
)
