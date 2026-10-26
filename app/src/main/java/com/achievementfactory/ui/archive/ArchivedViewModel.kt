package com.achievementfactory.ui.archive

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.achievementfactory.AchievementFactoryApp
import com.achievementfactory.data.achievement.AchievementRepository
import com.achievementfactory.domain.model.ArchivedAchievement
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "ArchivedViewModel"
private const val LOAD_ERROR_MESSAGE = "Couldn't load the archive. Restart the app to try again."

/** The Archived screen's render state — same Loading/Content/Error shape as every other screen. */
sealed interface ArchivedUiState {
    data object Loading : ArchivedUiState
    data class Content(val items: List<ArchivedAchievement>) : ArchivedUiState
    data class Error(val message: String) : ArchivedUiState
}

/**
 * Backs the Archived screen (ACH-06 – ACH-08). Phase 4 extends this same screen with expired
 * todos. Restore and delete are fire-and-forget writes: the list is a live Room flow, so a
 * restored or deleted row simply disappears on the next emission — no manual refresh. Both are
 * guarded like every other write in the app, because an exception escaping `viewModelScope.launch`
 * kills the process.
 */
class ArchivedViewModel(private val repository: AchievementRepository) : ViewModel() {

    val uiState: StateFlow<ArchivedUiState> = repository.observeArchived()
        .map<List<ArchivedAchievement>, ArchivedUiState> { ArchivedUiState.Content(it) }
        .catch { emit(ArchivedUiState.Error(LOAD_ERROR_MESSAGE)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchivedUiState.Loading)

    fun onRestore(id: Long) {
        viewModelScope.launch {
            runCatching { repository.restore(id) }
                .onFailure { Log.w(TAG, "restore failed for achievement id=$id", it) }
        }
    }

    /** Only ever called after the screen's confirmation dialog (ACH-08). */
    fun onDeletePermanently(id: Long) {
        viewModelScope.launch {
            runCatching { repository.deletePermanently(id) }
                .onFailure { Log.w(TAG, "delete failed for achievement id=$id", it) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AchievementFactoryApp
                ArchivedViewModel(app.container.achievementRepository)
            }
        }
    }
}
