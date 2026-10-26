package com.achievementfactory.ui.period

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
    ) : PeriodUiState {
        val isEmpty: Boolean get() = outstanding.isEmpty() && done.isEmpty()

        companion object {
            /**
             * Splits [achievements] into outstanding/done, keeping any achievement whose id is
             * in [heldBackIds] in the outstanding group even though it is done — for the duration
             * of its completion animation, so the sink into Done reads as a consequence of that
             * animation rather than interrupting it (D-06).
             */
            fun partition(achievements: List<TrackedAchievement>, heldBackIds: Set<Long> = emptySet()): Content {
                val (done, outstanding) = achievements.partition { it.isDone && it.id !in heldBackIds }
                return Content(outstanding = outstanding, done = done, heldBackIds = heldBackIds)
            }
        }
    }

    data class Error(val message: String) : PeriodUiState
}
