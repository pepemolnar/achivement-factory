package com.achievementfactory.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.achievementfactory.ui.create.CreateAchievementScreen
import com.achievementfactory.ui.create.CreateAchievementViewModel
import com.achievementfactory.ui.daily.DailyScreen
import com.achievementfactory.ui.daily.DailyViewModel
import com.achievementfactory.ui.detail.AchievementDetailScreen
import com.achievementfactory.ui.detail.AchievementDetailViewModel

/** Route constants — [DAILY], [CREATE] and the read-only [DETAIL] destination (D-03). */
object Routes {
    const val DAILY = "daily"
    const val CREATE = "create"
    const val DETAIL = "detail/{achievementId}"

    fun detailOf(id: Long): String = "detail/$id"
}

/** The app's single [NavHost], hosting the Daily tab, creation-screen and detail destinations. */
@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.DAILY) {
        composable(Routes.DAILY) {
            val viewModel: DailyViewModel = viewModel(factory = DailyViewModel.Factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            DailyScreen(
                uiState = uiState,
                onCreateClick = { navController.navigate(Routes.CREATE) },
                onIncrement = viewModel::onIncrement,
                // Long-press opens the detail screen (D-03) — reserved exclusively for this,
                // never an undo gesture and never a quick-set stepper.
                onLongPress = { id -> navController.navigate(Routes.detailOf(id)) },
                undoRequests = viewModel.undoRequests,
                onUndo = viewModel::onUndo,
                celebrations = viewModel.celebrations,
                onCelebrationSettled = viewModel::onCelebrationSettled,
            )
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
        composable(Routes.CREATE) {
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
                onTargetChange = viewModel::onTargetChange,
                onToggleAdvanced = viewModel::onToggleAdvanced,
                onSave = viewModel::onSave,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
