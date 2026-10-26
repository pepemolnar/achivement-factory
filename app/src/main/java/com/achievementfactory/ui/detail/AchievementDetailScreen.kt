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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.achievementfactory.domain.model.AchievementDetail
import com.achievementfactory.domain.tier.TierLadder
import com.achievementfactory.period.PeriodType
import com.achievementfactory.ui.achievement.AchievementGlyph
import com.achievementfactory.ui.achievement.ProgressRing
import com.achievementfactory.ui.achievement.TierBadge
import com.achievementfactory.ui.theme.Spacing
import com.achievementfactory.ui.theme.color

/**
 * The read-only detail screen (TRACK-08, D-03, D-13): identity, the current period's progress
 * (today, this week or this month) and tier reached, the tier ladder if one is configured, current
 * streak and — for the first time anywhere in the app — best streak. Reached by long-pressing a
 * card. Carries no input field and no button beyond its own back arrow in this phase; a later
 * phase is expected to layer controls onto this same file without a navigation change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementDetailScreen(
    uiState: AchievementDetailUiState,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit = {},
    onArchive: () -> Unit = {},
    closeRequests: Flow<Unit> = emptyFlow(),
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(closeRequests, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            closeRequests.collect { onBack() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Edit and archive live here, one long-press away from the card, so a tap on
                    // a card always stays +1 (Phase 3 decision). Archiving is reversible from the
                    // Archived screen, so it needs no confirmation.
                    if (uiState is AchievementDetailUiState.Content) {
                        IconButton(onClick = { onEdit(uiState.detail.id) }) {
                            Icon(imageVector = Icons.Filled.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = onArchive) {
                            Icon(imageVector = Icons.Filled.Archive, contentDescription = "Archive")
                        }
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
        detail.periodCount.toFloat() / detail.target.toFloat()
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
                progress = if (detail.isDone) 1f else progress.coerceIn(0f, 1f),
                accent = accentColor,
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
        val periodPhrase = detail.periodType.currentPeriodPhrase()
        val progressText = when {
            detail.tiers != null && detail.tiers.gold > 1 -> "${detail.periodCount} $periodPhrase"
            detail.target > 1 -> "${detail.periodCount}/${detail.target} $periodPhrase"
            detail.isDone -> "Done $periodPhrase"
            else -> "Not done yet $periodPhrase"
        }
        Text(
            text = progressText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )

        detail.tierReached?.let { tier ->
            TierBadge(tier = tier, modifier = Modifier.padding(top = Spacing.sm))
        }

        detail.tiers?.let { ladder -> TierLadderSummary(ladder) }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StreakStat(
                label = "Current streak (${detail.periodType.streakUnit()})",
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

/** The configured thresholds, lowest first — e.g. "Silver 3 · Gold 5" (TIER-02). */
@Composable
private fun TierLadderSummary(ladder: TierLadder) {
    Text(
        text = ladder.tiers.joinToString(separator = " · ") { tier -> "${tier.displayName} ${ladder.thresholdOf(tier)}" },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.xs),
    )
}

private fun PeriodType.currentPeriodPhrase(): String = when (this) {
    PeriodType.DAILY -> "today"
    PeriodType.WEEKLY -> "this week"
    PeriodType.MONTHLY -> "this month"
}

private fun PeriodType.streakUnit(): String = when (this) {
    PeriodType.DAILY -> "days"
    PeriodType.WEEKLY -> "weeks"
    PeriodType.MONTHLY -> "months"
}
