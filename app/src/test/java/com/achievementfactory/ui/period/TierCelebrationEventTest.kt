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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CELEB-01/CELEB-02 at the ViewModel edge: the completion event carries the tier reached, a
 * mid-period upgrade emits exactly one [CelebrationEvent.TierUpgraded], and both follow the same
 * zero-replay rules as Phase 1's completion event (see [CelebrationEventTest]).
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
        justCompleted: Boolean = false,
        tierReached: Tier? = null,
        tierUpgraded: Boolean = false,
    ) = IncrementOutcome(
        newCount = newCount,
        isDone = tierReached != null,
        justCompleted = justCompleted,
        newStreak = 1,
        undoToken = UndoToken(achievementId = 1L, periodKey = PeriodKey("2026-08-10"), expectedCount = newCount),
        tierReached = tierReached,
        tierUpgraded = tierUpgraded,
    )

    private fun viewModelReturning(vararg outcomes: IncrementOutcome): PeriodViewModel {
        val repository = mockk<AchievementRepository>()
        every { repository.observePeriod(PeriodType.WEEKLY) } returns flowOf(emptyList())
        coEvery { repository.increment(1L) } returnsMany outcomes.toList()
        return PeriodViewModel(repository, PeriodType.WEEKLY)
    }

    @Test
    fun completingTap_onALadder_carriesTheLowestTierIntoTheCompletedEvent() = runTest(testDispatcher) {
        val viewModel = viewModelReturning(outcome(newCount = 2, justCompleted = true, tierReached = Tier.BRONZE))

        viewModel.celebrations.test {
            viewModel.onIncrement(1L)
            val event = awaitItem() as CelebrationEvent.Completed
            assertEquals(Tier.BRONZE, event.tier)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun completingTap_withNoLadder_carriesNoTier() = runTest(testDispatcher) {
        val viewModel = viewModelReturning(outcome(newCount = 1, justCompleted = true, tierReached = null))

        viewModel.celebrations.test {
            viewModel.onIncrement(1L)
            assertEquals(null, (awaitItem() as CelebrationEvent.Completed).tier)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun upgradeTap_emitsExactlyOneTierUpgradedEvent_forTheNewTier() = runTest(testDispatcher) {
        val viewModel = viewModelReturning(
            outcome(newCount = 3, tierReached = Tier.SILVER, tierUpgraded = true),
            outcome(newCount = 4, tierReached = Tier.SILVER, tierUpgraded = false),
            outcome(newCount = 5, tierReached = Tier.GOLD, tierUpgraded = true),
        )

        viewModel.celebrations.test {
            viewModel.onIncrement(1L)
            assertEquals(CelebrationEvent.TierUpgraded(1L, Tier.SILVER), awaitItem())

            viewModel.onIncrement(1L)
            testDispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()

            viewModel.onIncrement(1L)
            assertEquals(CelebrationEvent.TierUpgraded(1L, Tier.GOLD), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun upgradeTap_doesNotHoldTheCardBackFromTheDoneSection() = runTest(testDispatcher) {
        val viewModel = viewModelReturning(outcome(newCount = 3, tierReached = Tier.SILVER, tierUpgraded = true))

        viewModel.uiState.test {
            assertEquals(PeriodUiState.Loading, awaitItem())
            val before = awaitItem() as PeriodUiState.Content
            assertTrue(before.heldBackIds.isEmpty())

            viewModel.onIncrement(1L)
            testDispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
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

    @Test
    fun viewModel_readsItsOwnPeriodTypeOnly() = runTest(testDispatcher) {
        val repository = mockk<AchievementRepository>()
        every { repository.observePeriod(PeriodType.MONTHLY) } returns flowOf(emptyList())

        val viewModel = PeriodViewModel(repository, PeriodType.MONTHLY)

        viewModel.uiState.test {
            assertEquals(PeriodUiState.Loading, awaitItem())
            assertTrue((awaitItem() as PeriodUiState.Content).isEmpty)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
