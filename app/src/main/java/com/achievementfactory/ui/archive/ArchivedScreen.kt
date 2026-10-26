package com.achievementfactory.ui.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.achievementfactory.domain.model.ArchivedAchievement
import com.achievementfactory.period.PeriodType
import com.achievementfactory.ui.achievement.AchievementGlyph
import com.achievementfactory.ui.period.tabLabel
import com.achievementfactory.ui.theme.Spacing
import com.achievementfactory.ui.theme.color

/**
 * Archived achievements (ACH-06 – ACH-08), grouped by period in tab order. Restore puts one back
 * at the end of its tab; deleting forever is the only irreversible action in the app, so it always
 * goes through a confirmation dialog that says plainly what will be lost. Reached from the tab
 * screen's top-bar menu. Phase 4 adds expired todos to this same screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedScreen(
    uiState: ArchivedUiState,
    onBack: () -> Unit,
    onRestore: (Long) -> Unit,
    onDeletePermanently: (Long) -> Unit,
) {
    // Held only for as long as the dialog is up — a confirmation is never persisted or replayed.
    var pendingDelete by remember { mutableStateOf<ArchivedAchievement?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Archived", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        when (uiState) {
            is ArchivedUiState.Loading -> Centered(innerPadding) { CircularProgressIndicator() }
            is ArchivedUiState.Error -> Centered(innerPadding) {
                Text(uiState.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            is ArchivedUiState.Content -> if (uiState.items.isEmpty()) {
                Centered(innerPadding) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Nothing archived", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Archive an achievement from its detail screen to tuck it away here.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                ArchivedList(
                    innerPadding = innerPadding,
                    items = uiState.items,
                    onRestore = onRestore,
                    onDeleteRequest = { pendingDelete = it },
                )
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete “${target.name}” forever?") },
            text = {
                Text(
                    "Its whole history goes with it — every period, streak and medal. " +
                        "This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePermanently(target.id)
                        pendingDelete = null
                    },
                ) {
                    Text("Delete forever", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun Centered(innerPadding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ArchivedList(
    innerPadding: PaddingValues,
    items: List<ArchivedAchievement>,
    onRestore: (Long) -> Unit,
    onDeleteRequest: (ArchivedAchievement) -> Unit,
) {
    val groups = PeriodType.entries.mapNotNull { type ->
        items.filter { it.periodType == type }.takeIf { it.isNotEmpty() }?.let { type to it }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = innerPadding.calculateTopPadding() + Spacing.md,
            bottom = innerPadding.calculateBottomPadding() + Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        groups.forEach { (type, rows) ->
            item(key = "header-${type.name}") {
                Text(
                    text = type.tabLabel(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
                )
            }
            items(rows, key = { it.id }) { row ->
                ArchivedRow(row, onRestore = { onRestore(row.id) }, onDelete = { onDeleteRequest(row) })
            }
        }
    }
}

@Composable
private fun ArchivedRow(row: ArchivedAchievement, onRestore: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(Spacing.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.cardMinHeight),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AchievementGlyph(icon = row.icon, name = row.name, accent = row.accent.color())
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.bodyLarge)
                if (row.bestStreak > 0) {
                    Text(
                        "Best streak ${row.bestStreak}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onRestore) { Text("Restore") }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.DeleteForever,
                    contentDescription = "Delete ${row.name} forever",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
