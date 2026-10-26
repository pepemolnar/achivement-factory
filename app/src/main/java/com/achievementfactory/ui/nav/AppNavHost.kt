package com.achievementfactory.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.achievementfactory.period.PeriodType
import com.achievementfactory.ui.create.CreateAchievementScreen
import com.achievementfactory.ui.create.CreateAchievementViewModel
import com.achievementfactory.ui.detail.AchievementDetailScreen
import com.achievementfactory.ui.detail.AchievementDetailViewModel
import com.achievementfactory.ui.period.PeriodScreen
import com.achievementfactory.ui.period.PeriodViewModel
import com.achievementfactory.ui.period.tabLabel

/**
 * Route constants — [DAILY] (the tab container: Daily, Weekly and Monthly are lateral state
 * inside it, not routes of their own), [CREATE] and the read-only [DETAIL] destination (D-03).
 */
object Routes {
    const val DAILY = "daily"
    const val PERIOD_TYPE_ARG = "periodType"
    const val CREATE = "create?$PERIOD_TYPE_ARG={$PERIOD_TYPE_ARG}"
    const val DETAIL = "detail/{achievementId}"

    fun createFor(periodType: PeriodType): String = "create?$PERIOD_TYPE_ARG=${periodType.name}"

    fun detailOf(id: Long): String = "detail/$id"
}

/**
 * Navigates only while the destination the click came from is still RESUMED — the standard
 * Navigation-Compose debounce for click-driven navigation.
 *
 * A click callback fires immediately, but the back stack does not change until the navigation is
 * actually applied. Two taps landing inside that gap — a double-tap on the FAB, or long-pressing
 * two cards in quick succession — therefore each see the *same* current destination and both
 * navigate, pushing two identical entries. The user then has to press Back twice to get out of
 * one screen, and two independent ViewModels exist for it. The first `navigate` moves the current
 * entry out of RESUMED, so this check rejects the second.
 */
private fun NavHostController.navigateOnce(route: String) {
    val currentState = currentBackStackEntry?.lifecycle?.currentState ?: return
    if (currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        navigate(route)
    }
}

/** The app's single [NavHost], hosting the period tabs, creation-screen and detail destinations. */
@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.DAILY) {
        composable(Routes.DAILY) {
            // Saved, so the selected tab survives rotation, process death and a round trip
            // through the create/detail screens.
            var selectedTab by rememberSaveable { mutableStateOf(PeriodType.DAILY) }

            // One ViewModel per tab, keyed by period type, all scoped to this back-stack entry —
            // each tab keeps its own held-back set and its own one-shot event streams. `key(...)`
            // re-keys the screen's local state (snackbar host, active celebrations) per tab too.
            key(selectedTab) {
                val viewModel: PeriodViewModel = viewModel(
                    key = "period-${selectedTab.name}",
                    factory = PeriodViewModel.factoryFor(selectedTab),
                )
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                PeriodScreen(
                    periodType = selectedTab,
                    uiState = uiState,
                    onCreateClick = { navController.navigateOnce(Routes.createFor(selectedTab)) },
                    onIncrement = viewModel::onIncrement,
                    // Long-press opens the detail screen (D-03) — reserved exclusively for this,
                    // never an undo gesture and never a quick-set stepper.
                    onLongPress = { id -> navController.navigateOnce(Routes.detailOf(id)) },
                    undoRequests = viewModel.undoRequests,
                    onUndo = viewModel::onUndo,
                    celebrations = viewModel.celebrations,
                    onCelebrationSettled = viewModel::onCelebrationSettled,
                    bottomBar = {
                        PeriodTabBar(selected = selectedTab, onSelect = { selectedTab = it })
                    },
                )
            }
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("achievementId") { type = NavType.LongType }),
        ) {
            val viewModel: AchievementDetailViewModel = viewModel(factory = AchievementDetailViewModel.Factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            AchievementDetailScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.CREATE,
            arguments = listOf(
                navArgument(Routes.PERIOD_TYPE_ARG) {
                    type = NavType.StringType
                    defaultValue = PeriodType.DAILY.name
                },
            ),
        ) {
            val viewModel: CreateAchievementViewModel = viewModel(factory = CreateAchievementViewModel.Factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(uiState.saved) {
                if (uiState.saved) {
                    navController.popBackStack()
                }
            }

            CreateAchievementScreen(
                uiState = uiState,
                onNameChange = viewModel::onNameChange,
                onIconChange = viewModel::onIconChange,
                onAccentChange = viewModel::onAccentChange,
                onPeriodTypeChange = viewModel::onPeriodTypeChange,
                onTargetChange = viewModel::onTargetChange,
                onToggleAdvanced = viewModel::onToggleAdvanced,
                onTiersEnabledChange = viewModel::onTiersEnabledChange,
                onBronzeChange = viewModel::onBronzeChange,
                onSilverChange = viewModel::onSilverChange,
                onGoldChange = viewModel::onGoldChange,
                onSave = viewModel::onSave,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/** The three-tab switcher (NAV-01) — Daily, Weekly, Monthly, in that fixed order. */
@Composable
private fun PeriodTabBar(selected: PeriodType, onSelect: (PeriodType) -> Unit) {
    NavigationBar {
        PeriodType.entries.forEach { type ->
            NavigationBarItem(
                selected = type == selected,
                onClick = { onSelect(type) },
                icon = { Icon(imageVector = type.tabIcon(), contentDescription = null) },
                label = { Text(type.tabLabel()) },
            )
        }
    }
}

private fun PeriodType.tabIcon(): ImageVector = when (this) {
    PeriodType.DAILY -> Icons.Filled.Today
    PeriodType.WEEKLY -> Icons.Filled.DateRange
    PeriodType.MONTHLY -> Icons.Filled.CalendarMonth
}
