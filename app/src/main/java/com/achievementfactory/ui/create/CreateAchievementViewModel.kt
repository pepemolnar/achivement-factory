package com.achievementfactory.ui.create

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.achievementfactory.AchievementFactoryApp
import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.data.achievement.AchievementRepository
import com.achievementfactory.data.todo.TodoRepository
import com.achievementfactory.domain.edit.ThresholdChangeError
import com.achievementfactory.domain.edit.ThresholdChangeRule
import com.achievementfactory.domain.model.AchievementDefinition
import com.achievementfactory.domain.model.UpdateResult
import com.achievementfactory.domain.tier.TierLadder
import com.achievementfactory.domain.tier.TierLadderError
import com.achievementfactory.domain.tier.TierLadderValidation
import com.achievementfactory.period.PeriodType
import java.text.BreakIterator
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val NAME_REQUIRED_ERROR = "Name is required."
private const val SAVE_FAILED_ERROR = "Couldn't save. Try again."
private const val NOT_FOUND_ERROR = "This achievement is no longer here."
private const val TAG = "CreateAchievementVM"

/** The message shown under the tier fields for each way a ladder can be invalid (TIER-04/05). */
internal fun TierLadderError.message(): String = when (this) {
    TierLadderError.MISSING_GOLD -> "Gold is required — every ladder tops out at Gold."
    TierLadderError.SKIPPED_LEVEL -> "Add Silver too — Bronze can't skip a level."
    TierLadderError.OUT_OF_RANGE ->
        "Medal counts must be between ${TierLadder.MIN_THRESHOLD} and ${TierLadder.MAX_THRESHOLD}."
    TierLadderError.NOT_INCREASING -> "Each medal needs a higher count than the one below it."
}

/** Why an edit's thresholds were refused, quoting the value it may not go below. */
internal fun ThresholdChangeError.message(): String = when (this) {
    is ThresholdChangeError.DoneLowered ->
        "The target (Gold, when medals are on) can only go up — it's $previous now."
}

/**
 * Form state, validation and save for the achievement form — creation (D-14 through D-19, plus
 * the period picker and tier ladder — PERIOD-02, PERIOD-03, TIER-02 through TIER-06) and, when
 * [editingId] is set, editing (ACH-03 – ACH-05). Copies the [ViewModel] + `Factory` convention
 * established by `PeriodViewModel`. [initialPeriodType] is the tab the FAB was pressed on, read
 * from the `create` route's argument by [Factory]; [editingId] comes from the `edit` route.
 *
 * In edit mode the period can't change, an existing ladder can't be switched off, and thresholds
 * are checked with [ThresholdChangeRule] before saving — the repository enforces the same rule
 * again inside its write transaction, so the form's check is a courtesy, not the guarantee.
 */
