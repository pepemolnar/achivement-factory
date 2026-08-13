package com.achievementfactory.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.achievementfactory.ui.daily.DailyScreen
import com.achievementfactory.ui.daily.DailyViewModel

/**
 * Route constants. Only [DAILY] is reachable in this plan; the create and detail routes are
 * added by plan 01-03.
 */
object Routes {
    const val DAILY = "daily"
}

/** The app's single [NavHost], hosting the Daily tab destination. */
@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.DAILY) {
        composable(Routes.DAILY) {
            val viewModel: DailyViewModel = viewModel(factory = DailyViewModel.Factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            DailyScreen(uiState = uiState, onCreateClick = {})
        }
    }
}
