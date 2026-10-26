package com.achievementfactory.ui.detail

import com.achievementfactory.domain.model.AchievementDetail

/**
 * The detail screen's render state — mirrors the shape of [com.achievementfactory.ui.period.PeriodUiState]
 * (`Loading`/content/terminal-case) so later screens in this project keep inheriting one
 * ViewModel/UiState convention rather than reinventing it per screen.
 */
sealed interface AchievementDetailUiState {

    data object Loading : AchievementDetailUiState

    data class Content(val detail: AchievementDetail) : AchievementDetailUiState

    /** The requested id resolves to no row — an unknown or since-removed achievement. */
    data object NotFound : AchievementDetailUiState
}
