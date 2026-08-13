package com.achievementfactory.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.achievementfactory.ui.theme.AchievementFactoryTheme

/**
 * The app's single Activity, hosting the single NavHost (added in plan 01-03).
 * For this plan, renders a static Scaffold with a "Daily" top bar and an empty body —
 * plan 01-02 replaces the body with the real Daily tab screen.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AchievementFactoryTheme {
                DailyScaffold()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyScaffold() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Daily") }) },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding))
    }
}
