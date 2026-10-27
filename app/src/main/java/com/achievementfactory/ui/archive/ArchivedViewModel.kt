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
import com.achievementfactory.data.todo.TodoRepository
import com.achievementfactory.domain.model.ArchivedAchievement
import com.achievementfactory.domain.model.ExpiredTodo
import com.achievementfactory.period.PeriodType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "ArchivedViewModel"
private const val LOAD_ERROR_MESSAGE = "Couldn't load the archive. Restart the app to try again."

/** The Archived screen's render state — same Loading/Content/Error shape as every other screen. */
sealed interface ArchivedUiState {
    data object Loading : ArchivedUiState
    data class Content(
        val items: List<ArchivedAchievement>,
        /** Unfinished todos whose period ended (TODO-04) — the archive is shared (NAV-03). */
        val todos: List<ExpiredTodo> = emptyList(),
    ) : ArchivedUiState {
        val isEmpty: Boolean get() = items.isEmpty() && todos.isEmpty()
    }
    data class Error(val message: String) : ArchivedUiState
}

/**
 * Backs the Archived screen (ACH-06 – ACH-08, TODO-04 – TODO-06, NAV-03): archived achievements
 * and expired todos together. Restore and delete are fire-and-forget writes: the list is a live Room flow, so a
 * restored or deleted row simply disappears on the next emission — no manual refresh. Both are
 * guarded like every other write in the app, because an exception escaping `viewModelScope.launch`
 * kills the process.
 */
class ArchivedViewModel(
    private val repository: AchievementRepository,
    // Nullable so achievement-only tests can construct this without it; the app always passes it.
    private val todoRepository: TodoRepository? = null,
) : ViewModel() {

    val uiState: StateFlow<ArchivedUiState> = combine(
        repository.observeArchived(),
        todoRepository?.observeExpired() ?: flowOf(emptyList()),
    ) { achievements, todos -> ArchivedUiState.Content(achievements, todos) as ArchivedUiState }
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

    /** Brings an expired todo back into today, this week or this month (TODO-05). */
    fun onRestoreTodo(id: Long, periodType: PeriodType) {
        val todos = todoRepository ?: return
        viewModelScope.launch {
            runCatching { todos.restore(id, periodType) }
                .onFailure { Log.w(TAG, "restore failed for todo id=$id", it) }
        }
    }

    /** Only ever called after the screen's confirmation dialog (TODO-06). */
    fun onDeleteTodo(id: Long) {
        val todos = todoRepository ?: return
        viewModelScope.launch {
            runCatching { todos.delete(id) }
                .onFailure { Log.w(TAG, "delete failed for todo id=$id", it) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AchievementFactoryApp
                ArchivedViewModel(app.container.achievementRepository, app.container.todoRepository)
            }
        }
    }
}
