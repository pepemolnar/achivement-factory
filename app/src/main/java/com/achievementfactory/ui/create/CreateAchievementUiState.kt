package com.achievementfactory.ui.create

import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.domain.tier.TierLadder
import com.achievementfactory.domain.tier.TierLadderValidation
import com.achievementfactory.period.PeriodType

/**
 * Form state for the full-screen achievement-creation flow (D-14, D-17, D-19). `target`
 * defaults to 1 with [advancedExpanded] starting collapsed — the common once-a-day habit never
 * has to look at a counter control to create something. `saved` flips to true exactly once on a
 * successful save, driving the screen's single pop-back-stack navigation.
 *
 * [periodType] starts as the tab the FAB was pressed on. The tier ladder lives behind
 * [tiersEnabled] inside the advanced area (D-19); while it is on, the ladder's lowest tier *is*
 * the done threshold (TIER-06), so the plain counter target is not used.
 *
 * [nameError], [tierError] and [saveError] are deliberately separate fields: the first two are
 * validation results about specific fields and belong under them, the last is "the database write
 * failed" and belongs next to the Save button.
 */
data class CreateAchievementUiState(
    val name: String = "",
    val icon: String? = null,
    val accent: AccentSwatch = AccentSwatch.INDIGO,
    val periodType: PeriodType = PeriodType.DAILY,
    val targetInput: String = "1",
    val advancedExpanded: Boolean = false,
    val tiersEnabled: Boolean = false,
    val bronzeInput: String = "",
    val silverInput: String = "",
    val goldInput: String = "",
    val nameError: String? = null,
    val tierError: String? = null,
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

    /**
     * The tier fields read as a ladder. A blank field is "tier not configured" — never 0 — so an
     * untouched Bronze/Silver box simply produces a shorter ladder. Unlike [target], thresholds
     * are not coerced: an out-of-range or out-of-order value is reported, not silently fixed.
     */
    val tierValidation: TierLadderValidation
        get() = TierLadder.validate(
            bronze = bronzeInput.toIntOrNull(),
            silver = silverInput.toIntOrNull(),
            gold = goldInput.toIntOrNull(),
        )

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
