package com.achievementfactory.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.period.PeriodType
import com.achievementfactory.ui.period.tabLabel
import com.achievementfactory.ui.theme.Spacing

/**
 * The full-screen achievement-creation form (D-14) — deliberately not a bottom sheet, so the
 * same layout is reusable by Phase 3's edit mode. The period picker sits in the main form (every
 * achievement has exactly one period, and it cannot change after creation), while the optional
 * tier ladder lives inside [AdvancedSection] next to the counter target (D-19).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAchievementScreen(
    uiState: CreateAchievementUiState,
    onNameChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onAccentChange: (AccentSwatch) -> Unit,
    onTargetChange: (String) -> Unit,
    onToggleAdvanced: () -> Unit,
    onSave: () -> Unit,
    onPeriodTypeChange: (PeriodType) -> Unit = {},
    onTiersEnabledChange: (Boolean) -> Unit = {},
    onBronzeChange: (String) -> Unit = {},
    onSilverChange: (String) -> Unit = {},
    onGoldChange: (String) -> Unit = {},
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.isEditing) "Edit achievement" else "New achievement",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Surface {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    // A failed write belongs next to the control that attempted it, not under the
                    // Name field — the name is not what went wrong.
                    uiState.saveError?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = Spacing.sm),
                        )
                    }
                    Button(
                        onClick = onSave,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (uiState.isEditing) "Save changes" else "Save achievement")
                    }
                }
            }
        },
    ) { innerPadding ->
        if (uiState.loading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChange,
                label = { Text("Name") },
                singleLine = true,
                isError = uiState.nameError != null,
                supportingText = {
                    uiState.nameError?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.icon ?: "",
                onValueChange = onIconChange,
                label = { Text("Icon") },
                singleLine = true,
                supportingText = { Text("Tap the emoji key on the keyboard to pick one.") },
                modifier = Modifier.width(120.dp),
            )

            AccentSwatchPicker(selected = uiState.accent, onSelect = onAccentChange)

            PeriodPicker(selected = uiState.periodType, onSelect = onPeriodTypeChange, locked = uiState.isEditing)

            Column(modifier = Modifier.padding(top = Spacing.lg)) {
                AdvancedSection(
                    expanded = uiState.advancedExpanded,
                    targetInput = uiState.targetInput,
                    onToggle = onToggleAdvanced,
                    onTargetChange = onTargetChange,
                    tiersEnabled = uiState.tiersEnabled,
                    bronzeInput = uiState.bronzeInput,
                    silverInput = uiState.silverInput,
                    goldInput = uiState.goldInput,
                    tierError = uiState.tierError ?: uiState.thresholdError.takeIf { uiState.tiersEnabled },
                    targetError = uiState.thresholdError.takeIf { !uiState.tiersEnabled },
                    ladderLocked = uiState.ladderLocked,
                    onTiersEnabledChange = onTiersEnabledChange,
                    onBronzeChange = onBronzeChange,
                    onSilverChange = onSilverChange,
                    onGoldChange = onGoldChange,
                )
            }
        }
        }
    }
}

/**
 * Daily / Weekly / Monthly (PERIOD-02, PERIOD-03) as one single-choice segmented row, in the same
 * order as the tab bar. Weeks run Monday–Sunday and months are calendar months; the caption says
 * so, because "weekly" alone does not.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodPicker(selected: PeriodType, onSelect: (PeriodType) -> Unit, locked: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(text = "Repeats", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = PeriodType.entries
            options.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = type == selected,
                    onClick = { onSelect(type) },
                    enabled = !locked || type == selected,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(type.tabLabel())
                }
            }
        }
        Text(
            text = if (locked) "The period can't change after an achievement is created." else when (selected) {
                PeriodType.DAILY -> "Resets every day at midnight."
                PeriodType.WEEKLY -> "Resets every Monday. Weeks run Monday to Sunday."
                PeriodType.MONTHLY -> "Resets on the 1st of every month."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
