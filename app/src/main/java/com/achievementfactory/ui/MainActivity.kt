package com.achievementfactory.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.achievementfactory.ui.nav.AppNavHost
import com.achievementfactory.ui.theme.AchievementFactoryTheme

/**
 * The app's single Activity, hosting the single [AppNavHost]. The Daily tab (the only
 * destination in this plan) renders through [com.achievementfactory.ui.daily.DailyScreen].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AchievementFactoryTheme {
                AppNavHost(navController = rememberNavController())
            }
        }
    }
}
