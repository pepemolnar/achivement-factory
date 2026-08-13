package com.achievementfactory.ui.daily

import com.achievementfactory.domain.model.DailyAchievement

/**
 * The Daily tab's render state. [Content] never carries a "should animate"/"show confetti"
 * style field — that separation is what makes the one-shot completion-event channel (CELEB-04,
 * plan 01-07) possible; a `StateFlow` cannot express a one-shot event, so events must never
 * live here.
 */
sealed interface DailyUiState {

    data object Loading : DailyUiState

    data class Content(
        val outstanding: List<DailyAchievement>,
        val done: List<DailyAchievement>,
    ) : DailyUiState {
        val isEmpty: Boolean get() = outstanding.isEmpty() && done.isEmpty()
    }

    data class Error(val message: String) : DailyUiState
}
