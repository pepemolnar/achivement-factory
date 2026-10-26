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
private const val TAG = "CreateAchievementVM"

/** The message shown under the tier fields for each way a ladder can be invalid (TIER-04/05). */
internal fun TierLadderError.message(): String = when (this) {
    TierLadderError.MISSING_GOLD -> "Gold is required — every ladder tops out at Gold."
    TierLadderError.SKIPPED_LEVEL -> "Add Silver too — Bronze can't skip a level."
    TierLadderError.OUT_OF_RANGE ->
        "Medal counts must be between ${TierLadder.MIN_THRESHOLD} and ${TierLadder.MAX_THRESHOLD}."
    TierLadderError.NOT_INCREASING -> "Each medal needs a higher count than the one below it."
}

/**
 * Form state, validation and save for the achievement-creation screen (D-14 through D-19, plus
 * the period picker and tier ladder — PERIOD-02, PERIOD-03, TIER-02 through TIER-06). Copies the
 * [ViewModel] + `Factory` convention established by `PeriodViewModel`. [initialPeriodType] is the
 * tab the FAB was pressed on, read from the `create` route's argument by [Factory].
 */
class CreateAchievementViewModel(
    private val repository: AchievementRepository,
    initialPeriodType: PeriodType = PeriodType.DAILY,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateAchievementUiState(periodType = initialPeriodType))
    val uiState: StateFlow<CreateAchievementUiState> = _uiState.asStateFlow()

    private var saveInFlight = false

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

    fun onAccentChange(accent: AccentSwatch) {
        _uiState.update { it.copy(accent = accent) }
    }

    fun onPeriodTypeChange(periodType: PeriodType) {
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
        _uiState.update { it.copy(targetInput = rawTarget) }
    }

    fun onToggleAdvanced() {
        _uiState.update { it.copy(advancedExpanded = !it.advancedExpanded) }
    }

    fun onTiersEnabledChange(enabled: Boolean) {
        _uiState.update { it.copy(tiersEnabled = enabled, tierError = null) }
    }

    /** Same raw-text, digits-only, 3-digit rule as [onTargetChange]; a cleared field is "no Bronze". */
    fun onBronzeChange(raw: String) {
        if (!isAcceptableCount(raw)) return
        _uiState.update { it.copy(bronzeInput = raw, tierError = null) }
    }

    fun onSilverChange(raw: String) {
        if (!isAcceptableCount(raw)) return
        _uiState.update { it.copy(silverInput = raw, tierError = null) }
    }

    fun onGoldChange(raw: String) {
        if (!isAcceptableCount(raw)) return
        _uiState.update { it.copy(goldInput = raw, tierError = null) }
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
        if (saveInFlight || current.saved) return

        val trimmedName = current.name.trim()
        val nameError = if (trimmedName.isEmpty()) NAME_REQUIRED_ERROR else null

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

        saveInFlight = true
        _uiState.update { it.copy(saveError = null) }
        viewModelScope.launch {
            try {
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
                CreateAchievementViewModel(app.container.achievementRepository, initialPeriodType)
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
