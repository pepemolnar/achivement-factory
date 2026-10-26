package com.achievementfactory.domain.model

import com.achievementfactory.domain.tier.Tier

/**
 * The result of a single atomic increment (PITFALLS 9). [justCompleted] is true only on the
 * exact increment that crosses the achievement's done threshold — a repeat tap after the target
 * is already met still reports [isDone] = true but [justCompleted] = false, so completion is
 * announced once per period, not once per tap. [newStreak] is the achievement's own cached
 * streak immediately after this call's forced reconciliation (see
 * [com.achievementfactory.data.achievement.AchievementRepository.increment]) — the edge the
 * one-shot completion event reads `isFirstFlame` off, rather than re-deriving it from a
 * separately-collected UI state snapshot that could race the same write.
 *
 * [tierReached] is the tier the resulting count reaches (null for an achievement with no ladder,
 * or below its done threshold). [tierUpgraded] is true only on the exact increment that crosses
 * from one tier into a *higher* one (CELEB-02) — never on the completing tap itself, which is
 * [justCompleted]'s edge, and never on a repeat tap within the same tier. Like [justCompleted],
 * both are derived from this call's own `newCount`, never from a second read.
 */
data class IncrementOutcome(
    val newCount: Int,
    val isDone: Boolean,
    val justCompleted: Boolean,
    val newStreak: Int,
    val undoToken: UndoToken,
    val tierReached: Tier? = null,
    val tierUpgraded: Boolean = false,
)
