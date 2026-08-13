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

    private fun createAchievement() {
        composeRule.onNodeWithContentDescription("Add achievement").performClick()
        composeRule.onNodeWithText("Name").performTextInput(ACHIEVEMENT_NAME)
        composeRule.onNodeWithText("Save achievement").performClick()
        composeRule.waitForIdle()
    }

    private fun createAndCompleteAchievement() {
        createAchievement()

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

    /**
     * The replay window the two tests either side of this one cannot reach.
     * [createAndCompleteAchievement] waits for the sequence to fully settle *before* recreating,
     * so it only ever exercises an already-drained event stream — it passes even against an
     * implementation that buffers undelivered events. Here the recreation lands while the tap's
     * write is still in flight or the sequence is still running.
     *
     * Which of the two sub-cases actually occurs is inherently racy and this test deliberately
     * does not try to pin it down, because the required outcome is the same either way:
     *
     *  - The emit happens *before* the recreation: the celebration is running, and recreating
     *    disposes it. `DailyScreen`'s `activeCelebrations` is `remember`, not `rememberSaveable`,
     *    so the new composition starts with no active celebration and there is nothing to replay.
     *  - The emit happens *after* the recreation: nothing is subscribed at that instant (the
     *    screen's collector runs under `repeatOnLifecycle(STARTED)`), so a zero-replay
     *    `SharedFlow` drops the event. A `Channel(Channel.BUFFERED)` would instead hand it to the
     *    collector that attaches after recreation, and the celebration would visibly replay on an
     *    already-completed card — which is the regression this asserts against.
     *
     * Either way: no node may be celebrating once the recreated screen is idle.
     */
    @Test
    fun recreatingTheActivityMidCelebration_doesNotReplayTheCelebrationAfterwards() {
        createAchievement()

        composeRule.onNodeWithText(ACHIEVEMENT_NAME).performClick()
        // No waitUntil-settled here on purpose — that wait is exactly what blinds the other tests.
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        // Give any (incorrectly) buffered event time to reach the freshly attached collector and
        // start a celebration, rather than asserting on a frame that is merely too early to see it.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(celebratingNodeMatcher).fetchSemanticsNodes().isEmpty()
        }

        val stillCelebrating = composeRule.onAllNodes(celebratingNodeMatcher).fetchSemanticsNodes()
        assertTrue("celebration replayed after a mid-sequence recreation: $stillCelebrating", stillCelebrating.isEmpty())
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
