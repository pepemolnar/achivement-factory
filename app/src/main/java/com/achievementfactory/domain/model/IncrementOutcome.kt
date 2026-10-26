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
 * For a laddered achievement "done" means Gold, while the streak is kept by the lowest tier, so
 * the two edges come apart:
 *
 * - [justCompleted] — this increment crossed into *done* (Gold, or the plain target with no
 *   ladder). The card's ring closes and it sinks into Done.
 * - [streakSecured] — this increment crossed the threshold that makes the period count toward the
 *   streak (the lowest tier, or the plain target). It defaults to [justCompleted] because, with no
 *   ladder or a Gold-only ladder, the two are the same tap.
 * - [tierUpgraded] — this increment reached a new, higher tier *without* completing: Bronze or
 *   Silver on a Gold-topped ladder (CELEB-02). Never true on the same tap as [justCompleted].
 *
 * [tierReached] is the tier the resulting count reaches (null with no ladder, or below the lowest
 * tier). All of these are derived from this call's own `newCount`, never from a second read.
 */
data class IncrementOutcome(
    val newCount: Int,
    val isDone: Boolean,
    val justCompleted: Boolean,
    val newStreak: Int,
    val undoToken: UndoToken,
    val tierReached: Tier? = null,
    val tierUpgraded: Boolean = false,
    val streakSecured: Boolean = justCompleted,
)
