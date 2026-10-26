package com.achievementfactory.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.period.PeriodType
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
    fun populatedVersion1Database_migratesTo2_andReopensThroughRoomWithDataIntact() {
        val createdAtEpochDay = 20_000L

        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO achievements
                    (id, name, periodType, icon, accentSwatch, counterTarget, bronzeThreshold,
                     silverThreshold, goldThreshold, archived, createdAtEpochDay, currentStreak,
                     bestStreak, streakCachedForPeriodKey)
                VALUES
                    (1, 'Read 10 pages', 'DAILY', NULL, 'SKY', 1, NULL, NULL, NULL, 0,
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

        // v1 -> v2 (ACH-09's sortOrder). Validates the migrated schema against the committed 2.json.
        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_1_2)
            .build()
        helper.closeWhenFinished(reopened)

        runBlocking {
            val achievement = reopened.achievementDao().getById(1)
            assertNotNull("achievement row did not survive close/reopen", achievement)
            assertEquals("Read 10 pages", achievement?.name)
            assertEquals(3, achievement?.currentStreak)
            assertEquals(5, achievement?.bestStreak)
            assertEquals("2026-08-12", achievement?.streakCachedForPeriodKey)
            assertEquals("the only daily achievement is first in its tab", 0, achievement?.sortOrder)

            // The fixture must stay a row the *application* could actually have written. It used
            // to seed accentSwatch = 'BLUE', which is not an AccentSwatch member at all (the ten
            // are RED/ORANGE/AMBER/LIME/EMERALD/TEAL/SKY/INDIGO/VIOLET/PINK) — the test only
            // passed because it reads through the DAO and never through the mapping layer, where
            // an unrecognized name is silently replaced by a fallback swatch. A migration test
            // whose fixture is not a realistic row proves nothing about realistic rows surviving,
            // which is the entire property this file exists to assert. This assertion is what
            // keeps the fixture honest as the enum evolves.
            assertTrue(
                "fixture accentSwatch '${achievement?.accentSwatch}' is not an AccentSwatch member",
                AccentSwatch.entries.any { it.name == achievement?.accentSwatch },
            )
            assertTrue(
                "fixture periodType '${achievement?.periodType}' is not a PeriodType member",
                PeriodType.entries.any { it.name == achievement?.periodType },
            )

            val progress = reopened.progressDao().getFor(achievementId = 1, periodKey = "2026-08-13")
            assertNotNull("progress row did not survive close/reopen", progress)
            assertEquals(1, progress?.count)
            assertTrue(progress?.done == true)
        }
    }
}
