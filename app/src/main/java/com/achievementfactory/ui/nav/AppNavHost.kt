package com.achievementfactory.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.achievementfactory.ui.create.CreateAchievementScreen
import com.achievementfactory.ui.create.CreateAchievementViewModel
import com.achievementfactory.ui.daily.DailyScreen
import com.achievementfactory.ui.daily.DailyViewModel

/**
 * Route constants. [DAILY] and [CREATE] are reachable; the read-only detail route is added by a
 * later plan.
 */
object Routes {
    const val DAILY = "daily"
    const val CREATE = "create"
}

/** The app's single [NavHost], hosting the Daily tab and the creation-screen destinations. */
@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.DAILY) {
        composable(Routes.DAILY) {
            val viewModel: DailyViewModel = viewModel(factory = DailyViewModel.Factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            DailyScreen(uiState = uiState, onCreateClick = { navController.navigate(Routes.CREATE) })
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
