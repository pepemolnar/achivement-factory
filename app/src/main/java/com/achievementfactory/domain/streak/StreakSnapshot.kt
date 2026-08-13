package com.achievementfactory.domain.streak

/**
 * The result of one [StreakCalculator.calculate] run: how many consecutive periods are
 * currently unbroken, and the highest that number has ever reached for this achievement.
 * [best] is always `>= current` by construction — it can only ever grow or hold, never shrink.
 */
data class StreakSnapshot(val current: Int, val best: Int)
