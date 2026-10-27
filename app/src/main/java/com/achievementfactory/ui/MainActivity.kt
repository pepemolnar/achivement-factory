package com.achievementfactory.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.achievementfactory.ui.nav.AppNavHost
import com.achievementfactory.ui.theme.AchievementFactoryTheme

/**
 * The app's single Activity, hosting the single [AppNavHost]. Its start destination is the
 * three-tab container (Daily, Weekly, Monthly), each tab rendered through
 * [com.achievementfactory.ui.period.PeriodScreen].
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
