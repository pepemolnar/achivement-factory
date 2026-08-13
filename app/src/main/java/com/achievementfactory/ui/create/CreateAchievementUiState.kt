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
    val targetInput: String = "1",
    val advancedExpanded: Boolean = false,
    val nameError: String? = null,
    val saveError: String? = null,
    val saved: Boolean = false,
) {
    /**
     * The counter target the save actually uses, coerced from [targetInput] only at the point of
     * reading rather than on every keystroke.
     *
     * The field holds raw text on purpose. When the `Int` was the state, a fully controlled
     * `value = target.toString()` field rewrote anything unparseable to `1` as it was typed:
     * clearing the box to type a new number snapped it back to `"1"`, so typing `5` produced
     * `15`, and pasting anything longer than `Int.MAX_VALUE` was silently replaced by `1` — the
     * opposite of what was asked for, with no error shown. Keeping the text and coercing here
     * means what the user sees is exactly what they typed.
     */
    val target: Int get() = targetInput.toIntOrNull()?.coerceIn(MIN_TARGET, MAX_TARGET) ?: MIN_TARGET

    companion object {
        const val MIN_TARGET = 1

        /**
         * Also the field's max input length in digits. Without a ceiling a target of
         * `2_000_000_000` is accepted and produces an achievement that can never be completed.
         */
        const val MAX_TARGET = 999
        const val MAX_TARGET_DIGITS = 3
    }
}
