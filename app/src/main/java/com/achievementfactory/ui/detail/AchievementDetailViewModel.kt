package com.achievementfactory.ui.detail

import android.util.Log
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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "AchievementDetailVM"

/** Same small, zero-replay buffer as the tab screen's one-shot events (CONVENTIONS "State versus events"). */
private const val EVENT_BUFFER_CAPACITY = 8

/**
 * The detail screen's ViewModel — same `stateIn`/`WhileSubscribed(5_000)` shape as
 * [com.achievementfactory.ui.period.PeriodViewModel]. [Factory] reads `achievementId` off the
 * `SavedStateHandle` via `createSavedStateHandle()`, so the route argument declared in
 * `AppNavHost` reaches this constructor with no custom assisted-injection mechanism.
 */
class AchievementDetailViewModel(
    private val repository: AchievementRepository,
    private val achievementId: Long,
) : ViewModel() {

    val uiState: StateFlow<AchievementDetailUiState> = repository.observeDetail(achievementId)
        .map<AchievementDetail?, AchievementDetailUiState> { detail ->
            if (detail != null) AchievementDetailUiState.Content(detail) else AchievementDetailUiState.NotFound
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AchievementDetailUiState.Loading)

    // "Archiving finished, leave this screen" is a one-shot event, not state: a zero-replay
    // SharedFlow, exactly like the tab screen's undo and celebration events. Navigating away
    // *before* the write lands would cancel viewModelScope and could drop the archive, so the
    // screen only leaves once this fires.
    private val _closeRequests = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val closeRequests: SharedFlow<Unit> = _closeRequests.asSharedFlow()

    /** Archives this achievement (ACH-06), then asks the screen to close. Failures are logged and dropped. */
    fun onArchive() {
        viewModelScope.launch {
            val archived = runCatching { repository.archive(achievementId) }
                .onFailure { Log.w(TAG, "archive failed for achievement id=$achievementId", it) }
                .getOrDefault(false)
            if (archived) _closeRequests.tryEmit(Unit)
        }
    }

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
