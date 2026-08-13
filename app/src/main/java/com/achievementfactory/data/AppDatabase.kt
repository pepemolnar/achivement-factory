package com.achievementfactory.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.achievementfactory.data.achievement.AchievementDao
import com.achievementfactory.data.achievement.AchievementEntity
import com.achievementfactory.data.progress.ProgressDao
import com.achievementfactory.data.progress.ProgressEntity

/**
 * Schema version 1. `exportSchema = true` plus the committed JSON under `app/schemas/` is what
 * makes `MigrationTestHelper` possible for every future schema change (PITFALLS 7).
 *
 * The destructive-migration-fallback builder options must never appear in this builder chain
 * or anywhere else in the repository — grep-gated in Task 3's acceptance criteria and enforced
 * as a standing project rule (T-01-03). Every schema version bump instead ships an explicit
 * `Migration(from, to)` plus a `MigrationTestHelper` test.
 */
@Database(
    entities = [AchievementEntity::class, ProgressEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun achievementDao(): AchievementDao
    abstract fun progressDao(): ProgressDao
}
