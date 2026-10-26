package com.achievementfactory.ui.archive

import app.cash.turbine.test
import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.data.achievement.AchievementRepository
import com.achievementfactory.domain.model.ArchivedAchievement
import com.achievementfactory.period.PeriodType
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArchivedViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val item = ArchivedAchievement(1L, "Read", null, AccentSwatch.SKY, PeriodType.DAILY, bestStreak = 4)

    @Test
    fun uiState_listsTheArchivedAchievements() = runTest(testDispatcher) {
        val repository = mockk<AchievementRepository>()
        every { repository.observeArchived() } returns flowOf(listOf(item))

        val viewModel = ArchivedViewModel(repository)

        viewModel.uiState.test {
            assertEquals(ArchivedUiState.Loading, awaitItem())
            assertEquals(ArchivedUiState.Content(listOf(item)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_mapsAFailureToError() = runTest(testDispatcher) {
        val repository = mockk<AchievementRepository>()
        every { repository.observeArchived() } returns flow { throw RuntimeException("boom") }

        val viewModel = ArchivedViewModel(repository)

        viewModel.uiState.test {
            assertEquals(ArchivedUiState.Loading, awaitItem())
            assertEquals(true, awaitItem() is ArchivedUiState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun restoreAndDelete_goToTheRepository_andFailuresDoNotCrash() = runTest(testDispatcher) {
        val repository = mockk<AchievementRepository>()
        every { repository.observeArchived() } returns flowOf(emptyList())
        coEvery { repository.restore(1L) } returns true
        coEvery { repository.deletePermanently(2L) } throws RuntimeException("SQLiteFullException")

        val viewModel = ArchivedViewModel(repository)
        viewModel.onRestore(1L)
        viewModel.onDeletePermanently(2L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.restore(1L) }
        coVerify(exactly = 1) { repository.deletePermanently(2L) }
    }
}
