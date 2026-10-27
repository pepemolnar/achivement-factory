package com.achievementfactory.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val DB_NAME = "migration-1-2-jvm.db"

/**
 * [MIGRATION_1_2] on the JVM, against a real SQLite file (Robolectric's native SQLite) — so the
 * migration is exercised by the device-free gate, not only by the instrumented `MigrationTest`.
 *
 * The version-1 database is built by hand from the exact `CREATE` statements and identity hash in
 * the committed `app/schemas/.../1.json`, populated with realistic rows, and then opened through
 * the app's own Room builder with [MIGRATION_1_2]. Room migrates it and then validates every table
 * against the version-2 entities, throwing if the migrated schema differs in any column, default
 * or index — so simply opening it is the schema assertion; the rest checks no data was lost and
 * the new column was seeded in the order the user already saw.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationOneToTwoTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun deleteDatabaseFile() {
        context.deleteDatabase(DB_NAME)
    }

    private fun createVersion1Database() {
        val file = context.getDatabasePath(DB_NAME).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `achievements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `periodType` TEXT NOT NULL, `icon` TEXT, `accentSwatch` TEXT NOT NULL, " +
                    "`counterTarget` INTEGER NOT NULL, `bronzeThreshold` INTEGER, `silverThreshold` INTEGER, " +
                    "`goldThreshold` INTEGER, `archived` INTEGER NOT NULL, `createdAtEpochDay` INTEGER NOT NULL, " +
                    "`currentStreak` INTEGER NOT NULL, `bestStreak` INTEGER NOT NULL, `streakCachedForPeriodKey` TEXT)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_achievements_periodType` ON `achievements` (`periodType`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_achievements_archived` ON `achievements` (`archived`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `progress` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`achievementId` INTEGER NOT NULL, `periodKey` TEXT NOT NULL, `count` INTEGER NOT NULL, " +
                    "`done` INTEGER NOT NULL, `tierReached` TEXT, `updatedAtEpochDay` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`achievementId`) REFERENCES `achievements`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_progress_achievementId_periodKey` " +
                    "ON `progress` (`achievementId`, `periodKey`)",
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                    "VALUES(42, 'a20f85ba6d4ddefb1493c24a8acf8f49')",
            )

            // Daily: created out of id order on purpose (id 3 is the oldest) to prove the seed
            // follows creation day, then id — not insertion order.
            db.execSQL(
                "INSERT INTO achievements VALUES " +
                    "(1, 'Read', 'DAILY', '📖', 'SKY', 1, NULL, NULL, NULL, 0, 20310, 4, 9, '2026-08-12'), " +
                    "(2, 'Run', 'DAILY', NULL, 'RED', 3, NULL, NULL, NULL, 0, 20311, 0, 2, '2026-08-12'), " +
                    "(3, 'Stretch', 'DAILY', NULL, 'TEAL', 1, NULL, NULL, NULL, 0, 20300, 1, 1, '2026-08-12'), " +
                    "(4, 'Push-ups', 'WEEKLY', NULL, 'AMBER', 2, 2, 4, 6, 0, 20305, 2, 3, '2026-08-10'), " +
                    "(5, 'Old habit', 'DAILY', NULL, 'PINK', 1, NULL, NULL, NULL, 1, 20200, 0, 30, NULL)",
            )
            db.execSQL(
                "INSERT INTO progress VALUES " +
                    "(1, 1, '2026-08-12', 1, 1, NULL, 20312), " +
                    "(2, 4, '2026-08-10', 4, 1, 'SILVER', 20312)",
            )
            db.version = 1
        }
    }

    @Test
    fun populatedVersion1Database_migratesToVersion2_withEveryRowIntact_andOrderSeeded() {
        createVersion1Database()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val dao = database.achievementDao()
                val read = dao.getById(1)
                assertNotNull(read)
                assertEquals("Read", read!!.name)
                assertEquals("📖", read.icon)
                assertEquals(4, read.currentStreak)
                assertEquals(9, read.bestStreak)
                assertEquals("2026-08-12", read.streakCachedForPeriodKey)

                // Daily creation order: Stretch (20300), Read (20310), Run (20311). The archived
                // daily row (20200) is older still and takes position 0 among all dailies.
                assertEquals(1, dao.getById(3)!!.sortOrder)
                assertEquals(2, dao.getById(1)!!.sortOrder)
                assertEquals(3, dao.getById(2)!!.sortOrder)
                assertEquals(0, dao.getById(5)!!.sortOrder)
                assertEquals("weekly positions are counted separately", 0, dao.getById(4)!!.sortOrder)

                val pushUps = dao.getById(4)!!
                assertEquals(2, pushUps.bronzeThreshold)
                assertEquals(6, pushUps.goldThreshold)
                assertTrue(dao.getById(5)!!.archived)

                val progress = database.progressDao().getFor(4, "2026-08-10")
                assertEquals(4, progress?.count)
                assertEquals("SILVER", progress?.tierReached)
                assertEquals(1, database.progressDao().countFor(1, "2026-08-12"))

                // Phase 4's todos table exists after the migration and is usable.
                val todoId = database.todoDao().insert(
                    com.achievementfactory.data.todo.TodoEntity(
                        title = "Call mum",
                        periodType = "DAILY",
                        periodKey = "2026-08-12",
                        createdAtEpochDay = 20312,
                    ),
                )
                assertEquals("Call mum", database.todoDao().getById(todoId)?.title)
            }
        } finally {
            database.close()
        }
    }
}
