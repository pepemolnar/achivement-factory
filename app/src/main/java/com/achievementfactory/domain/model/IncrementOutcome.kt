package com.achievementfactory.domain.model

/**
 * The result of a single atomic increment (PITFALLS 9). [justCompleted] is true only on the
 * exact increment that crosses the achievement's target — a repeat tap after the target is
 * already met still reports [isDone] = true but [justCompleted] = false, so completion is
 * announced once per period, not once per tap.
 */
data class IncrementOutcome(
    val newCount: Int,
    val isDone: Boolean,
    val justCompleted: Boolean,
    val undoToken: UndoToken,
)
