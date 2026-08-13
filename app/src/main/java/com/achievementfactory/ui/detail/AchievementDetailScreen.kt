package com.achievementfactory.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.achievementfactory.domain.model.AchievementDetail
import com.achievementfactory.ui.achievement.AchievementGlyph
import com.achievementfactory.ui.achievement.ProgressRing
import com.achievementfactory.ui.theme.Spacing
import com.achievementfactory.ui.theme.color

/**
 * The read-only detail screen (TRACK-08, D-03, D-13): identity, today's progress, current
 * streak and — for the first time anywhere in the app — best streak. Reached by long-pressing a
 * card. Carries no input field and no button beyond its own back arrow in this phase; a later
 * phase is expected to layer controls onto this same file without a navigation change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementDetailScreen(
    uiState: AchievementDetailUiState,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        when (uiState) {
            is AchievementDetailUiState.Loading -> LoadingBody(innerPadding)
            is AchievementDetailUiState.NotFound -> NotFoundBody(innerPadding)
            is AchievementDetailUiState.Content -> DetailBody(innerPadding, uiState.detail)
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
private fun NotFoundBody(innerPadding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "This achievement is no longer here.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DetailBody(innerPadding: PaddingValues, detail: AchievementDetail) {
    val accentColor = detail.accent.color()
    val progress = if (detail.target > 0) {
        detail.todayCount.toFloat() / detail.target.toFloat()
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(top = Spacing.xxxl, start = Spacing.lg, end = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            ProgressRing(
                progress = progress.coerceIn(0f, 1f),
                accent = accentColor,
                closed = detail.isDone,
                modifier = Modifier.size(Spacing.iconClusterSize + Spacing.xs * 2),
            )
            if (detail.isDone) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Completed",
                    tint = accentColor,
                    modifier = Modifier.size(Spacing.iconClusterSize),
                )
            } else {
                AchievementGlyph(icon = detail.icon, name = detail.name, accent = accentColor)
            }
        }

        Text(
            text = detail.name,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = Spacing.md),
        )

        // Target-1 achievements read as a boolean tick, matching the card's own rule — no
        // fraction is shown for them here either.
        val progressText = when {
            detail.target > 1 -> "${detail.todayCount}/${detail.target}"
            detail.isDone -> "Done today"
            else -> "Not done yet"
        }
        Text(
            text = progressText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StreakStat(
                label = "Current streak",
                value = detail.currentStreak.toString(),
                valueStyle = MaterialTheme.typography.headlineSmall,
            )
            StreakStat(
                label = "Best streak",
                value = detail.bestStreak.toString(),
                valueStyle = MaterialTheme.typography.displaySmall,
            )
        }
    }
}

/**
 * One streak figure: its caption in the Label role above the number itself. [valueStyle] is the
 * only difference between the current-streak and best-streak instances — best streak is the one
 * number in this phase rendered in the Display role (28sp).
 */
@Composable
fun StreakStat(label: String, value: String, valueStyle: TextStyle) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = valueStyle)
    }
}
