package com.achievementfactory.ui.detail

import app.cash.turbine.test
import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.data.achievement.AchievementRepository
import com.achievementfactory.domain.model.AchievementDetail
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * A plain JVM test (no Robolectric needed) — [AchievementRepository] is mocked with MockK,
 * continuing [com.achievementfactory.ui.period.PeriodViewModelTest]'s convention.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AchievementDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun detail(id: Long = 1L, streak: Int = 0) = AchievementDetail(
        id = id,
        name = "Read",
        icon = null,
        accent = AccentSwatch.SKY,
        target = 1,
        periodCount = 0,
        isDone = false,
        currentStreak = streak,
        bestStreak = streak,
    )

    @Test
    fun uiState_emitsLoadingFirst() = runTest(testDispatcher) {
        val repository = mockk<AchievementRepository>()
        every { repository.observeDetail(1L) } returns flow { /* never emits — Loading persists */ }

        val viewModel = AchievementDetailViewModel(repository, achievementId = 1L)

        viewModel.uiState.test {
            assertEquals(AchievementDetailUiState.Loading, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_nullFromRepository_mapsToNotFound() = runTest(testDispatcher) {
        val repository = mockk<AchievementRepository>()
        every { repository.observeDetail(99L) } returns flowOf(null)

        val viewModel = AchievementDetailViewModel(repository, achievementId = 99L)

        viewModel.uiState.test {
            assertEquals(AchievementDetailUiState.Loading, awaitItem())
            assertEquals(AchievementDetailUiState.NotFound, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_realDetailFromRepository_mapsToContent() = runTest(testDispatcher) {
        val repository = mockk<AchievementRepository>()
        val expected = detail(streak = 2)
        every { repository.observeDetail(1L) } returns flowOf(expected)

        val viewModel = AchievementDetailViewModel(repository, achievementId = 1L)

        viewModel.uiState.test {
            assertEquals(AchievementDetailUiState.Loading, awaitItem())
            val content = awaitItem() as AchievementDetailUiState.Content
            assertEquals(expected, content.detail)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_reEmitsContent_whenUpstreamStreakChanges() = runTest(testDispatcher) {
        val repository = mockk<AchievementRepository>()
        val stream = MutableStateFlow<AchievementDetail?>(detail(streak = 1))
        every { repository.observeDetail(1L) } returns stream

        val viewModel = AchievementDetailViewModel(repository, achievementId = 1L)

        viewModel.uiState.test {
            assertEquals(AchievementDetailUiState.Loading, awaitItem())
            val first = awaitItem() as AchievementDetailUiState.Content
            assertEquals(1, first.detail.currentStreak)

            stream.value = detail(streak = 2)

            val second = awaitItem() as AchievementDetailUiState.Content
            assertEquals(2, second.detail.currentStreak)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
