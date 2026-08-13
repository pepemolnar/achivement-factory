package com.achievementfactory.ui.create

import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.data.achievement.AchievementRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A plain JVM test — [AchievementRepository] is stubbed with MockK as a test double (the same
 * concrete-class-mocking convention `DailyViewModelTest` established, since the class is final
 * and not meant to grow an interface for this alone).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateAchievementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeRepository(): AchievementRepository {
        val repository = mockk<AchievementRepository>()
        coEvery { repository.create(any(), any(), any(), any()) } returns 1L
        return repository
    }

    @Test
    fun initialState_hasExpectedDefaults() {
        val viewModel = CreateAchievementViewModel(fakeRepository())
        val state = viewModel.uiState.value

        assertEquals("", state.name)
        assertNull(state.icon)
        assertEquals(AccentSwatch.INDIGO, state.accent)
        assertEquals(1, state.target)
        assertFalse(state.advancedExpanded)
        assertNull(state.nameError)
        assertFalse(state.saved)
    }

    @Test
    fun onSave_withBlankName_setsErrorAndCallsRepositoryZeroTimes() = runTest(testDispatcher) {
        val repository = fakeRepository()
        val viewModel = CreateAchievementViewModel(repository)

        viewModel.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Name is required.", viewModel.uiState.value.nameError)
        coVerify(exactly = 0) { repository.create(any(), any(), any(), any()) }
    }

    @Test
    fun onSave_withWhitespaceOnlyName_setsErrorAndCallsRepositoryZeroTimes() = runTest(testDispatcher) {
        val repository = fakeRepository()
        val viewModel = CreateAchievementViewModel(repository)
        viewModel.onNameChange("   ")

        viewModel.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Name is required.", viewModel.uiState.value.nameError)
        coVerify(exactly = 0) { repository.create(any(), any(), any(), any()) }
    }

    @Test
    fun onSave_withOnlyNameFilled_succeedsUsingDefaultAccentAndTarget() = runTest(testDispatcher) {
        val repository = fakeRepository()
        val viewModel = CreateAchievementViewModel(repository)
        viewModel.onNameChange("Read")

        viewModel.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.create("Read", null, AccentSwatch.INDIGO, 1) }
        assertTrue(viewModel.uiState.value.saved)
    }

    @Test
    fun onNameChange_afterFailedSave_clearsNameError() = runTest(testDispatcher) {
        val viewModel = CreateAchievementViewModel(fakeRepository())
        viewModel.onSave()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Name is required.", viewModel.uiState.value.nameError)

        viewModel.onNameChange("R")

        assertNull(viewModel.uiState.value.nameError)
    }

    @Test
    fun onSave_trimsNameBeforeReachingRepository() = runTest(testDispatcher) {
        val repository = fakeRepository()
        val viewModel = CreateAchievementViewModel(repository)
        viewModel.onNameChange("  Read a book  ")

        viewModel.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.create("Read a book", null, AccentSwatch.INDIGO, 1) }
    }

    @Test
    fun onIconChange_withMultiCodepointEmojiFollowedByTrailingText_storesOnlyTheEmoji() {
        val viewModel = CreateAchievementViewModel(fakeRepository())

        // U+1F44D THUMBS UP SIGN + U+1F3FD EMOJI MODIFIER FITZPATRICK TYPE-4 — a single
        // grapheme cluster — followed by trailing pasted text.
        val compoundEmoji = "👍🏽"
        viewModel.onIconChange("${compoundEmoji}and more pasted text")

        assertEquals(compoundEmoji, viewModel.uiState.value.icon)
    }

    @Test
    fun onIconChange_withPastedSentence_storesOnlyTheFirstCharacter() {
        val viewModel = CreateAchievementViewModel(fakeRepository())

        viewModel.onIconChange("Hello world")

        assertEquals("H", viewModel.uiState.value.icon)
    }

    @Test
    fun onTargetChange_clampsToMinimumOfOne() {
        val viewModel = CreateAchievementViewModel(fakeRepository())

        viewModel.onTargetChange("0")
        assertEquals(1, viewModel.uiState.value.target)

        viewModel.onTargetChange("-5")
        assertEquals(1, viewModel.uiState.value.target)
    }

    @Test
    fun onTargetChange_withClearedOrNonNumericInput_fallsBackToOne() {
        val viewModel = CreateAchievementViewModel(fakeRepository())
        viewModel.onTargetChange("3")
        assertEquals(3, viewModel.uiState.value.target)

        viewModel.onTargetChange("")
        assertEquals(1, viewModel.uiState.value.target)

        viewModel.onTargetChange("abc")
        assertEquals(1, viewModel.uiState.value.target)
    }

    @Test
    fun onSave_flipsSavedExactlyOnce_repeatedCallDoesNotResave() = runTest(testDispatcher) {
        val repository = fakeRepository()
        val viewModel = CreateAchievementViewModel(repository)
        viewModel.onNameChange("Read")

        viewModel.onSave()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.create(any(), any(), any(), any()) }
    }
}
