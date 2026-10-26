package com.achievementfactory.ui.achievement

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.domain.model.TrackedAchievement
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric-hosted Compose semantics-tree assertions (no device/emulator needed) covering
 * D-08: the streak flame badge must be entirely absent from the composed card at streak 0, and
 * present the moment the streak is 1.
 */
@RunWith(RobolectricTestRunner::class)
class AchievementCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun achievement(streak: Int) = TrackedAchievement(
        id = 1L,
        name = "Read",
        icon = null,
        accent = AccentSwatch.SKY,
        target = 1,
        count = 0,
        isDone = false,
        currentStreak = streak,
        bestStreak = streak,
    )

    @Test
    fun streakZero_rendersNoFlameBadgeNode() {
        composeRule.setContent {
            AchievementCard(achievement = achievement(streak = 0), onIncrement = {}, onLongPress = {})
        }

        composeRule.onNodeWithContentDescription("Current streak: 0").assertDoesNotExist()
    }

    @Test
    fun streakOne_rendersFlameBadgeNode() {
        composeRule.setContent {
            AchievementCard(achievement = achievement(streak = 1), onIncrement = {}, onLongPress = {})
        }

        composeRule.onNodeWithContentDescription("Current streak: 1").assertExists()
    }
}
