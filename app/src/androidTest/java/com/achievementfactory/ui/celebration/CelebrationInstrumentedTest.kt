package com.achievementfactory.ui.celebration

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.achievementfactory.ui.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val ACHIEVEMENT_NAME = "Celebration replay check"

/** Matches any node carrying `AchievementCard`'s `achievement-celebrating-*` `testTag` — present
 *  only while [CompletionCelebration] is actively driving a card's ring/checkmark/flame. */
private val celebratingNodeMatcher = SemanticsMatcher("has a celebrating-prefixed test tag") { node ->
    node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("achievement-celebrating") == true
}

/**
 * The two no-replay cases only a real device/emulator can prove (CELEB-04, T-01-27, PITFALLS 8):
 * complete an achievement, then either recreate the `Activity` (simulating rotation/process
 * restoration) or move it through the backgrounded/resumed lifecycle states, and confirm the
 * celebration never fires a second time. No device or emulator is attached in the sandboxed
 * session this file was authored in; it is compiled by every automated `assembleDebugAndroidTest`
 * gate and is meant to run in the end-of-phase on-device pass, same as every prior plan's
 * device-dependent coverage in this phase.
 */
@RunWith(AndroidJUnit4::class)
class CelebrationInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun createAndCompleteAchievement() {
        composeRule.onNodeWithContentDescription("Add achievement").performClick()
        composeRule.onNodeWithText("Name").performTextInput(ACHIEVEMENT_NAME)
        composeRule.onNodeWithText("Save achievement").performClick()
        composeRule.waitForIdle()

        // Target defaults to 1 (D-17) — the one completing tap fires the celebration.
        composeRule.onNodeWithText(ACHIEVEMENT_NAME).performClick()

        // The celebration's own coroutine (t=0 ring close, ~250ms checkmark, ~400ms flame, then
        // SETTLED) is not frame-clock-driven, so wait explicitly for the sequence to finish
        // rather than relying on `waitForIdle()` alone.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(celebratingNodeMatcher).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun completingAnAchievement_thenRecreatingTheActivity_doesNotReplayTheCelebration() {
        createAndCompleteAchievement()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        // No replay buffer on the celebration channel (T-01-27) — a fresh collector after
        // recreation must receive nothing, so no card should be actively celebrating.
        val stillCelebrating = composeRule.onAllNodes(celebratingNodeMatcher).fetchSemanticsNodes()
        assertTrue("celebration replayed after Activity recreation: $stillCelebrating", stillCelebrating.isEmpty())
    }

    @Test
    fun completingAnAchievement_thenBackgroundingAndRestoringTheApp_doesNotReplayTheCelebration() {
        createAndCompleteAchievement()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        val stillCelebrating = composeRule.onAllNodes(celebratingNodeMatcher).fetchSemanticsNodes()
        assertTrue("celebration replayed after backgrounding/restoring: $stillCelebrating", stillCelebrating.isEmpty())
    }
}
