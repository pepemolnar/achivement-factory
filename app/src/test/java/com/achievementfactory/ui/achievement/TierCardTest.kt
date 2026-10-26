package com.achievementfactory.ui.achievement

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.domain.model.TrackedAchievement
import com.achievementfactory.domain.tier.Tier
import com.achievementfactory.domain.tier.TierLadder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * TIER-07 on the card: the tier reached this period is visible, and the progress text points at
 * the next tier. Robolectric-hosted, same as [AchievementCardTest].
 */
@RunWith(RobolectricTestRunner::class)
class TierCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun achievement(count: Int, tiers: TierLadder?, tierReached: Tier?, target: Int = 1) = TrackedAchievement(
        id = 1L,
        name = "Push-ups",
        icon = null,
        accent = AccentSwatch.SKY,
        target = tiers?.completeThreshold ?: target,
        count = count,
        isDone = if (tiers != null) tierReached == Tier.GOLD else count >= target,
        currentStreak = 0,
        bestStreak = 0,
        tiers = tiers,
        tierReached = tierReached,
    )

    @Test
    fun reachedTier_rendersATierBadge() {
        val ladder = TierLadder(bronze = 1, silver = 3, gold = 5)
        composeRule.setContent {
            AchievementCard(achievement = achievement(count = 3, tiers = ladder, tierReached = Tier.SILVER), onIncrement = {}, onLongPress = {})
        }

        composeRule.onNodeWithContentDescription("Tier reached: Silver").assertExists()
    }

    @Test
    fun noTierReached_rendersNoTierBadge() {
        val ladder = TierLadder(bronze = 2, silver = 3, gold = 5)
        composeRule.setContent {
            AchievementCard(achievement = achievement(count = 1, tiers = ladder, tierReached = null), onIncrement = {}, onLongPress = {})
        }

        composeRule.onNodeWithContentDescription("Tier reached", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("1/2").assertExists()
    }

    @Test
    fun progressLabel_pointsAtTheNextTier_thenAtGold() {
        val ladder = TierLadder(bronze = 2, silver = 4, gold = 6)
        assertEquals("0/2", progressLabel(achievement(count = 0, tiers = ladder, tierReached = null)))
        assertEquals("3/4", progressLabel(achievement(count = 3, tiers = ladder, tierReached = Tier.BRONZE)))
        assertEquals("5/6", progressLabel(achievement(count = 5, tiers = ladder, tierReached = Tier.SILVER)))
        assertEquals("7/6", progressLabel(achievement(count = 7, tiers = ladder, tierReached = Tier.GOLD)))
    }

    @Test
    fun progressLabel_keepsThePhase1Rules_withNoLadder() {
        assertNull(progressLabel(achievement(count = 0, tiers = null, tierReached = null, target = 1)))
        assertEquals("2/3", progressLabel(achievement(count = 2, tiers = null, tierReached = null, target = 3)))
    }

    @Test
    fun progressLabel_isHidden_forAGoldOnlyLadderOfOne() {
        assertNull(progressLabel(achievement(count = 0, tiers = TierLadder(null, null, 1), tierReached = null)))
    }
}
