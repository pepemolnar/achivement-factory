package com.achievementfactory.data.progress

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.achievementfactory.data.achievement.AchievementEntity

/**
 * One row per achievement per period instance — the immutable log that is the source of truth
 * for streaks and tier history (ARCHITECTURE.md "Streak Computation").
 *
 * The **unique** index on `["achievementId", "periodKey"]` is what makes "at most one progress
 * row per achievement per period" a database guarantee rather than application discipline
 * (DATA-01). The `CASCADE` foreign key means archiving/deleting an achievement's row also
 * removes its progress history.
 *
 * [done] and [tierReached] are snapshotted at write time, never derived on read from the
 * achievement's *current* target/thresholds — so a closed period's recorded result is
 * immutable even if the achievement is edited later (TRACK-10, D-28).
 */
@Entity(
    tableName = "progress",
    indices = [Index(value = ["achievementId", "periodKey"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = AchievementEntity::class,
            parentColumns = ["id"],
            childColumns = ["achievementId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val achievementId: Long,
    val periodKey: String,
    val count: Int = 0,
    val done: Boolean = false,
    val tierReached: String? = null, // "BRONZE" | "SILVER" | "GOLD" | null — snapshotted
    val updatedAtEpochDay: Long,
)
