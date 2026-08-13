package com.achievementfactory.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.achievementfactory.AchievementFactoryApp
import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.data.achievement.AchievementRepository
import java.text.BreakIterator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val NAME_REQUIRED_ERROR = "Name is required."

/**
 * Form state, validation and save for the achievement-creation screen (D-14 through D-19).
 * Copies the [ViewModel] + `Factory` convention established by `DailyViewModel`.
 */
class CreateAchievementViewModel(
    private val repository: AchievementRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateAchievementUiState())
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

    /** Clamps to a minimum of 1; a cleared or non-numeric field falls back to 1, never 0. */
    fun onTargetChange(rawTarget: String) {
        val clamped = rawTarget.toIntOrNull()?.coerceAtLeast(1) ?: 1
        _uiState.update { it.copy(target = clamped) }
    }

    fun onToggleAdvanced() {
        _uiState.update { it.copy(advancedExpanded = !it.advancedExpanded) }
    }

    /**
     * Validates that the trimmed name is non-empty, setting [NAME_REQUIRED_ERROR] and returning
     * without touching the repository when it is not. Guarded so a second invocation while a
     * save is in flight, or after `saved` is already true, does nothing (double-tap safety).
     */
    fun onSave() {
        val current = _uiState.value
        if (saveInFlight || current.saved) return

        val trimmedName = current.name.trim()
        if (trimmedName.isEmpty()) {
            _uiState.update { it.copy(nameError = NAME_REQUIRED_ERROR) }
            return
        }

        saveInFlight = true
        viewModelScope.launch {
            repository.create(trimmedName, current.icon, current.accent, current.target)
            _uiState.update { it.copy(saved = true) }
            saveInFlight = false
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AchievementFactoryApp
                CreateAchievementViewModel(app.container.achievementRepository)
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
