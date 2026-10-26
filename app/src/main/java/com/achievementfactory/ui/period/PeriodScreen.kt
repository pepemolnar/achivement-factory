package com.achievementfactory.ui.period

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.achievementfactory.domain.model.TodoItem
import com.achievementfactory.domain.model.UndoToken
import com.achievementfactory.period.PeriodType
import com.achievementfactory.ui.achievement.AchievementCard
import com.achievementfactory.ui.celebration.CelebrationEvent
import com.achievementfactory.ui.todo.TodoCard
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
    onReorder: (List<Long>) -> Unit = {},
    onOpenArchived: () -> Unit = {},
    onToggleTodo: (Long, Boolean) -> Unit = { _, _ -> },
    onDeleteTodo: (Long) -> Unit = {},
) {
    // A delete confirmation is momentary UI, never persisted or replayed.
    var pendingTodoDelete by remember { mutableStateOf<TodoItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    // Reorder mode (ACH-09): entered from the top-bar menu, left with Done or Back. Saveable so a
    // rotation mid-reorder doesn't drop the user out of it.
    var reorderMode by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = reorderMode) { reorderMode = false }
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
            TopAppBar(
                title = {
                    Text(
                        text = if (reorderMode) "Reorder" else periodType.tabLabel(),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                actions = {
                    if (reorderMode) {
                        TextButton(onClick = { reorderMode = false }) { Text("Done") }
                    } else {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            val canReorder = (uiState as? PeriodUiState.Content)?.all?.size?.let { it > 1 } == true
                            DropdownMenuItem(
                                text = { Text("Reorder") },
                                enabled = canReorder,
                                onClick = {
                                    menuOpen = false
                                    reorderMode = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Archived") },
                                onClick = {
                                    menuOpen = false
                                    onOpenArchived()
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!reorderMode) {
                FloatingActionButton(onClick = onCreateClick) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Add achievement")
                }
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
            } else if (reorderMode) {
                ReorderList(innerPadding = innerPadding, achievements = uiState.all, onReorder = onReorder)
            } else {
                PeriodList(
                    innerPadding = innerPadding,
                    content = uiState,
                    onIncrement = onIncrement,
                    onLongPress = onLongPress,
                    activeCelebrations = activeCelebrations,
                    onCelebrationSettled = settle,
                    onToggleTodo = onToggleTodo,
                    onTodoLongPress = { pendingTodoDelete = it },
                )
            }
        }
    }

    pendingTodoDelete?.let { todo ->
        AlertDialog(
            onDismissRequest = { pendingTodoDelete = null },
            title = { Text("Delete \u201C${todo.title}\u201D?") },
            text = { Text("This todo will be removed for good.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTodo(todo.id)
                        pendingTodoDelete = null
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingTodoDelete = null }) { Text("Cancel") } },
        )
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
                text = "Nothing here yet",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Tap + to add an achievement or a todo.",
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
    onToggleTodo: (Long, Boolean) -> Unit,
    onTodoLongPress: (TodoItem) -> Unit,
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

        // Todos follow the achievements in one list (NAV-02). String keys keep them from ever
        // colliding with an achievement's Long id.
        items(content.outstandingTodos, key = { "todo-${it.id}" }) { todo ->
            TodoCard(
                todo = todo,
                onToggle = { onToggleTodo(todo.id, true) },
                onLongPress = { onTodoLongPress(todo) },
                modifier = Modifier.animateItem(placementSpec = placementSpec),
            )
        }

        if (content.hasDoneSection) {
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
            items(content.doneTodos, key = { "todo-${it.id}" }) { todo ->
                TodoCard(
                    todo = todo,
                    onToggle = { onToggleTodo(todo.id, false) },
                    onLongPress = { onTodoLongPress(todo) },
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
