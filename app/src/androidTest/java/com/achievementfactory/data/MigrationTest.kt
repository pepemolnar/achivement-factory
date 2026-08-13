package com.achievementfactory.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-test"

/**
 * Anchors a `MigrationTestHelper` harness on the committed version-1 schema export under
 * `app/schemas/` (DATA-03). There is nothing to migrate *from* yet — version 1 is the only
 * version this codebase has ever shipped — so this test proves what is meaningful today: a
 * database created from that exported schema, populated with an achievement row and a progress
 * row, survives being closed and reopened through Room with every row intact.
 *
 * -----------------------------------------------------------------------------------------
 * THE RULE FOR EVERY FUTURE SCHEMA VERSION BUMP — read this before touching
 * APP_DATABASE_VERSION:
 *
 *   1. Write a real `Migration(n, n + 1)`. The destructive-migration-fallback builder options
 *      must never be used as a substitute — see docs/RELEASE.md's standing rule.
 *   2. Add that migration to the `Room.databaseBuilder(...)` call in AppContainer.
 *   3. Bump `APP_DATABASE_VERSION` in AppDatabase.kt, run a build so the Room Gradle plugin
 *      exports the new schema JSON, and commit that file under `app/schemas/`
 *      (`SchemaExportTest` fails the build if this step is skipped).
 *   4. Extend THIS test (or add a sibling test) that calls `helper.createDatabase(TEST_DB, n)`,
 *      populates it with realistic rows at the OLD schema shape, closes it, then calls
 *      `helper.runMigrationsAndValidate(TEST_DB, n + 1, true, YourMigration(n, n + 1))` and
 *      asserts every row survived with the expected values. Never validate a migration against
 *      a freshly created empty database at the new version — that proves nothing about
 *      whether real data on the owner's phone would have survived.
 * -----------------------------------------------------------------------------------------
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun version1Database_createdFromTheExportedSchema_survivesACloseAndReopenWithDataIntact() {
        val createdAtEpochDay = 20_000L

        helper.createDatabase(TEST_DB, APP_DATABASE_VERSION).apply {
            execSQL(
                """
                INSERT INTO achievements
                    (id, name, periodType, icon, accentSwatch, counterTarget, bronzeThreshold,
                     silverThreshold, goldThreshold, archived, createdAtEpochDay, currentStreak,
                     bestStreak, streakCachedForPeriodKey)
                VALUES
                    (1, 'Read 10 pages', 'DAILY', NULL, 'BLUE', 1, NULL, NULL, NULL, 0,
                     $createdAtEpochDay, 3, 5, '2026-08-12')
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO progress
                    (id, achievementId, periodKey, count, done, tierReached, updatedAtEpochDay)
                VALUES
                    (1, 1, '2026-08-13', 1, 1, NULL, $createdAtEpochDay)
                """.trimIndent(),
            )
            close()
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB).build()
        helper.closeWhenFinished(reopened)

        runBlocking {
            val achievement = reopened.achievementDao().getById(1)
            assertNotNull("achievement row did not survive close/reopen", achievement)
            assertEquals("Read 10 pages", achievement?.name)
            assertEquals(3, achievement?.currentStreak)
            assertEquals(5, achievement?.bestStreak)
            assertEquals("2026-08-12", achievement?.streakCachedForPeriodKey)

            val progress = reopened.progressDao().getFor(achievementId = 1, periodKey = "2026-08-13")
            assertNotNull("progress row did not survive close/reopen", progress)
            assertEquals(1, progress?.count)
            assertTrue(progress?.done == true)
        }
    }
}
