package com.achievementfactory.ui.period

import app.cash.turbine.test
import com.achievementfactory.data.achievement.AchievementRepository
import com.achievementfactory.data.todo.TodoRepository
import com.achievementfactory.domain.model.TodoItem
import com.achievementfactory.period.PeriodType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/** NAV-02 and TODO-03/TODO-06 at the tab ViewModel. */
@OptIn(ExperimentalCoroutinesApi::class)
class TodoTabViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val open = TodoItem(1L, "Call mum", PeriodType.DAILY, done = false)
    private val ticked = TodoItem(2L, "Buy milk", PeriodType.DAILY, done = true)

    private fun viewModel(todoRepository: TodoRepository): PeriodViewModel {
        val achievements = mockk<AchievementRepository>()
        every { achievements.observePeriod(PeriodType.DAILY) } returns flowOf(emptyList())
        return PeriodViewModel(achievements, PeriodType.DAILY, todoRepository)
    }

    @Test
    fun todos_areSplitIntoOutstandingAndDone_andTheTabIsNotEmpty() = runTest(testDispatcher) {
        val todos = mockk<TodoRepository>()
        every { todos.observeTodos(PeriodType.DAILY) } returns flowOf(listOf(open, ticked))

        val viewModel = viewModel(todos)

        viewModel.uiState.test {
            assertEquals(PeriodUiState.Loading, awaitItem())
            val content = awaitItem() as PeriodUiState.Content
            assertEquals(listOf(open), content.outstandingTodos)
            assertEquals(listOf(ticked), content.doneTodos)
            assertFalse(content.isEmpty)
            assertEquals(true, content.hasDoneSection)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun toggleAndDelete_goToTheTodoRepository_andFailuresDoNotCrash() = runTest(testDispatcher) {
        val todos = mockk<TodoRepository>()
        every { todos.observeTodos(PeriodType.DAILY) } returns flowOf(emptyList())
        coEvery { todos.setDone(1L, true) } returns true
        coEvery { todos.delete(2L) } throws RuntimeException("SQLiteFullException")

        val viewModel = viewModel(todos)
        viewModel.onToggleTodo(1L, true)
        viewModel.onDeleteTodo(2L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { todos.setDone(1L, true) }
        coVerify(exactly = 1) { todos.delete(2L) }
    }
}
