package com.achievementfactory.data.achievement

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Schema version 1. This is the one chance to get the column set right before every future
 * change requires a migration, so it carries columns Phases 2 and 3 need even though this
 * phase does not populate them:
 *
 * - [bronzeThreshold]/[silverThreshold]/[goldThreshold] and [archived] are unused in Phase 1
 *   (TIER-01 only requires the field to exist and the no-tier path — all three null — to
 *   work); including them now avoids a migration in Phase 2.
 * - [icon] and [accentSwatch] are this project's additions to ARCHITECTURE.md's proposed
 *   entity, required by ACH-02.
 */
@Entity(
    tableName = "achievements",
    indices = [Index("periodType"), Index("archived")],
)
data class AchievementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val periodType: String, // "DAILY" | "WEEKLY" | "MONTHLY"
    val icon: String?, // emoji glyph from the system keyboard; null => first-letter fallback (D-18)
    val accentSwatch: String, // an AccentSwatch name
    val counterTarget: Int = 1,
    val bronzeThreshold: Int? = null,
    val silverThreshold: Int? = null,
    val goldThreshold: Int? = null,
    val archived: Boolean = false,
    val createdAtEpochDay: Long,
    // Denormalized read cache — see AppDatabase.kt / ARCHITECTURE.md "Streak Computation".
    // Source of truth is always the immutable `progress` log; these columns are recomputed by
    // a bounded backward walk whenever streakCachedForPeriodKey is behind the current key.
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val streakCachedForPeriodKey: String? = null,
)
