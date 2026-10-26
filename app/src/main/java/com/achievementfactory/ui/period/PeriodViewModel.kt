package com.achievementfactory.ui.period

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
import com.achievementfactory.domain.model.UndoToken
import com.achievementfactory.period.PeriodType
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val LOAD_ERROR_MESSAGE = "Couldn't load your achievements. Restart the app to try again."
private const val TAG = "PeriodViewModel"

/**
 * Buffer depth for the two one-shot event streams below, paired with `replay = 0` and
 * `BufferOverflow.DROP_OLDEST`.
 *
 * This combination — not `Channel(Channel.BUFFERED)` — is what actually makes CELEB-04 ("a
 * celebration plays exactly once") true. A `Channel` retains up to 64 *undelivered* elements and
 * hands every one of them to the next collector; `PeriodScreen` collects under
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
 * One period tab's ViewModel — the shape every screen-level ViewModel in this project copies
 * (ARCHITECTURE.md "Pattern 2"). One instance exists per [periodType] (keyed in `AppNavHost`), so
 * the Daily, Weekly and Monthly tabs each have their own held-back set and their own one-shot
 * event streams, and an event produced on one tab can never play on another. Reactively derives
 * [uiState] from [AchievementRepository.observePeriod]; never polls, never exposes a manual
 * refresh method.
 */
class PeriodViewModel(
    private val repository: AchievementRepository,
    val periodType: PeriodType = PeriodType.DAILY,
    // Nullable so the many achievement-only tests can keep constructing this with one repository;
    // the real app always passes it (see [factoryFor]).
    private val todoRepository: TodoRepository? = null,
) : ViewModel() {

    // The seam plan 01-04 built and this plan populates: an achievement whose id is in this set
    // stays in the `outstanding` group even though it is done, for exactly the duration of its
    // completion celebration (D-06) — never derived from the celebration event stream itself, so
    // uiState stays a plain reactive projection with no one-shot event folded into it.
    private val _heldBackIds = MutableStateFlow<Set<Long>>(emptySet())

    // One self-expiring release timer per held-back id — see [holdBack]. Main-thread confined:
    // every reader/writer runs on viewModelScope (Dispatchers.Main.immediate) or on a main-thread
    // UI callback, so a plain map needs no extra synchronization.
    private val heldBackReleaseTimers = mutableMapOf<Long, Job>()

    val uiState: StateFlow<PeriodUiState> = combine(
        repository.observePeriod(periodType),
        _heldBackIds,
        todoRepository?.observeTodos(periodType) ?: flowOf(emptyList()),
    ) { achievements, heldBackIds, todos ->
        PeriodUiState.Content.partition(achievements, heldBackIds, todos) as PeriodUiState
    }
        .catch { emit(PeriodUiState.Error(LOAD_ERROR_MESSAGE)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeriodUiState.Loading)

    // Undo is an event, not state (PITFALLS 8/Anti-Pattern 1). Never fold this token into
    // PeriodUiState. See EVENT_BUFFER_CAPACITY for why this is a zero-replay SharedFlow rather than
    // a Channel.
    private val _undoRequests = MutableSharedFlow<UndoToken>(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val undoRequests: SharedFlow<UndoToken> = _undoRequests.asSharedFlow()

    // Same one-shot pattern as undoRequests (CELEB-04). Emitted only from the exact transition
    // edges (IncrementOutcome.justCompleted / .tierUpgraded), never derived from a state snapshot.
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
     * `observePeriod` emission still shows the true stored state, so the UI cannot end up
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
            val tierReached = outcome.tierReached
            // The flame reacts only on the tap that actually moved the streak — for a ladder that is
            // the lowest medal, not Gold — and "first flame" only ever on that tap.
            val isFirstFlame = outcome.streakSecured && outcome.newStreak == 1
            if (outcome.justCompleted) {
                holdBack(id)
                _celebrations.tryEmit(
                    CelebrationEvent.Completed(
                        achievementId = id,
                        newStreak = outcome.newStreak,
                        isFirstFlame = isFirstFlame,
                        tier = tierReached,
                        ticksStreak = outcome.streakSecured,
                    ),
                )
            } else if (outcome.tierUpgraded && tierReached != null) {
                // No hold-back: a medal below Gold does not make the card done, so it is not
                // moving into the Done section — there is no sink to delay.
                _celebrations.tryEmit(
                    CelebrationEvent.TierUpgraded(
                        achievementId = id,
                        tier = tierReached,
                        ticksStreak = outcome.streakSecured,
                        newStreak = outcome.newStreak,
                        isFirstFlame = isFirstFlame,
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
     * Persists the order the user dragged the tab into (ACH-09). Fire-and-forget: the list is a
     * live Room flow, so the stored order comes straight back on the next emission. Guarded like
     * every other write here, because an exception escaping `viewModelScope.launch` kills the
     * process.
     */
    /**
     * Ticks or unticks a todo (TODO-03) — tapping again is the undo. No celebration event: a todo
     * is a one-off with no streak to tick (the full-tab moment that counts todos is Phase 5).
     */
    fun onToggleTodo(id: Long, done: Boolean) {
        val todos = todoRepository ?: return
        viewModelScope.launch {
            runCatching { todos.setDone(id, done) }
                .onFailure { Log.w(TAG, "toggle failed for todo id=$id", it) }
        }
    }

    /** Deletes a todo for good (TODO-06) — only ever called after the screen's confirmation. */
    fun onDeleteTodo(id: Long) {
        val todos = todoRepository ?: return
        viewModelScope.launch {
            runCatching { todos.delete(id) }
                .onFailure { Log.w(TAG, "delete failed for todo id=$id", it) }
        }
    }

    fun onReorder(orderedIds: List<Long>) {
        viewModelScope.launch {
            runCatching { repository.reorder(periodType, orderedIds) }
                .onFailure { Log.w(TAG, "reorder failed for $periodType", it) }
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
        /** The factory for one tab. Pair it with a per-[periodType] `key` in `viewModel(...)`. */
        fun factoryFor(periodType: PeriodType): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AchievementFactoryApp
                PeriodViewModel(app.container.achievementRepository, periodType, app.container.todoRepository)
            }
        }

        val Factory: ViewModelProvider.Factory = factoryFor(PeriodType.DAILY)
    }
}
