package com.achievementfactory.ui.daily

import com.achievementfactory.domain.model.DailyAchievement

/**
 * The Daily tab's render state. [Content] never carries a "should animate"/"play the celebration"
 * style field — that separation is what makes the one-shot completion-event channel (CELEB-04,
 * plan 01-07) possible; a `StateFlow` cannot express a one-shot event, so events must never
 * live here.
 */
sealed interface DailyUiState {

    data object Loading : DailyUiState

    data class Content(
        val outstanding: List<DailyAchievement>,
        val done: List<DailyAchievement>,
        val heldBackIds: Set<Long> = emptySet(),
    ) : DailyUiState {
        val isEmpty: Boolean get() = outstanding.isEmpty() && done.isEmpty()

        companion object {
            /**
             * Splits [achievements] into outstanding/done, keeping any achievement whose id is
             * in [heldBackIds] in the outstanding group even though it is done. In this plan
             * [heldBackIds] is always empty at the call site (`DailyViewModel`), so a completed
             * card sinks into Done as soon as the write lands (D-05) — plan 01-07 is what
             * actually populates the set, for the duration of the completion animation, so the
             * sink reads as a consequence of that animation rather than interrupting it (D-06).
             */
            fun partition(achievements: List<DailyAchievement>, heldBackIds: Set<Long> = emptySet()): Content {
                val (done, outstanding) = achievements.partition { it.isDone && it.id !in heldBackIds }
                return Content(outstanding = outstanding, done = done, heldBackIds = heldBackIds)
            }
        }
    }

    data class Error(val message: String) : DailyUiState
}
