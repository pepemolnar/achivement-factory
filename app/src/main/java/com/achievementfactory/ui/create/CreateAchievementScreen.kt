package com.achievementfactory.ui.create

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.ui.theme.Spacing

/**
 * The full-screen achievement-creation form (D-14) — deliberately not a bottom sheet, so the
 * same layout is reusable by Phase 3's edit mode and extensible with Phase 2's graded-threshold
 * fields inside [AdvancedSection] without a redesign.
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
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "New achievement", style = MaterialTheme.typography.headlineSmall) },
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
                        Text("Save achievement")
                    }
                }
            }
        },
    ) { innerPadding ->
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

            Column(modifier = Modifier.padding(top = Spacing.lg)) {
                AdvancedSection(
                    expanded = uiState.advancedExpanded,
                    target = uiState.target,
                    onToggle = onToggleAdvanced,
                    onTargetChange = onTargetChange,
                )
            }
        }
    }
}
