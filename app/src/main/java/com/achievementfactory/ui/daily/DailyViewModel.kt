package com.achievementfactory.ui.daily

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.achievementfactory.AchievementFactoryApp
import com.achievementfactory.data.achievement.AchievementRepository
import com.achievementfactory.domain.model.DailyAchievement
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val LOAD_ERROR_MESSAGE = "Couldn't load your achievements. Restart the app to try again."

/**
 * The Daily tab's ViewModel — the shape every later screen-level ViewModel in this project
 * copies (ARCHITECTURE.md "Pattern 2"). Reactively derives [uiState] from
 * [AchievementRepository.observeDaily]; never polls, never exposes a manual refresh method.
 */
class DailyViewModel(repository: AchievementRepository) : ViewModel() {

    val uiState: StateFlow<DailyUiState> = repository.observeDaily()
        .map<List<DailyAchievement>, DailyUiState> { achievements ->
            val (done, outstanding) = achievements.partition { it.isDone }
            DailyUiState.Content(outstanding = outstanding, done = done)
        }
        .catch { emit(DailyUiState.Error(LOAD_ERROR_MESSAGE)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyUiState.Loading)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AchievementFactoryApp
                DailyViewModel(app.container.achievementRepository)
            }
        }
    }
}
