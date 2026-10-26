package com.achievementfactory.ui.create

import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.data.achievement.AchievementRepository
import com.achievementfactory.domain.edit.ThresholdChangeError
import com.achievementfactory.domain.model.AchievementDefinition
import com.achievementfactory.domain.model.UpdateResult
import com.achievementfactory.domain.tier.TierLadder
import com.achievementfactory.period.PeriodType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The form in edit mode (ACH-03 – ACH-05), with the repository mocked like every other ViewModel test. */
@OptIn(ExperimentalCoroutinesApi::class)
class EditAchievementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val plain = AchievementDefinition(
        id = 7L,
        name = "Read",
        icon = "📖",
        accent = AccentSwatch.TEAL,
        periodType = PeriodType.WEEKLY,
        target = 3,
        tiers = null,
        archived = false,
    )

    private fun repositoryWith(definition: AchievementDefinition?): AchievementRepository {
        val repository = mockk<AchievementRepository>()
        coEvery { repository.getDefinition(7L) } returns definition
        coEvery { repository.update(any(), any(), any(), any(), any(), any()) } returns UpdateResult.Updated
        return repository
    }

    @Test
    fun loads_theAchievementIntoTheForm() = runTest(testDispatcher) {
        val viewModel = CreateAchievementViewModel(repositoryWith(plain), editingId = 7L)
        assertTrue(viewModel.uiState.value.loading)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertTrue(state.isEditing)
        assertEquals("Read", state.name)
        assertEquals("📖", state.icon)
        assertEquals(AccentSwatch.TEAL, state.accent)
        assertEquals(PeriodType.WEEKLY, state.periodType)
        assertEquals("3", state.targetInput)
        assertTrue("a target above 1 opens Advanced so it's visible", state.advancedExpanded)
    }

    @Test
    fun periodType_cannotChangeInEditMode() = runTest(testDispatcher) {
        val viewModel = CreateAchievementViewModel(repositoryWith(plain), editingId = 7L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPeriodTypeChange(PeriodType.DAILY)

        assertEquals(PeriodType.WEEKLY, viewModel.uiState.value.periodType)
    }

    @Test
    fun save_callsUpdate_notCreate() = runTest(testDispatcher) {
        val repository = repositoryWith(plain)
        val viewModel = CreateAchievementViewModel(repository, editingId = 7L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onNameChange("Read more")
        viewModel.onTargetChange("5")

        viewModel.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.update(7L, "Read more", "📖", AccentSwatch.TEAL, 5, null) }
        coVerify(exactly = 0) { repository.create(any(), any(), any(), any(), any(), any()) }
        assertTrue(viewModel.uiState.value.saved)
    }

    @Test
    fun loweringTheTarget_isExplainedUnderTheField_andNotSaved() = runTest(testDispatcher) {
        val repository = repositoryWith(plain)
        val viewModel = CreateAchievementViewModel(repository, editingId = 7L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onTargetChange("2")

        viewModel.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "The target can only go up — it's 3 now. A lower one would make the streak easier.",
            viewModel.uiState.value.thresholdError,
        )
        assertFalse(viewModel.uiState.value.saved)
        coVerify(exactly = 0) { repository.update(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun anExistingLadder_cannotBeSwitchedOff() = runTest(testDispatcher) {
        val laddered = plain.copy(target = 2, tiers = TierLadder(null, 2, 4))
        val viewModel = CreateAchievementViewModel(repositoryWith(laddered), editingId = 7L)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.ladderLocked)

        viewModel.onTiersEnabledChange(false)

        assertTrue(viewModel.uiState.value.tiersEnabled)
    }

    @Test
    fun aRefusalFromTheRepository_isShownToo() = runTest(testDispatcher) {
        val repository = repositoryWith(plain)
        coEvery { repository.update(any(), any(), any(), any(), any(), any()) } returns
            UpdateResult.Refused(ThresholdChangeError.TargetLowered)
        val viewModel = CreateAchievementViewModel(repository, editingId = 7L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.thresholdError != null)
        assertFalse(viewModel.uiState.value.saved)
    }

    @Test
    fun aVanishedAchievement_showsAnError_andSaveDoesNothing() = runTest(testDispatcher) {
        val repository = repositoryWith(null)
        val viewModel = CreateAchievementViewModel(repository, editingId = 7L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onNameChange("Anything")
        viewModel.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("This achievement is no longer here.", viewModel.uiState.value.saveError)
        coVerify(exactly = 0) { repository.update(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { repository.create(any(), any(), any(), any(), any(), any()) }
    }
}
