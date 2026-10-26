package com.achievementfactory.ui.period

import app.cash.turbine.test
import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.data.achievement.AchievementRepository
import com.achievementfactory.data.todo.TodoRepository
import com.achievementfactory.domain.model.IncrementOutcome
import com.achievementfactory.domain.model.TodoItem
import com.achievementfactory.domain.model.TrackedAchievement
import com.achievementfactory.domain.model.UndoToken
import com.achievementfactory.period.PeriodKey
import com.achievementfactory.period.PeriodType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * CELEB-03 at the ViewModel edge: the full-tab event fires on the action that clears the tab —
 * and only then — and, like every one-shot event, is never delivered to a collector that wasn't
 * there when it happened.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FullTabCelebrationEventTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun achievement(id: Long, isDone: Boolean) = TrackedAchievement(
        id = id,
        name = "A$id",
        icon = null,
        accent = AccentSwatch.SKY,
        target = 1,
        count = if (isDone) 1 else 0,
        isDone = isDone,
        currentStreak = 0,
        bestStreak = 0,
    )

    private val completingTap = IncrementOutcome(
        newCount = 1,
        isDone = true,
        justCompleted = true,
        newStreak = 1,
        undoToken = UndoToken(1L, PeriodKey("2026-08-12"), 1),
    )

    /** [afterWrite] is what the tab looks like once the completing write has landed. */
    private fun viewModel(
        afterWrite: List<TrackedAchievement>,
        todos: List<TodoItem> = emptyList(),
        outcome: IncrementOutcome = completingTap,
    ): Pair<PeriodViewModel, TodoRepository> {
        val repository = mockk<AchievementRepository>()
        every { repository.observePeriod(PeriodType.DAILY) } returns flowOf(afterWrite)
        coEvery { repository.increment(1L) } returns outcome
        val todoRepository = mockk<TodoRepository>()
        every { todoRepository.observeTodos(PeriodType.DAILY) } returns flowOf(todos)
        coEvery { todoRepository.setDone(any(), any()) } returns true
        return PeriodViewModel(repository, PeriodType.DAILY, todoRepository) to todoRepository
    }

    @Test
    fun completingTheLastAchievement_firesTheFullTabEventOnce() = runTest(testDispatcher) {
        val (viewModel, _) = viewModel(afterWrite = listOf(achievement(1, true), achievement(2, true)))

        viewModel.fullTabCelebrations.test {
            viewModel.onIncrement(1L)
            awaitItem()
            testDispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun anotherAchievementStillOutstanding_noFullTabEvent() = runTest(testDispatcher) {
        val (viewModel, _) = viewModel(afterWrite = listOf(achievement(1, true), achievement(2, false)))

        viewModel.fullTabCelebrations.test {
            viewModel.onIncrement(1L)
            testDispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun anUnfinishedTodo_keepsTheTabFromClearing() = runTest(testDispatcher) {
        val (viewModel, _) = viewModel(
            afterWrite = listOf(achievement(1, true)),
            todos = listOf(TodoItem(9L, "Call mum", PeriodType.DAILY, done = false)),
        )

        viewModel.fullTabCelebrations.test {
            viewModel.onIncrement(1L)
            testDispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun tickingTheLastTodo_clearsTheTab() = runTest(testDispatcher) {
        val (viewModel, _) = viewModel(
            afterWrite = listOf(achievement(1, true)),
            todos = listOf(TodoItem(9L, "Call mum", PeriodType.DAILY, done = true)),
        )

        viewModel.fullTabCelebrations.test {
            viewModel.onToggleTodo(9L, true)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun untickingATodo_neverCelebrates() = runTest(testDispatcher) {
        val (viewModel, _) = viewModel(afterWrite = listOf(achievement(1, true)))

        viewModel.fullTabCelebrations.test {
            viewModel.onToggleTodo(9L, false)
            testDispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun aTapThatDoesNotComplete_neverCelebrates_evenOnAnAlreadyClearTab() = runTest(testDispatcher) {
        val (viewModel, _) = viewModel(
            afterWrite = listOf(achievement(1, true)),
            outcome = completingTap.copy(newCount = 2, justCompleted = false),
        )

        viewModel.fullTabCelebrations.test {
            viewModel.onIncrement(1L)
            testDispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun eventEmittedWhileNothingIsSubscribed_isNotDeliveredToTheNextCollector() = runTest(testDispatcher) {
        val (viewModel, _) = viewModel(afterWrite = listOf(achievement(1, true)))

        viewModel.onIncrement(1L)
        advanceUntilIdle()

        viewModel.fullTabCelebrations.test {
            testDispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
    }
}