class CreateAchievementViewModel(
    private val repository: AchievementRepository,
    initialPeriodType: PeriodType = PeriodType.DAILY,
    private val editingId: Long? = null,
    // Nullable so achievement-only tests can keep constructing this without it; the app always
    // passes it (see [Factory]).
    private val todoRepository: TodoRepository? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CreateAchievementUiState(periodType = initialPeriodType, editingId = editingId, loading = editingId != null),
    )
    val uiState: StateFlow<CreateAchievementUiState> = _uiState.asStateFlow()

    private var saveInFlight = false

    /** The achievement as it was when edit mode loaded it — what thresholds are compared against. */
    private var original: AchievementDefinition? = null

    init {
        if (editingId != null) load(editingId)
    }

    private fun load(id: Long) {
        viewModelScope.launch {
            val definition = try {
                repository.getDefinition(id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                Log.w(TAG, "load failed for achievement id=$id", failure)
                null
            }
            if (definition == null) {
                _uiState.update { it.copy(loading = false, saveError = NOT_FOUND_ERROR) }
                return@launch
            }
            original = definition
            val tiers = definition.tiers
            // The form's target is always the done threshold — Gold when there are medals (the
            // stored counterTarget is the streak threshold then, which the form never shows).
            val doneThreshold = tiers?.completeThreshold ?: definition.target
            _uiState.update {
                it.copy(
                    loading = false,
                    name = definition.name,
                    icon = definition.icon,
                    accent = definition.accent,
                    periodType = definition.periodType,
                    targetInput = doneThreshold.toString(),
                    tiersEnabled = tiers != null,
                    bronzeInput = tiers?.bronze?.toString() ?: "",
                    silverInput = tiers?.silver?.toString() ?: "",
                    goldInput = tiers?.gold?.toString() ?: "",
                    advancedExpanded = tiers != null || doneThreshold > 1,
                )
            }
        }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, nameError = null) }
    }

    /**
     * Reduces the incoming text to its first grapheme cluster via [BreakIterator], never
     * [String.first] (which would split a surrogate pair and mangle a compound emoji). There is
     * no curated icon set anywhere: the system emoji keyboard is the picker (D-15).
     */
    fun onIconChange(icon: String) {
        _uiState.update { it.copy(icon = firstGrapheme(icon)) }
    }

    /** Achievement or todo (create mode only — an existing achievement can't become a todo). */
    fun onKindChange(kind: ItemKind) {
        if (_uiState.value.isEditing) return
        _uiState.update { it.copy(kind = kind, tierError = null) }
    }

    fun onAccentChange(accent: AccentSwatch) {
        _uiState.update { it.copy(accent = accent) }
    }

    /** Ignored in edit mode — the period can never change after creation (ACH-05). */
    fun onPeriodTypeChange(periodType: PeriodType) {
        if (_uiState.value.isEditing) return
        _uiState.update { it.copy(periodType = periodType) }
    }

    /**
     * Accepts the raw text and stores it verbatim, so the field never rewrites what the user is
     * mid-way through typing (see [CreateAchievementUiState.target]). Input that could not become
     * a valid target — anything non-digit, or more digits than
     * [CreateAchievementUiState.MAX_TARGET_DIGITS] — is rejected outright, leaving the previous
     * text in place rather than silently substituting a different number. A cleared field is
     * allowed and reads back as [CreateAchievementUiState.MIN_TARGET], never 0.
     */
    fun onTargetChange(rawTarget: String) {
        if (!isAcceptableCount(rawTarget)) return
        _uiState.update { it.copy(targetInput = rawTarget, thresholdError = null) }
    }

    fun onToggleAdvanced() {
        _uiState.update { it.copy(advancedExpanded = !it.advancedExpanded) }
    }

    /**
     * Switching medals on carries the target over as Gold; switching them off carries Gold back as
     * the target — the target and Gold are the same number by definition (TIER-06). A value the
     * user already typed into the field being filled is kept.
     */
    fun onTiersEnabledChange(enabled: Boolean) {
        _uiState.update {
            it.copy(
                tiersEnabled = enabled,
                goldInput = if (enabled && it.goldInput.isBlank()) it.targetInput else it.goldInput,
                targetInput = if (!enabled && it.goldInput.isNotBlank()) it.goldInput else it.targetInput,
                tierError = null,
                thresholdError = null,
            )
        }
    }

    /** Same raw-text, digits-only, 3-digit rule as [onTargetChange]; a cleared field is "no Bronze". */
    fun onBronzeChange(raw: String) {
        if (!isAcceptableCount(raw)) return
        _uiState.update { it.copy(bronzeInput = raw, tierError = null, thresholdError = null) }
    }

    fun onSilverChange(raw: String) {
        if (!isAcceptableCount(raw)) return
        _uiState.update { it.copy(silverInput = raw, tierError = null, thresholdError = null) }
    }

    fun onGoldChange(raw: String) {
        if (!isAcceptableCount(raw)) return
        _uiState.update { it.copy(goldInput = raw, tierError = null, thresholdError = null) }
    }

    /**
     * Validates that the trimmed name is non-empty and — when tiers are on — that the ladder is
     * valid, reporting every problem at once under its own field and returning without touching
     * the repository if there is any. An invalid ladder also opens the advanced area, so the
     * error is never hidden behind a collapsed section. Guarded so a second invocation while a
     * save is in flight, or after `saved` is already true, does nothing (double-tap safety).
     *
     * [saveInFlight] is released in a `finally` on purpose. It is the latch the double-tap guard
     * above reads, so any path that leaves it stuck at `true` — a thrown `SQLiteException`, or
     * cancellation when the user backs out mid-insert — would make the Save button silently dead
     * for the rest of this ViewModel's life. The `catch` exists for the same reason
     * [com.achievementfactory.ui.period.PeriodViewModel]'s does: an exception escaping a
     * `viewModelScope.launch` kills the process.
     */
    fun onSave() {
        val current = _uiState.value
        if (saveInFlight || current.saved || current.loading) return

        val trimmedName = current.name.trim()
        val nameError = if (trimmedName.isEmpty()) NAME_REQUIRED_ERROR else null

        if (current.kind == ItemKind.TODO) {
            saveTodo(trimmedName, nameError, current.periodType)
            return
        }

        var ladder: TierLadder? = null
        var tierError: String? = null
        if (current.tiersEnabled) {
            when (val validation = current.tierValidation) {
                is TierLadderValidation.Valid -> ladder = validation.ladder
                is TierLadderValidation.Invalid -> tierError = validation.error.message()
            }
        }

        if (nameError != null || tierError != null) {
            _uiState.update {
                it.copy(
                    nameError = nameError,
                    tierError = tierError,
                    advancedExpanded = it.advancedExpanded || tierError != null,
                )
            }
            return
        }

        val editing = original
        if (editingId != null && editing == null) return // edit mode whose load failed
        if (editing != null) {
            val refusal = ThresholdChangeRule.check(editing.target, editing.tiers, current.target, ladder)
            if (refusal != null) {
                _uiState.update { it.copy(thresholdError = refusal.message(), advancedExpanded = true) }
                return
            }
        }

        saveInFlight = true
        _uiState.update { it.copy(saveError = null) }
        viewModelScope.launch {
            try {
                if (editing != null) {
                    when (val result = repository.update(editing.id, trimmedName, current.icon, current.accent, current.target, ladder)) {
                        UpdateResult.Updated -> _uiState.update { it.copy(saved = true) }
                        UpdateResult.NotFound -> _uiState.update { it.copy(saveError = NOT_FOUND_ERROR) }
                        is UpdateResult.Refused -> _uiState.update {
                            it.copy(thresholdError = result.error.message(), advancedExpanded = true)
                        }
                    }
                    return@launch
                }
                repository.create(
                    trimmedName,
                    current.icon,
                    current.accent,
                    current.target,
                    current.periodType,
                    ladder,
                )
                _uiState.update { it.copy(saved = true) }
            } catch (cancellation: CancellationException) {
                // Never swallowed — cancellation must keep propagating or structured concurrency
                // breaks. The `finally` below still releases the latch.
                throw cancellation
            } catch (failure: Exception) {
                Log.w(TAG, "create failed for name=$trimmedName", failure)
                _uiState.update { it.copy(saveError = SAVE_FAILED_ERROR) }
            } finally {
                saveInFlight = false
            }
        }
    }

    /** A todo is just a title and its period (TODO-01): no icon, colour, target or medals. */
    private fun saveTodo(title: String, nameError: String?, periodType: PeriodType) {
        if (nameError != null) {
            _uiState.update { it.copy(nameError = nameError) }
            return
        }
        val todos = todoRepository ?: return
        saveInFlight = true
        _uiState.update { it.copy(saveError = null) }
        viewModelScope.launch {
            try {
                todos.create(title, periodType)
                _uiState.update { it.copy(saved = true) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                Log.w(TAG, "todo create failed for title=$title", failure)
                _uiState.update { it.copy(saveError = SAVE_FAILED_ERROR) }
            } finally {
                saveInFlight = false
            }
        }
    }

    private fun isAcceptableCount(raw: String): Boolean =
        raw.all(Char::isDigit) && raw.length <= CreateAchievementUiState.MAX_TARGET_DIGITS

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AchievementFactoryApp
                val savedStateHandle = createSavedStateHandle()
                // Tolerant parse: an absent or unrecognized argument opens the form on Daily.
                val requested = savedStateHandle.get<String>("periodType")
                val initialPeriodType = PeriodType.entries.firstOrNull { it.name == requested } ?: PeriodType.DAILY
                // Present only on the `edit/{achievementId}` route; null means "create".
                val editingId = savedStateHandle.get<Long>("achievementId")
                CreateAchievementViewModel(
                    app.container.achievementRepository,
                    initialPeriodType,
                    editingId,
                    app.container.todoRepository,
                )
            }
        }
    }
}

/**
 * Returns the first extended grapheme cluster of [input] using Unicode text-segmentation rules
 * (UAX #29) rather than character-index slicing, so a compound emoji is kept whole. Returns null
 * when the result is blank (T-01-11).
 */
internal fun firstGrapheme(input: String): String? {
    if (input.isEmpty()) return null
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(input)
    val end = iterator.next()
    if (end == BreakIterator.DONE) return null
    return input.substring(0, end).ifBlank { null }
}
