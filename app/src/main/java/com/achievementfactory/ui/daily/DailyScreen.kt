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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.achievementfactory.ui.achievement.AchievementCard
import com.achievementfactory.ui.theme.Spacing

/**
 * The Daily tab (NAV-04, TRACK-02, D-05): renders live Room data as neutral cards, groups
 * completed items under a Done divider, and shows the two-line empty state when there is
 * nothing to show. [onCreateClick] navigates to the creation screen (wired in `AppNavHost`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyScreen(
    uiState: DailyUiState,
    onCreateClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = "Daily", style = MaterialTheme.typography.headlineSmall) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateClick) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add achievement")
            }
        },
    ) { innerPadding ->
        when (uiState) {
            is DailyUiState.Loading -> LoadingBody(innerPadding)
            is DailyUiState.Error -> ErrorBody(innerPadding, uiState.message)
            is DailyUiState.Content -> if (uiState.isEmpty) {
                EmptyBody(innerPadding)
            } else {
                DailyList(innerPadding, uiState)
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
private fun DailyList(innerPadding: PaddingValues, content: DailyUiState.Content) {
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
            AchievementCard(achievement = achievement)
        }

        if (content.done.isNotEmpty()) {
            item(key = "done-divider") {
                DoneSectionHeader()
            }
            items(content.done, key = { it.id }) { achievement ->
                AchievementCard(achievement = achievement)
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
