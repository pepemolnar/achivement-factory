package com.achievementfactory.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: adds `achievements.sortOrder` for drag-to-reorder (ACH-09).
 *
 * The column is added with `DEFAULT 0` (matching the entity's `@ColumnInfo(defaultValue = "0")`,
 * which Room's post-migration validation compares against) and then seeded so that every existing
 * achievement keeps exactly the order it was shown in before the upgrade: creation order
 * (`createdAtEpochDay`, then `id` for same-day ties) within its period type. Each row's position
 * is the number of rows of the same period type that sort before it, so the seeded values are
 * 0-based and gap-free per tab. Archived rows are seeded the same way; their position only matters
 * again once restored, and a restore moves the achievement to the end of its tab anyway.
 *
 * Purely additive: no row is deleted or rewritten beyond the new column, so streak caches,
 * thresholds and the whole progress log pass through untouched.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE achievements ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            UPDATE achievements SET sortOrder = (
                SELECT COUNT(*) FROM achievements AS earlier
                WHERE earlier.periodType = achievements.periodType
                  AND (earlier.createdAtEpochDay < achievements.createdAtEpochDay
                       OR (earlier.createdAtEpochDay = achievements.createdAtEpochDay
                           AND earlier.id < achievements.id))
            )
            """.trimIndent(),
        )
    }
}
