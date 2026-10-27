package com.achievementfactory.ui.period

import app.cash.turbine.test
import com.achievementfactory.data.achievement.AchievementRepository
import com.achievementfactory.domain.model.IncrementOutcome
import com.achievementfactory.domain.model.UndoToken
import com.achievementfactory.domain.tier.Tier
import com.achievementfactory.period.PeriodKey
import com.achievementfactory.period.PeriodType
import com.achievementfactory.ui.celebration.CelebrationEvent
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CELEB-01/CELEB-02 at the ViewModel edge, under the revised TIER-06 (done = Gold, streak = lowest
 * tier): medals below Gold emit [CelebrationEvent.TierUpgraded] without holding the card back,
 * the lowest medal ticks the streak flame, Gold emits [CelebrationEvent.Completed] without
 * re-ticking it, and every one of these follows the same zero-replay rules as Phase 1's
 * completion event (see [CelebrationEventTest]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TierCelebrationEventTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun outcome(
        newCount: Int,
        tierReached: Tier?,
        justCompleted: Boolean = false,
        tierUpgraded: Boolean = false,
        streakSecured: Boolean = false,
        newStreak: Int = 1,
    ) = IncrementOutcome(
        newCount = newCount,
        isDone = tierReached == Tier.GOLD,
        justCompleted = justCompleted,
        newStreak = newStreak,
        undoToken = UndoToken(achievementId = 1L, periodKey = PeriodKey("2026-08-10"), expectedCount = newCount),
        tierReached = tierReached,
        tierUpgraded = tierUpgraded,
        streakSecured = streakSecured,
    )

    private fun viewModelReturning(vararg outcomes: IncrementOutcome): PeriodViewModel {
        val repository = mockk<AchievementRepository>()
        every { repository.observePeriod(PeriodType.WEEKLY) } returns flowOf(emptyList())
        coEvery { repository.increment(1L) } returnsMany outcomes.toList()
        return PeriodViewModel(repository, PeriodType.WEEKLY)
    }

    @Test
    fun lowestMedal_emitsATierUpgrade_thatTicksTheStreak() = runTest(testDispatcher) {
        val viewModel = viewModelReturning(
            outcome(newCount = 2, tierReached = Tier.BRONZE, tierUpgraded = true, streakSecured = true, newStreak = 1),
        )

        viewModel.celebrations.test {
            viewModel.onIncrement(1L)
            assertEquals(
                CelebrationEvent.TierUpgraded(
                    achievementId = 1L,
                    tier = Tier.BRONZE,
                    ticksStreak = true,
                    newStreak = 1,
                    isFirstFlame = true,
                ),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun higherMedalBelowGold_emitsATierUpgrade_withoutTickingTheStreak() = runTest(testDispatcher) {
        val viewModel = viewModelReturning(outcome(newCount = 3, tierReached = Tier.SILVER, tierUpgraded = true))

        viewModel.celebrations.test {
            viewModel.onIncrement(1L)
            val event = awaitItem() as CelebrationEvent.TierUpgraded
            assertEquals(Tier.SILVER, event.tier)
            assertFalse(event.ticksStreak)
            assertFalse(event.isFirstFlame)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun goldAfterALowerMedal_completes_withoutReTickingTheStreak() = runTest(testDispatcher) {
        val viewModel = viewModelReturning(
            outcome(newCount = 5, tierReached = Tier.GOLD, justCompleted = true, streakSecured = false, newStreak = 1),
        )

        viewModel.celebrations.test {
            viewModel.onIncrement(1L)
            val event = awaitItem() as CelebrationEvent.Completed
            assertEquals(Tier.GOLD, event.tier)
            assertFalse(event.ticksStreak)
            assertFalse("the first flame already played at the lowest medal", event.isFirstFlame)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun goldOnlyLadder_completesAndTicksTheStreakOnTheSameTap() = runTest(testDispatcher) {
        val viewModel = viewModelReturning(
            outcome(newCount = 2, tierReached = Tier.GOLD, justCompleted = true, streakSecured = true, newStreak = 1),
        )

        viewModel.celebrations.test {
            viewModel.onIncrement(1L)
            val event = awaitItem() as CelebrationEvent.Completed
            assertTrue(event.ticksStreak)
            assertTrue(event.isFirstFlame)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun noLadderCompletion_keepsPhase1Behaviour_noTier_andTicksTheStreak() = runTest(testDispatcher) {
        val repository = mockk<AchievementRepository>()
        every { repository.observePeriod(PeriodType.DAILY) } returns flowOf(emptyList())
        coEvery { repository.increment(1L) } returns IncrementOutcome(
            newCount = 1,
            isDone = true,
            justCompleted = true,
            newStreak = 1,
            undoToken = UndoToken(1L, PeriodKey("2026-08-12"), 1),
        )
        val viewModel = PeriodViewModel(repository)

        viewModel.celebrations.test {
            viewModel.onIncrement(1L)
            val event = awaitItem() as CelebrationEvent.Completed
            assertEquals(null, event.tier)
            assertTrue(event.ticksStreak)
            assertTrue(event.isFirstFlame)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun repeatTapWithinTheSameMedal_emitsNothing() = runTest(testDispatcher) {
        val viewModel = viewModelReturning(outcome(newCount = 4, tierReached = Tier.SILVER))

        viewModel.celebrations.test {
            viewModel.onIncrement(1L)
            testDispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun medalBelowGold_doesNotHoldTheCardBack() = runTest(testDispatcher) {
        val viewModel = viewModelReturning(
            outcome(newCount = 2, tierReached = Tier.BRONZE, tierUpgraded = true, streakSecured = true),
        )

        viewModel.uiState.test {
            assertEquals(PeriodUiState.Loading, awaitItem())
            assertTrue((awaitItem() as PeriodUiState.Content).heldBackIds.isEmpty())

            viewModel.onIncrement(1L)
            testDispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun goldCompletion_holdsTheCardBackLikeAnyCompletion() = runTest(testDispatcher) {
        val viewModel = viewModelReturning(outcome(newCount = 5, tierReached = Tier.GOLD, justCompleted = true))

        viewModel.uiState.test {
            assertEquals(PeriodUiState.Loading, awaitItem())
            awaitItem()

            viewModel.onIncrement(1L)
            assertTrue((awaitItem() as PeriodUiState.Content).heldBackIds.contains(1L))
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The CELEB-04 window, for the new event type: nothing subscribed at emission → never delivered. */
    @Test
    fun upgradeEmittedWhileNothingIsSubscribed_isNotDeliveredToTheNextCollector() = runTest(testDispatcher) {
        val viewModel = viewModelReturning(outcome(newCount = 3, tierReached = Tier.SILVER, tierUpgraded = true))

        viewModel.onIncrement(1L)
        advanceUntilIdle()

        viewModel.celebrations.test {
            testDispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
    }
}
