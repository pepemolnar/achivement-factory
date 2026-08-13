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
        assertNull(state.saveError)
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

    /**
     * The field is fully controlled, so whatever the state says is what the user sees. Clearing
     * the box to retype must therefore leave it *empty*, not snap it back to `"1"` — when it
     * snapped back, typing `5` next produced `15`.
     */
    @Test
    fun onTargetChange_clearingTheFieldToRetype_doesNotRewriteItBackToOne() {
        val viewModel = CreateAchievementViewModel(fakeRepository())
        viewModel.onTargetChange("3")

        viewModel.onTargetChange("")
        assertEquals("", viewModel.uiState.value.targetInput)

        viewModel.onTargetChange("5")
        assertEquals("5", viewModel.uiState.value.targetInput)
        assertEquals(5, viewModel.uiState.value.target)
    }

    @Test
    fun onTargetChange_rejectsInputThatCouldNeverBeAValidTarget_keepingWhatWasThere() {
        val viewModel = CreateAchievementViewModel(fakeRepository())
        viewModel.onTargetChange("7")

        // Non-digits and over-long pastes are refused outright rather than silently substituted
        // with 1, which is the opposite of what the user asked for.
        viewModel.onTargetChange("99999999999")
        assertEquals("7", viewModel.uiState.value.targetInput)

        viewModel.onTargetChange("12a")
        assertEquals("7", viewModel.uiState.value.targetInput)
    }

    @Test
    fun target_isCappedSoAnUncompletableAchievementCannotBeCreated() = runTest(testDispatcher) {
        val repository = fakeRepository()
        val viewModel = CreateAchievementViewModel(repository)
        viewModel.onNameChange("Read")
        viewModel.onTargetChange("999")

        viewModel.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.create("Read", null, AccentSwatch.INDIGO, 999) }
    }

    /**
     * The `saveInFlight` latch is what the double-tap guard reads. Without the `finally` that
     * releases it, a single failed write leaves the Save button permanently dead — the user taps
     * and nothing happens, forever, with no feedback. The second `onSave()` below is the part that
     * fails if the latch is ever left stuck again.
     */
    @Test
    fun onSave_whenTheWriteThrows_surfacesAnErrorAndLeavesTheButtonUsableForARetry() = runTest(testDispatcher) {
        val repository = mockk<AchievementRepository>()
        coEvery { repository.create(any(), any(), any(), any()) } throws
            RuntimeException("SQLiteFullException") andThen 1L
        val viewModel = CreateAchievementViewModel(repository)
        viewModel.onNameChange("Read")

        viewModel.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Couldn't save. Try again.", viewModel.uiState.value.saveError)
        assertFalse(viewModel.uiState.value.saved)
        assertNull("a write failure must not be reported as a name-validation error", viewModel.uiState.value.nameError)

        viewModel.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { repository.create(any(), any(), any(), any()) }
        assertTrue(viewModel.uiState.value.saved)
        assertNull(viewModel.uiState.value.saveError)
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
