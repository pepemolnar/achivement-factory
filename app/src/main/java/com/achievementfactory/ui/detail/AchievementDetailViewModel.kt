package com.achievementfactory.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.achievementfactory.AchievementFactoryApp
import com.achievementfactory.data.achievement.AchievementRepository
import com.achievementfactory.domain.model.AchievementDetail
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The detail screen's ViewModel — same `stateIn`/`WhileSubscribed(5_000)` shape as
 * [com.achievementfactory.ui.daily.DailyViewModel]. [Factory] reads `achievementId` off the
 * `SavedStateHandle` via `createSavedStateHandle()`, so the route argument declared in
 * `AppNavHost` reaches this constructor with no custom assisted-injection mechanism.
 */
class AchievementDetailViewModel(
    repository: AchievementRepository,
    achievementId: Long,
) : ViewModel() {

    val uiState: StateFlow<AchievementDetailUiState> = repository.observeDetail(achievementId)
        .map<AchievementDetail?, AchievementDetailUiState> { detail ->
            if (detail != null) AchievementDetailUiState.Content(detail) else AchievementDetailUiState.NotFound
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AchievementDetailUiState.Loading)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AchievementFactoryApp
                val savedStateHandle = createSavedStateHandle()
                val achievementId = checkNotNull(savedStateHandle.get<Long>("achievementId")) {
                    "AchievementDetailViewModel requires a non-null achievementId route argument"
                }
                AchievementDetailViewModel(app.container.achievementRepository, achievementId)
            }
        }
    }
}
