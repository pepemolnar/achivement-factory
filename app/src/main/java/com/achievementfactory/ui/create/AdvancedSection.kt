package com.achievementfactory.ui.create

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.input.KeyboardType

/**
 * Collapsed-by-default advanced area (D-19) holding the counter target. The common once-a-day
 * habit never has to look inside this section. The future graded-threshold-ladder configuration
 * lands in this same area, next to the target field.
 */
@Composable
fun AdvancedSection(
    expanded: Boolean = false,
    targetInput: String,
    onToggle: () -> Unit,
    onTargetChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurface
    val chevronRotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "advancedChevron")

    Column(modifier = modifier.fillMaxWidth()) {
        TextButton(onClick = onToggle) {
            Text(text = "Advanced", color = if (expanded) activeColor else inactiveColor)
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = if (expanded) activeColor else inactiveColor,
                modifier = Modifier.rotate(chevronRotation),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                CounterTargetField(targetInput = targetInput, onTargetChange = onTargetChange)
                // Insertion point for the future graded-threshold-ladder fields (D-19) — nothing
                // added here yet.
            }
        }
    }
}

/**
 * The counter target field (D-19), hidden by default behind [AdvancedSection].
 *
 * [targetInput] is raw text, not a formatted `Int`: the field must render exactly what was typed,
 * including a momentarily empty box while the user replaces the number. Coercion happens once, at
 * read time, in [CreateAchievementUiState.target].
 */
@Composable
fun CounterTargetField(
    targetInput: String,
    onTargetChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = targetInput,
        onValueChange = onTargetChange,
        label = { Text("Counter target") },
        supportingText = {
            Text(
                "The achievement is done when the count reaches this number " +
                    "(${CreateAchievementUiState.MIN_TARGET}–${CreateAchievementUiState.MAX_TARGET}).",
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}
