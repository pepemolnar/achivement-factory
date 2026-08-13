package com.achievementfactory.ui.daily

import app.cash.turbine.test
import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.data.achievement.AchievementRepository
import com.achievementfactory.domain.model.DailyAchievement
import com.achievementfactory.domain.model.IncrementOutcome
import com.achievementfactory.domain.model.UndoToken
import com.achievementfactory.period.PeriodKey
import com.achievementfactory.ui.celebration.CelebrationEvent
import com.achievementfactory.ui.theme.MotionSpecs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Proves CELEB-04 end to end against [DailyViewModel]'s real `celebrations` channel and
 * `heldBackIds` seam, with [AchievementRepository] mocked via MockK (final class, same
 * convention as [DailyViewModelTest]). Every case in plan 01-07 Task 1's `<behavior>` list has a
 * dedicated test here — most importantly the late-collector case, which is the entire mechanism
 * behind "never replays on rotation/recomposition/restart."
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CelebrationEventTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun achievement(id: Long, isDone: Boolean, currentStreak: Int = 0) = DailyAchievement(
        id = id,
        name = "Achievement $id",
        icon = null,
        accent = AccentSwatch.SKY,
        target = 1,
        count = if (isDone) 1 else 0,
        isDone = isDone,
        currentStreak = currentStreak,
        bestStreak = currentStreak,
    )

    private fun outcome(
        newCount: Int,
        isDone: Boolean,
        justCompleted: Boolean,
        newStreak: Int,
        expectedCount: Int = newCount,
    ) = IncrementOutcome(
        newCount = newCount,
        isDone = isDone,
        justCompleted = justCompleted,
        newStreak = newStreak,
        undoToken = UndoToken(achievementId = 1L, periodKey = PeriodKey("2026-08-13"), expectedCount = expectedCount),
    )

    @Test
    fun targetOneAchievement_completingTap_emitsExactlyOneEvent() = runTest(testDispatcher) {
        val repository = mockk<AchievementRepository>()
        every { repository.observeDaily() } returns flowOf(emptyList())
        coEvery { repository.increment(1L) } returns
            outcome(newCount = 1, isDone = true, justCompleted = true, newStreak = 1)

        val viewModel = DailyViewModel(repository)

        viewModel.celebrations.test {
            viewModel.onIncrement(1L)
            val event = awaitItem() as CelebrationEvent.Completed
            assertEquals(1L, event.achievementId)
            assertEquals(1, event.newStreak)
            assertTrue(event.isFirstFlame)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun targetThreeAchievement_onlyTheThirdCompletingTapEmitsAnEvent() = runTest(testDispatcher) {
        val repository = mockk<AchievementRepository>()
        every { repository.observeDaily() } returns flowOf(emptyList())
        coEvery { repository.increment(1L) } returnsMany listOf(
            outcome(newCount = 1, isDone = false, justCompleted = false, newStreak = 0),
            outcome(newCount = 2, isDone = false, justCompleted = false, newStreak = 0),
            outcome(newCount = 3, isDone = true, justCompleted = true, newStreak = 1),
        )

        val viewModel = DailyViewModel(repository)

        viewModel.celebrations.test {
            viewModel.onIncrement(1L)
            viewModel.onIncrement(1L)
            viewModel.onIncrement(1L)
            val event = awaitItem() as CelebrationEvent.Completed
            assertEquals(1, event.newStreak)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun tapPastTheTarget_emitsNoEvent() = runTest(testDispatcher) {
        val repository = mockk<AchievementRepository>()
        every { repository.observeDaily() } returns flowOf(emptyList())
        coEvery { repository.increment(1L) } returns
            outcome(newCount = 4, isDone = true, justCompleted = false, newStreak = 1)

        val viewModel = DailyViewModel(repository)

        viewModel.celebrations.test {
            viewModel.onIncrement(1L)
            // Let the write coroutine actually run before asserting nothing came out of it —
            // otherwise this passes simply because the increment had not been dispatched yet.
            testDispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun lateCollector_afterAnEventWasAlreadyDelivered_receivesNothing() = runTest(testDispatcher) {
        val repository = mockk<AchievementRepository>()
        every { repository.observeDaily() } returns flowOf(emptyList())
        coEvery { repository.increment(1L) } returns
            outcome(newCount = 1, isDone = true, justCompleted = true, newStreak = 1)

        val viewModel = DailyViewModel(repository)

        viewModel.celebrations.test {
            viewModel.onIncrement(1L)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        // No replay buffer — a collector that subscribes after the event was already delivered
        // gets nothing, not the last value.
        viewModel.celebrations.test {
            testDispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
    }

    /**
     * The case [lateCollector_afterAnEventWasAlreadyDelivered_receivesNothing] cannot reach, and
     * the one CELEB-04 is actually about. That test awaits the item *before* re-subscribing, so it
     * only ever exercises an already-drained stream and passes on any implementation.
     *
     * Here nothing is subscribed at emission time — exactly the window `DailyScreen` opens by
     * collecting under `repeatOnLifecycle(STARTED)`, since the DB write and the emit happen
     * asynchronously in `viewModelScope`, which outlives an `Activity` recreation. A
     * `Channel(Channel.BUFFERED)` retains the undelivered event here and hands it to the next
     * collector, replaying the whole celebration on an already-completed card after rotation; a
     * zero-replay `SharedFlow` drops it. This test fails against the former and passes against
     * the latter, which is the entire point of it.
     */
    @Test
    fun eventEmittedWhileNothingIsSubscribed_isNotDeliveredToTheNextCollector() = runTest(testDispatcher) {
        val repository = mockk<AchievementRepository>()
        every { repository.observeDaily() } returns flowOf(emptyList())
        coEvery { repository.increment(1L) } returns
            outcome(newCount = 1, isDone = true, justCompleted = true, newStreak = 1)

        val viewModel = DailyViewModel(repository)

        viewModel.onIncrement(1L)
        advanceUntilIdle()

        // The inner advanceUntilIdle calls matter: they let a collector that *did* receive a
        // buffered element actually run and hand it to Turbine. Without them `expectNoEvents()`
        // would only be observing a scheduler that had not been given the chance to deliver
        // anything yet, and would pass against a buffering implementation too.
        viewModel.celebrations.test {
            testDispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
        viewModel.undoRequests.test {
            testDispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun undoingTheCompletingTap_thenRecompleting_emitsASecondEvent() = runTest(testDispatcher) {
        val repository = mockk<AchievementRepository>()
        every { repository.observeDaily() } returns flowOf(emptyList())
        val firstOutcome = outcome(newCount = 1, isDone = true, justCompleted = true, newStreak = 1)
        coEvery { repository.increment(1L) } returnsMany listOf(firstOutcome, firstOutcome)
        coEvery { repository.undo(any()) } returns true

        val viewModel = DailyViewModel(repository)

        viewModel.celebrations.test {
            viewModel.onIncrement(1L)
            awaitItem()

            viewModel.onUndo(firstOutcome.undoToken)
            viewModel.onIncrement(1L)
            awaitItem()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun isFirstFlame_isTrueOnlyWhenTheStreakWentFromZeroToOne() = runTest(testDispatcher) {
        val repository = mockk<AchievementRepository>()
        every { repository.observeDaily() } returns flowOf(emptyList())
        coEvery { repository.increment(1L) } returns
            outcome(newCount = 1, isDone = true, justCompleted = true, newStreak = 4)

        val viewModel = DailyViewModel(repository)

        viewModel.celebrations.test {
            viewModel.onIncrement(1L)
            val event = awaitItem() as CelebrationEvent.Completed
            assertFalse(event.isFirstFlame)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The failure this guards is CR-03: `onCelebrationSettled` is driven by a `LaunchedEffect`
     * tied to the celebrating card's composition, so an `Activity` recreation mid-animation, the
     * card scrolling out of the `LazyColumn`, or navigating away all cancel it before SETTLED —
     * while `_heldBackIds` lives in the ViewModel and survives all three. Without the ceiling the
     * achievement is stranded above the Done divider for the ViewModel's whole lifetime. Note the
     * complete absence of an `onCelebrationSettled` call below: that is the point of the test.
     */
    @Test
    fun heldBackId_whoseCelebrationIsCancelledBeforeSettling_isReleasedByTheHardCeiling() = runTest(testDispatcher) {
        val doneAchievement = achievement(id = 1, isDone = true, currentStreak = 1)
        val repository = mockk<AchievementRepository>()
        every { repository.observeDaily() } returns flowOf(listOf(doneAchievement))
        coEvery { repository.increment(1L) } returns
            outcome(newCount = 1, isDone = true, justCompleted = true, newStreak = 1)

        val viewModel = DailyViewModel(repository)

        viewModel.uiState.test {
            assertEquals(DailyUiState.Loading, awaitItem())
            awaitItem()

            viewModel.onIncrement(1L)
            val heldBack = awaitItem() as DailyUiState.Content
            assertTrue(heldBack.outstanding.any { it.id == 1L })

            advanceTimeBy(MotionSpecs.CELEBRATION_MAX_HOLD_MS + 1)

            val released = awaitItem() as DailyUiState.Content
            assertTrue("the ceiling must release a celebration that never settled", released.done.any { it.id == 1L })
            assertFalse(released.heldBackIds.contains(1L))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun heldBackIds_containsTheAchievementFromEmissionUntilSettled_andItStaysOutstanding() = runTest(testDispatcher) {
        val doneAchievement = achievement(id = 1, isDone = true, currentStreak = 1)
        val repository = mockk<AchievementRepository>()
        every { repository.observeDaily() } returns flowOf(listOf(doneAchievement))
        coEvery { repository.increment(1L) } returns
            outcome(newCount = 1, isDone = true, justCompleted = true, newStreak = 1)

        val viewModel = DailyViewModel(repository)

        viewModel.uiState.test {
            assertEquals(DailyUiState.Loading, awaitItem())
            val initial = awaitItem() as DailyUiState.Content
            assertTrue(initial.done.any { it.id == 1L })
            assertFalse(initial.heldBackIds.contains(1L))

            viewModel.onIncrement(1L)
            val heldBack = awaitItem() as DailyUiState.Content
            assertTrue("achievement stays outstanding while held back", heldBack.outstanding.any { it.id == 1L })
            assertFalse(heldBack.done.any { it.id == 1L })
            assertTrue(heldBack.heldBackIds.contains(1L))

            viewModel.onCelebrationSettled(1L)
            val settled = awaitItem() as DailyUiState.Content
            assertTrue(settled.done.any { it.id == 1L })
            assertFalse(settled.heldBackIds.contains(1L))

            cancelAndIgnoreRemainingEvents()
        }
    }
}
