package com.achievementfactory.ui.create

import com.achievementfactory.data.achievement.AccentSwatch

/**
 * Form state for the full-screen achievement-creation flow (D-14, D-17, D-19). `target`
 * defaults to 1 with [advancedExpanded] starting collapsed — the common once-a-day habit never
 * has to look at a counter control to create something. `saved` flips to true exactly once on a
 * successful save, driving the screen's single pop-back-stack navigation.
 *
 * [nameError] and [saveError] are deliberately separate fields: the first is a validation result
 * about the name field specifically and belongs under it, the second is "the database write
 * failed" and belongs next to the Save button. Folding a write failure into [nameError] would
 * point the user at a field that is not the problem.
 */
data class CreateAchievementUiState(
    val name: String = "",
    val icon: String? = null,
    val accent: AccentSwatch = AccentSwatch.INDIGO,
    val target: Int = 1,
    val advancedExpanded: Boolean = false,
    val nameError: String? = null,
    val saveError: String? = null,
    val saved: Boolean = false,
)
