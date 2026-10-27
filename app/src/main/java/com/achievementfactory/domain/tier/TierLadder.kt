package com.achievementfactory.domain.tier

/**
 * An achievement's optional Bronze/Silver/Gold ladder (TIER-02 through TIER-06).
 *
 * Only ladders that fill *downward from Gold* exist: `{Gold}`, `{Silver, Gold}` or
 * `{Bronze, Silver, Gold}` (TIER-04), with thresholds strictly increasing from the lowest
 * configured tier up to Gold (TIER-05). The constructor enforces both, so holding a [TierLadder]
 * at all is proof it is valid — use [validate] to get a typed reason instead of an exception.
 *
 * Two thresholds matter, and they are deliberately different (TIER-06, as revised 2026-10-26):
 *
 * - [streakThreshold] — the lowest configured tier. Reaching it is enough for the period to count
 *   toward the streak. The repository writes it into `achievements.counterTarget` at creation,
 *   and the progress row's `done` column (`count >= target`) therefore means "this period counts
 *   toward the streak" for laddered achievements — which is why the Phase 1 SQL and the streak
 *   walk need no change at all.
 * - [completeThreshold] — Gold. Only reaching Gold makes the achievement *done* for the period in
 *   the UI: the card closes its ring and moves into the Done section. Bronze and Silver are
 *   milestones on the way, each celebrated as a tier upgrade.
 *
 * Pure Kotlin — no Android or Room import — so every rule here is covered by plain JUnit tests.
 */
data class TierLadder(
    val bronze: Int?,
    val silver: Int?,
    val gold: Int,
) {
    init {
        val error = errorFor(bronze, silver, gold)
        require(error == null) { "Invalid tier ladder bronze=$bronze silver=$silver gold=$gold: $error" }
    }

    /** The configured tiers, lowest first. Always ends with [Tier.GOLD]. */
    val tiers: List<Tier>
        get() = buildList {
            if (bronze != null) add(Tier.BRONZE)
            if (silver != null) add(Tier.SILVER)
            add(Tier.GOLD)
        }

    /** The lowest configured tier — the one whose threshold keeps the streak alive. */
    val lowestTier: Tier get() = tiers.first()

    /** The count at which the period counts toward the streak: the lowest configured tier. */
    val streakThreshold: Int get() = bronze ?: silver ?: gold

    /** The count at which the achievement is done for the period: always Gold. */
    val completeThreshold: Int get() = gold

    fun thresholdOf(tier: Tier): Int? = when (tier) {
        Tier.BRONZE -> bronze
        Tier.SILVER -> silver
        Tier.GOLD -> gold
    }

    /** The highest tier [count] reaches, or null when it is below [streakThreshold]. */
    fun tierFor(count: Int): Tier? = when {
        count >= gold -> Tier.GOLD
        silver != null && count >= silver -> Tier.SILVER
        bronze != null && count >= bronze -> Tier.BRONZE
        else -> null
    }

    /**
     * The next tier [count] has not reached yet, paired with its threshold — or null once Gold is
     * reached. Drives the card's "x/y" progress text toward the next medal.
     */
    fun nextTierAbove(count: Int): Pair<Tier, Int>? =
        tiers.firstOrNull { tier -> count < thresholdOf(tier)!! }?.let { it to thresholdOf(it)!! }

    companion object {
        const val MIN_THRESHOLD = 1

        /** Same ceiling as the plain counter target — a 4-digit threshold is a typo, not a goal. */
        const val MAX_THRESHOLD = 999

        /** Validates raw (possibly missing) thresholds without throwing. */
        fun validate(bronze: Int?, silver: Int?, gold: Int?): TierLadderValidation {
            val error = errorFor(bronze, silver, gold)
            return if (error == null) {
                TierLadderValidation.Valid(TierLadder(bronze, silver, gold!!))
            } else {
                TierLadderValidation.Invalid(error)
            }
        }

        /**
         * Rebuilds a ladder from the three nullable `achievements` columns. Returns null both for
         * "no ladder" (all three null) and — tolerantly, rather than throwing out of a read flow —
         * for a stored combination that is not a valid ladder (a hand-edited row). A read path
         * that throws here would terminate the tab's `uiState` flow for good, which is a far worse
         * failure than showing the achievement without medals.
         */
        fun fromStoredColumns(bronze: Int?, silver: Int?, gold: Int?): TierLadder? =
            (validate(bronze, silver, gold) as? TierLadderValidation.Valid)?.ladder

        private fun errorFor(bronze: Int?, silver: Int?, gold: Int?): TierLadderError? {
            if (gold == null) return TierLadderError.MISSING_GOLD
            if (bronze != null && silver == null) return TierLadderError.SKIPPED_LEVEL
            val configured = listOfNotNull(bronze, silver, gold)
            if (configured.any { it !in MIN_THRESHOLD..MAX_THRESHOLD }) return TierLadderError.OUT_OF_RANGE
            if (configured.zipWithNext().any { (lower, higher) -> lower >= higher }) {
                return TierLadderError.NOT_INCREASING
            }
            return null
        }
    }
}

sealed interface TierLadderValidation {
    data class Valid(val ladder: TierLadder) : TierLadderValidation
    data class Invalid(val error: TierLadderError) : TierLadderValidation
}

/** Why a set of thresholds is not a ladder — one case per rule in TIER-04/TIER-05. */
enum class TierLadderError {
    /** Every ladder must include Gold (TIER-04). */
    MISSING_GOLD,

    /** Bronze without Silver skips a level (TIER-04). */
    SKIPPED_LEVEL,

    /** A threshold outside [TierLadder.MIN_THRESHOLD]..[TierLadder.MAX_THRESHOLD]. */
    OUT_OF_RANGE,

    /** Thresholds must strictly increase Bronze → Silver → Gold (TIER-05). */
    NOT_INCREASING,
}
