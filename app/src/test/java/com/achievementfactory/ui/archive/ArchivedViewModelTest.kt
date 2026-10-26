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
import com.achievementfactory.domain.model.ExpiredTodo
import com.achievementfactory.data.todo.TodoRepository

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

    @Test
    fun expiredTodos_joinTheArchive_andTheirActionsReachTheTodoRepository() = runTest(testDispatcher) {
        val repository = mockk<AchievementRepository>()
        every { repository.observeArchived() } returns flowOf(listOf(item))
        val todos = mockk<TodoRepository>()
        val expired = ExpiredTodo(5L, "Call mum", PeriodType.DAILY, "2026-08-11")
        every { todos.observeExpired() } returns flowOf(listOf(expired))
        coEvery { todos.restore(5L, PeriodType.WEEKLY) } returns true
        coEvery { todos.delete(5L) } returns true

        val viewModel = ArchivedViewModel(repository, todos)

        viewModel.uiState.test {
            assertEquals(ArchivedUiState.Loading, awaitItem())
            assertEquals(ArchivedUiState.Content(listOf(item), listOf(expired)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.onRestoreTodo(5L, PeriodType.WEEKLY)
        viewModel.onDeleteTodo(5L)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { todos.restore(5L, PeriodType.WEEKLY) }
        coVerify(exactly = 1) { todos.delete(5L) }
    }

    @Test
    fun expiredPeriodLabel_describesEachKindOfPeriod_andFallsBackOnGarbage() {
        assertEquals(true, expiredPeriodLabel(ExpiredTodo(1L, "x", PeriodType.WEEKLY, "2026-08-10")).startsWith("Due week of"))
        assertEquals(true, expiredPeriodLabel(ExpiredTodo(1L, "x", PeriodType.MONTHLY, "2026-08")).contains("2026"))
        assertEquals("Due nonsense", expiredPeriodLabel(ExpiredTodo(1L, "x", PeriodType.DAILY, "nonsense")))
    }
}
