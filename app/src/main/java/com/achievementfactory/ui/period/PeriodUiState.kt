package com.achievementfactory.ui.period

import com.achievementfactory.domain.model.TodoItem
import com.achievementfactory.domain.model.TrackedAchievement

/**
 * One period tab's render state (Daily, Weekly or Monthly — NAV-01). [Content] never carries a
 * "should animate"/"play the celebration" style field — that separation is what makes the
 * one-shot celebration-event stream (CELEB-04) possible; a `StateFlow` cannot express a one-shot
 * event, so events must never live here.
 */
sealed interface PeriodUiState {

    data object Loading : PeriodUiState

    data class Content(
        val outstanding: List<TrackedAchievement>,
        val done: List<TrackedAchievement>,
        val heldBackIds: Set<Long> = emptySet(),
        /** Every achievement in the tab's stored order (ACH-09) — what reorder mode shows. */
        val all: List<TrackedAchievement> = outstanding + done,
        /** This period's unfinished todos, listed after the outstanding achievements (NAV-02). */
        val outstandingTodos: List<TodoItem> = emptyList(),
        /** This period's ticked todos, in the Done section with the done achievements. */
        val doneTodos: List<TodoItem> = emptyList(),
    ) : PeriodUiState {
        val isEmpty: Boolean
            get() = outstanding.isEmpty() && done.isEmpty() && outstandingTodos.isEmpty() && doneTodos.isEmpty()

        val hasDoneSection: Boolean get() = done.isNotEmpty() || doneTodos.isNotEmpty()

        companion object {
            /**
             * Splits [achievements] into outstanding/done, keeping any achievement whose id is
             * in [heldBackIds] in the outstanding group even though it is done — for the duration
             * of its completion animation, so the sink into Done reads as a consequence of that
             * animation rather than interrupting it (D-06).
             */
            fun partition(
                achievements: List<TrackedAchievement>,
                heldBackIds: Set<Long> = emptySet(),
                todos: List<TodoItem> = emptyList(),
            ): Content {
                val (done, outstanding) = achievements.partition { it.isDone && it.id !in heldBackIds }
                val (doneTodos, outstandingTodos) = todos.partition { it.done }
                return Content(
                    outstanding = outstanding,
                    done = done,
                    heldBackIds = heldBackIds,
                    all = achievements,
                    outstandingTodos = outstandingTodos,
                    doneTodos = doneTodos,
                )
            }
        }
    }

    data class Error(val message: String) : PeriodUiState
}
