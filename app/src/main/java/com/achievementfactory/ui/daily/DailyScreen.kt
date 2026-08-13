package com.achievementfactory.ui.daily

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.achievementfactory.domain.model.UndoToken
import com.achievementfactory.ui.achievement.AchievementCard
import com.achievementfactory.ui.celebration.CelebrationEvent
import com.achievementfactory.ui.theme.Spacing
import kotlinx.coroutines.flow.Flow

/**
 * The Daily tab (NAV-04, TRACK-02, D-05): renders live Room data as neutral cards, groups
 * completed items under a Done divider, and shows the two-line empty state when there is
 * nothing to show. [onCreateClick] navigates to the creation screen (wired in `AppNavHost`).
 * [onIncrement]/[onLongPress] forward the whole-card tap/long-press gestures (D-01–D-03).
 * [undoRequests] is a one-shot event stream, not state (PITFALLS 8) — each emission raises the
 * undo snackbar exactly once, never shown again on rotation/process restoration. [celebrations] is
 * the same one-shot pattern (CELEB-04): each `CelebrationEvent.Completed` is tracked in local
 * composable state keyed by achievement id — so more than one card can celebrate at once — until
 * the animation's own final stage calls [onCelebrationSettled], which releases the id from
 * `DailyViewModel`'s held-back set (D-06). This plan tracks that local state; the animation that
 * actually consumes it is wired in the following plan task.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyScreen(
    uiState: DailyUiState,
    onCreateClick: () -> Unit,
    onIncrement: (Long) -> Unit,
    onLongPress: (Long) -> Unit,
    undoRequests: Flow<UndoToken>,
    onUndo: (UndoToken) -> Unit,
    celebrations: Flow<CelebrationEvent>,
    onCelebrationSettled: (Long) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val activeCelebrations = remember { mutableStateMapOf<Long, CelebrationEvent.Completed>() }

    LaunchedEffect(undoRequests, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            undoRequests.collect { token ->
                val result = snackbarHostState.showSnackbar(
                    message = "Progress updated",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    onUndo(token)
                }
            }
        }
    }

    LaunchedEffect(celebrations, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            celebrations.collect { event ->
                if (event is CelebrationEvent.Completed) {
                    activeCelebrations[event.achievementId] = event
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = "Daily", style = MaterialTheme.typography.headlineSmall) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateClick) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add achievement")
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        when (uiState) {
            is DailyUiState.Loading -> LoadingBody(innerPadding)
            is DailyUiState.Error -> ErrorBody(innerPadding, uiState.message)
            is DailyUiState.Content -> if (uiState.isEmpty) {
                EmptyBody(innerPadding)
            } else {
                DailyList(innerPadding, uiState, onIncrement, onLongPress)
            }
        }
    }
}

@Composable
private fun LoadingBody(innerPadding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorBody(innerPadding: PaddingValues, message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

/** The empty state (NAV-04, D-20): exactly two lines, no illustration, no starter suggestions. */
@Composable
private fun EmptyBody(innerPadding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No achievements yet",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Tap + to add your first one.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DailyList(
    innerPadding: PaddingValues,
    content: DailyUiState.Content,
    onIncrement: (Long) -> Unit,
    onLongPress: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = innerPadding.calculateTopPadding() + Spacing.md,
            bottom = innerPadding.calculateBottomPadding() + Spacing.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(content.outstanding, key = { it.id }) { achievement ->
            AchievementCard(
                achievement = achievement,
                onIncrement = { onIncrement(achievement.id) },
                onLongPress = { onLongPress(achievement.id) },
            )
        }

        if (content.done.isNotEmpty()) {
            item(key = "done-divider") {
                DoneSectionHeader()
            }
            items(content.done, key = { it.id }) { achievement ->
                AchievementCard(
                    achievement = achievement,
                    onIncrement = { onIncrement(achievement.id) },
                    onLongPress = { onLongPress(achievement.id) },
                )
            }
        }
    }
}

@Composable
private fun DoneSectionHeader() {
    Column(modifier = Modifier.padding(vertical = Spacing.xl)) {
        HorizontalDivider(thickness = 1.dp)
        Text(
            text = "Done",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = Spacing.md),
        )
    }
}
