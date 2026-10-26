package com.achievementfactory.ui.create

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.input.KeyboardType
import com.achievementfactory.domain.tier.Tier
import com.achievementfactory.ui.theme.Spacing
import com.achievementfactory.ui.theme.color

/**
 * Collapsed-by-default advanced area (D-19) holding the counter target and the optional
 * Bronze/Silver/Gold ladder (TIER-02). The common once-a-day habit never has to look inside this
 * section. While the ladder is on, the counter target field is replaced by the three tier fields —
 * Gold's count is what "done" means and the lowest tier's count is what keeps the streak (TIER-06),
 * so a separate target would be a third, competing answer.
 */
@Composable
fun AdvancedSection(
    expanded: Boolean = false,
    targetInput: String,
    onToggle: () -> Unit,
    onTargetChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    tiersEnabled: Boolean = false,
    bronzeInput: String = "",
    silverInput: String = "",
    goldInput: String = "",
    tierError: String? = null,
    targetError: String? = null,
    ladderLocked: Boolean = false,
    onTiersEnabledChange: (Boolean) -> Unit = {},
    onBronzeChange: (String) -> Unit = {},
    onSilverChange: (String) -> Unit = {},
    onGoldChange: (String) -> Unit = {},
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
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                if (!tiersEnabled) {
                    CounterTargetField(targetInput = targetInput, onTargetChange = onTargetChange, error = targetError)
                }
                TierLadderFields(
                    enabled = tiersEnabled,
                    bronzeInput = bronzeInput,
                    silverInput = silverInput,
                    goldInput = goldInput,
                    error = tierError,
                    locked = ladderLocked,
                    onEnabledChange = onTiersEnabledChange,
                    onBronzeChange = onBronzeChange,
                    onSilverChange = onSilverChange,
                    onGoldChange = onGoldChange,
                )
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
    error: String? = null,
) {
    OutlinedTextField(
        value = targetInput,
        onValueChange = onTargetChange,
        label = { Text("Counter target") },
        isError = error != null,
        supportingText = {
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error)
            } else {
                Text(
                    "The achievement is done when the count reaches this number " +
                        "(${CreateAchievementUiState.MIN_TARGET}–${CreateAchievementUiState.MAX_TARGET}).",
                )
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * The tier ladder toggle and, when on, one count field per tier (TIER-02), labelled with the
 * fixed tier names (TIER-03). Bronze and Silver are optional and Gold is required; the rules
 * (TIER-04, TIER-05) are checked on save, and [error] is shown under the fields rather than
 * blocking typing — so the user can fill the boxes in any order.
 */
@Composable
private fun TierLadderFields(
    enabled: Boolean,
    bronzeInput: String,
    silverInput: String,
    goldInput: String,
    error: String?,
    locked: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onBronzeChange: (String) -> Unit,
    onSilverChange: (String) -> Unit,
    onGoldChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Medal tiers", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Bronze, Silver and Gold counts. Gold marks it done; the lowest medal you set keeps your streak going.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // An achievement that already has medals can't drop them (ACH-05) — the switch stays on.
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                enabled = !locked,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
        if (enabled) {
            TierCountField(tier = Tier.BRONZE, value = bronzeInput, optional = true, isError = error != null, onValueChange = onBronzeChange)
            TierCountField(tier = Tier.SILVER, value = silverInput, optional = true, isError = error != null, onValueChange = onSilverChange)
            TierCountField(tier = Tier.GOLD, value = goldInput, optional = false, isError = error != null, onValueChange = onGoldChange)
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun TierCountField(
    tier: Tier,
    value: String,
    optional: Boolean,
    isError: Boolean,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = if (optional) "${tier.displayName} (optional)" else tier.displayName,
                color = tier.color(),
            )
        },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
