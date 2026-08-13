package com.achievementfactory.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.achievementfactory.AchievementFactoryApp
import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.data.achievement.AchievementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * RED-phase skeleton — intentionally incomplete so `CreateAchievementViewModelTest` fails before
 * the real behavior is implemented. Replaced by the GREEN-phase implementation in the same task.
 */
class CreateAchievementViewModel(
    private val repository: AchievementRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateAchievementUiState())
    val uiState: StateFlow<CreateAchievementUiState> = _uiState.asStateFlow()

    fun onNameChange(name: String) {
        // Not yet implemented.
    }

    fun onIconChange(icon: String) {
        // Not yet implemented.
    }

    fun onAccentChange(accent: AccentSwatch) {
        // Not yet implemented.
    }

    fun onTargetChange(rawTarget: String) {
        // Not yet implemented.
    }

    fun onToggleAdvanced() {
        // Not yet implemented.
    }

    fun onSave() {
        // Not yet implemented.
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

internal fun firstGrapheme(input: String): String? = null
