package com.achievementfactory.ui.daily

import app.cash.turbine.test
import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.data.achievement.AchievementRepository
import com.achievementfactory.domain.model.DailyAchievement
import com.achievementfactory.domain.model.IncrementOutcome
import com.achievementfactory.domain.model.UndoToken
import com.achievementfactory.period.PeriodKey
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A plain JVM test (no Robolectric needed) — [AchievementRepository] is mocked with MockK, which
 * mocks final Kotlin classes with no extra Gradle config (CLAUDE.md "What NOT to Use").
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DailyViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun achievement(id: Long, isDone: Boolean) = DailyAchievement(
        id = id,
        name = "Achievement $id",
        icon = null,
        accent = AccentSwatch.SKY,
        target = 1,
        count = if (isDone) 1 else 0,
        isDone = isDone,
        currentStreak = 0,
        bestStreak = 0,
    )

    @Test
    fun uiState_emitsLoadingFirst() = runTest(testDispatcher) {
        val repository = mockk<AchievementRepository>()
        every { repository.observeDaily() } returns flow { /* never emits — Loading persists */ }

        val viewModel = DailyViewModel(repository)

        viewModel.uiState.test {
            assertEquals(DailyUiState.Loading, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_splitsAchievementsIntoDoneAndOutstanding() = runTest(testDispatcher) {
        val outstandingAchievement = achievement(id = 1, isDone = false)
        val doneAchievement = achievement(id = 2, isDone = true)
        val repository = mockk<AchievementRepository>()
        every { repository.observeDaily() } returns flowOf(listOf(outstandingAchievement, doneAchievement))

        val viewModel = DailyViewModel(repository)

        viewModel.uiState.test {
            assertEquals(DailyUiState.Loading, awaitItem())
            val content = awaitItem() as DailyUiState.Content
            assertEquals(listOf(outstandingAchievement), content.outstanding)
            assertEquals(listOf(doneAchievement), content.done)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_mapsUpstreamFailureToError() = runTest(testDispatcher) {
        val repository = mockk<AchievementRepository>()
        every { repository.observeDaily() } returns flow { throw RuntimeException("boom") }

        val viewModel = DailyViewModel(repository)

        viewModel.uiState.test {
            assertEquals(DailyUiState.Loading, awaitItem())
            val error = awaitItem() as DailyUiState.Error
            assertEquals(
                "Couldn't load your achievements. Restart the app to try again.",
                error.message,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onIncrement_emitsExactlyOneUndoTokenPerCall() = runTest(testDispatcher) {
        val repository = mockk<AchievementRepository>()
        every { repository.observeDaily() } returns flowOf(emptyList())
        val expectedToken = UndoToken(achievementId = 1L, periodKey = PeriodKey("2026-08-13"), expectedCount = 1)
        coEvery { repository.increment(1L) } returns IncrementOutcome(
            newCount = 1,
            isDone = true,
            justCompleted = true,
            undoToken = expectedToken,
        )

        val viewModel = DailyViewModel(repository)

        viewModel.undoRequests.test {
            viewModel.onIncrement(1L)
            assertEquals(expectedToken, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun contentPartition_achievementWhoseIdIsHeldBack_staysOutstandingDespiteBeingDone() {
        val heldBackDone = achievement(id = 1, isDone = true)
        val ordinaryDone = achievement(id = 2, isDone = true)

        val content = DailyUiState.Content.partition(
            achievements = listOf(heldBackDone, ordinaryDone),
            heldBackIds = setOf(1L),
        )

        assertEquals(listOf(heldBackDone), content.outstanding)
        assertEquals(listOf(ordinaryDone), content.done)
        assertTrue(content.heldBackIds.contains(1L))
    }
}
