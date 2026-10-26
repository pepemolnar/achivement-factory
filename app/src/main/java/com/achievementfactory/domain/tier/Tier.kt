package com.achievementfactory.domain.tier

/**
 * The fixed, unrenameable tier names (TIER-03), declared lowest to highest so [ordinal] order is
 * rank order — `Tier.GOLD > Tier.SILVER` is a meaningful comparison everywhere in this project.
 *
 * [name] is also the exact string snapshotted into `progress.tierReached` ("BRONZE" | "SILVER" |
 * "GOLD"), so the constants must never be renamed: a rename would orphan every recorded period.
 */
enum class Tier(val displayName: String) {
    BRONZE("Bronze"),
    SILVER("Silver"),
    GOLD("Gold"),
    ;

    companion object {
        /** Tolerant parse of a stored `tierReached` value — unknown or null reads as "no tier". */
        fun fromStored(value: String?): Tier? = entries.firstOrNull { it.name == value }
    }
}
