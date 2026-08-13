package com.achievementfactory.ui.daily

import android.util.Log
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
import com.achievementfactory.ui.celebration.CelebrationEvent
import com.achievementfactory.ui.theme.MotionSpecs
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val LOAD_ERROR_MESSAGE = "Couldn't load your achievements. Restart the app to try again."
private const val TAG = "DailyViewModel"

/**
 * Buffer depth for the two one-shot event streams below, paired with `replay = 0` and
 * `BufferOverflow.DROP_OLDEST`.
 *
 * This combination — not `Channel(Channel.BUFFERED)` — is what actually makes CELEB-04 ("a
 * celebration plays exactly once") true. A `Channel` retains up to 64 *undelivered* elements and
 * hands every one of them to the next collector; `DailyScreen` collects under
 * `repeatOnLifecycle(STARTED)`, so an event produced while the screen is stopped (the write lands
 * asynchronously in `viewModelScope`, which outlives an `Activity` recreation) was buffered and
 * then replayed after rotation — the celebration re-running on an already-completed card and the
 * undo snackbar re-raising for a tap made before the rotation. A `MutableSharedFlow` with
 * `replay = 0` drops an emission outright when there are no subscribers, which is the required
 * behaviour: an event nobody was there to see never happened.
 *
 * The buffer only ever covers a *present but momentarily slow* collector — the undo collector
 * suspends for the whole snackbar duration — so a burst of taps is not lost. It is never storage
 * for a collector that has not attached yet.
 */
private const val EVENT_BUFFER_CAPACITY = 8

/**
 * The Daily tab's ViewModel — the shape every later screen-level ViewModel in this project
 * copies (ARCHITECTURE.md "Pattern 2"). Reactively derives [uiState] from
 * [AchievementRepository.observeDaily]; never polls, never exposes a manual refresh method.
 */
class DailyViewModel(private val repository: AchievementRepository) : ViewModel() {

    // The seam plan 01-04 built and this plan populates: an achievement whose id is in this set
    // stays in the `outstanding` group even though it is done, for exactly the duration of its
    // completion celebration (D-06) — never derived from the celebration event stream itself, so
    // uiState stays a plain reactive projection with no one-shot event folded into it.
    private val _heldBackIds = MutableStateFlow<Set<Long>>(emptySet())

    // One self-expiring release timer per held-back id — see [holdBack]. Main-thread confined:
    // every reader/writer runs on viewModelScope (Dispatchers.Main.immediate) or on a main-thread
    // UI callback, so a plain map needs no extra synchronization.
    private val heldBackReleaseTimers = mutableMapOf<Long, Job>()

    val uiState: StateFlow<DailyUiState> = combine(
        repository.observeDaily(),
        _heldBackIds,
    ) { achievements, heldBackIds -> DailyUiState.Content.partition(achievements, heldBackIds) as DailyUiState }
        .catch { emit(DailyUiState.Error(LOAD_ERROR_MESSAGE)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyUiState.Loading)

    // Undo is an event, not state (PITFALLS 8/Anti-Pattern 1). Never fold this token into
    // DailyUiState. See EVENT_BUFFER_CAPACITY for why this is a zero-replay SharedFlow rather than
    // a Channel.
    private val _undoRequests = MutableSharedFlow<UndoToken>(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val undoRequests: SharedFlow<UndoToken> = _undoRequests.asSharedFlow()

    // Same one-shot pattern as undoRequests (CELEB-04). Emitted only from the exact transition
    // edge (IncrementOutcome.justCompleted), never derived from a state snapshot.
    private val _celebrations = MutableSharedFlow<CelebrationEvent>(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val celebrations: SharedFlow<CelebrationEvent> = _celebrations.asSharedFlow()

    /**
     * Logs one tap of progress. Always +1, regardless of the achievement's target (D-02).
     *
     * The write is wrapped in [runCatching] on purpose: `uiState`'s `.catch` only covers the
     * *read* flow, so an exception thrown here — a `SQLiteException` from a full/corrupt/closed
     * database — would reach the default uncaught-exception handler and kill the process on the
     * app's primary interaction. A failed tap is dropped (and logged) instead; the next
     * `observeDaily` emission still shows the true stored state, so the UI cannot end up
     * disagreeing with the database. A `null` outcome means the row vanished between render and
     * tap, which is a normal race rather than a failure.
     */
    fun onIncrement(id: Long) {
        viewModelScope.launch {
            val outcome = runCatching { repository.increment(id) }
                .onFailure { Log.w(TAG, "increment failed for achievement id=$id", it) }
                .getOrNull() ?: return@launch

            // tryEmit, never emit: with DROP_OLDEST it always succeeds and never suspends, so a
            // tap can no longer wedge behind a full buffer waiting for a collector to appear.
            _undoRequests.tryEmit(outcome.undoToken)
            if (outcome.justCompleted) {
                holdBack(id)
                _celebrations.tryEmit(
                    CelebrationEvent.Completed(
                        achievementId = id,
                        newStreak = outcome.newStreak,
                        isFirstFlame = outcome.newStreak == 1,
                    ),
                )
            }
        }
    }

    /**
     * Reverses the specific increment [token] identifies. A stale/expired token is a no-op, and
     * so is a failed reversal — guarded for the same process-kill reason as [onIncrement].
     */
    fun onUndo(token: UndoToken) {
        viewModelScope.launch {
            runCatching { repository.undo(token) }
                .onFailure { Log.w(TAG, "undo failed for achievement id=${token.achievementId}", it) }
        }
    }

    /**
     * Called once the completion celebration's final stage finishes — releases [id] from the
     * held-back set so its card is free to sink into Done on the next [uiState] emission (D-06).
     * Idempotent: calling it for an id that was never held back, or twice for the same id, is a
     * no-op. This is the *fast path*; [holdBack]'s ceiling is the backstop when it never fires.
     */
    fun onCelebrationSettled(id: Long) {
        releaseHeldBack(id)
    }

    /**
     * Pins [id] into the `outstanding` group for the duration of its celebration, with a hard
     * ceiling of [MotionSpecs.CELEBRATION_MAX_HOLD_MS].
     *
     * The ceiling is the point: [onCelebrationSettled] is driven by a `LaunchedEffect` tied to the
     * celebrating card's composition, and an `Activity` recreation mid-animation, the card
     * scrolling out of the `LazyColumn`, or navigating away all cancel that effect before it can
     * report SETTLED. `_heldBackIds` lives here in the ViewModel and outlives all three, so
     * without the timer a done achievement would sit above the Done divider — rendering a full
     * ring, un-clearable — for the rest of this ViewModel's life. Whether the list is correct must
     * not depend on an animation being allowed to finish.
     */
    private fun holdBack(id: Long) {
        _heldBackIds.update { it + id }
        heldBackReleaseTimers.remove(id)?.cancel()
        heldBackReleaseTimers[id] = viewModelScope.launch {
            delay(MotionSpecs.CELEBRATION_MAX_HOLD_MS)
            releaseHeldBack(id)
        }
    }

    private fun releaseHeldBack(id: Long) {
        val timer = heldBackReleaseTimers.remove(id)
        _heldBackIds.update { it - id }
        timer?.cancel()
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
