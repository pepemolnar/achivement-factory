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
import com.achievementfactory.domain.model.UndoToken
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val LOAD_ERROR_MESSAGE = "Couldn't load your achievements. Restart the app to try again."

/**
 * The Daily tab's ViewModel — the shape every later screen-level ViewModel in this project
 * copies (ARCHITECTURE.md "Pattern 2"). Reactively derives [uiState] from
 * [AchievementRepository.observeDaily]; never polls, never exposes a manual refresh method.
 */
class DailyViewModel(private val repository: AchievementRepository) : ViewModel() {

    val uiState: StateFlow<DailyUiState> = repository.observeDaily()
        .map<List<DailyAchievement>, DailyUiState> { achievements -> DailyUiState.Content.partition(achievements) }
        .catch { emit(DailyUiState.Error(LOAD_ERROR_MESSAGE)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyUiState.Loading)

    // Undo is an event, not state (PITFALLS 8/Anti-Pattern 1) — a Channel with no replay buffer
    // means a snackbar never re-shows on rotation/process restoration. Never fold this token
    // into DailyUiState.
    private val _undoRequests = Channel<UndoToken>(Channel.BUFFERED)
    val undoRequests: Flow<UndoToken> = _undoRequests.receiveAsFlow()

    /** Logs one tap of progress. Always +1, regardless of the achievement's target (D-02). */
    fun onIncrement(id: Long) {
        viewModelScope.launch {
            val outcome = repository.increment(id)
            _undoRequests.send(outcome.undoToken)
        }
    }

    /** Reverses the specific increment [token] identifies. A stale/expired token is a no-op. */
    fun onUndo(token: UndoToken) {
        viewModelScope.launch {
            repository.undo(token)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AchievementFactoryApp
                DailyViewModel(app.container.achievementRepository)
            }
        }
    }
}
