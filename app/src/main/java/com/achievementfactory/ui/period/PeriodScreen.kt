package com.achievementfactory.ui.period

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
import com.achievementfactory.period.PeriodType
import com.achievementfactory.ui.achievement.AchievementCard
import com.achievementfactory.ui.celebration.CelebrationEvent
import com.achievementfactory.ui.theme.MotionSpecs
import com.achievementfactory.ui.theme.Spacing
import com.achievementfactory.ui.theme.rememberReducedMotion
import kotlinx.coroutines.flow.Flow

/**
 * One period tab — Daily, Weekly or Monthly (NAV-01, NAV-04, TRACK-02, D-05): renders live Room
 * data as neutral cards, groups completed items under a Done divider, and shows the two-line
 * empty state when there is nothing to show. [bottomBar] is the tab switcher `AppNavHost` slots
 * in, so all three tabs share this one `Scaffold` rather than nesting one inside another.
 * [onCreateClick] opens the creation screen pre-set to this tab's [periodType].
 *
 * [undoRequests] and [celebrations] are one-shot event streams, not state (PITFALLS 8, CELEB-04)
 * — each emission raises the snackbar / plays the celebration exactly once, never again on
 * rotation or process restoration. Each celebration is tracked in local composable state keyed by
 * achievement id — so more than one card can celebrate at once — and forwarded into that card
 * until the card reports it settled; a settle report for an event that has since been replaced by
 * a newer one for the same card (a tier upgrade landing mid-completion) does not remove the newer
 * one. Callers must re-key this composable per tab (`key(periodType)`) so that state — the
 * snackbar host, the active-celebration map — never leaks from one tab into another.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodScreen(
    periodType: PeriodType,
    uiState: PeriodUiState,
    onCreateClick: () -> Unit,
    onIncrement: (Long) -> Unit,
    onLongPress: (Long) -> Unit,
    undoRequests: Flow<UndoToken>,
    onUndo: (UndoToken) -> Unit,
    celebrations: Flow<CelebrationEvent>,
    onCelebrationSettled: (Long) -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val activeCelebrations = remember { mutableStateMapOf<Long, CelebrationEvent>() }

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
                activeCelebrations[event.achievementId] = event
            }
        }
    }

    val settle: (CelebrationEvent) -> Unit = { event ->
        if (activeCelebrations[event.achievementId] == event) {
            activeCelebrations.remove(event.achievementId)
        }
        onCelebrationSettled(event.achievementId)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = periodType.tabLabel(), style = MaterialTheme.typography.headlineSmall) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateClick) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add achievement")
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = bottomBar,
    ) { innerPadding ->
        when (uiState) {
            is PeriodUiState.Loading -> LoadingBody(innerPadding)
            is PeriodUiState.Error -> ErrorBody(innerPadding, uiState.message)
            is PeriodUiState.Content -> if (uiState.isEmpty) {
                EmptyBody(innerPadding, periodType)
            } else {
                PeriodList(
                    innerPadding = innerPadding,
                    content = uiState,
                    onIncrement = onIncrement,
                    onLongPress = onLongPress,
                    activeCelebrations = activeCelebrations,
                    onCelebrationSettled = settle,
                )
            }
        }
    }
}

/** The tab's title and its label in the tab switcher. */
fun PeriodType.tabLabel(): String = when (this) {
    PeriodType.DAILY -> "Daily"
    PeriodType.WEEKLY -> "Weekly"
    PeriodType.MONTHLY -> "Monthly"
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
private fun EmptyBody(innerPadding: PaddingValues, periodType: PeriodType) {
    Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No ${periodType.tabLabel().lowercase()} achievements yet",
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
private fun PeriodList(
    innerPadding: PaddingValues,
    content: PeriodUiState.Content,
    onIncrement: (Long) -> Unit,
    onLongPress: (Long) -> Unit,
    activeCelebrations: Map<Long, CelebrationEvent>,
    onCelebrationSettled: (CelebrationEvent) -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val placementSpec = MotionSpecs.resolvePlacement(MotionSpecs.cardSink, reducedMotion)

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
                celebration = activeCelebrations[achievement.id],
                onCelebrationSettled = onCelebrationSettled,
                modifier = Modifier.animateItem(placementSpec = placementSpec),
            )
        }

        if (content.done.isNotEmpty()) {
            item(key = "done-divider") {
                DoneSectionHeader()
            }
            // Done cards still accept a celebration: a completion whose hold-back ceiling expired
            // keeps playing here after the card has moved into this section.
            items(content.done, key = { it.id }) { achievement ->
                AchievementCard(
                    achievement = achievement,
                    onIncrement = { onIncrement(achievement.id) },
                    onLongPress = { onLongPress(achievement.id) },
                    celebration = activeCelebrations[achievement.id],
                    onCelebrationSettled = onCelebrationSettled,
                    modifier = Modifier.animateItem(placementSpec = placementSpec),
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
