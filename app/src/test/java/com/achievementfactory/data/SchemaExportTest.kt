package com.achievementfactory.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards DATA-03: if `APP_DATABASE_VERSION` is ever bumped without a matching schema export
 * committed under `app/schemas/`, this test turns that silent gap into a red build — the
 * cheapest way to keep migration discipline true across four more phases (PITFALLS 7).
 *
 * Deliberately dependency-free: hand-parses just enough of the exported schema JSON to confirm
 * it exists and reports the same version this database annotation declares, rather than pulling
 * in a JSON library for a single assertion.
 *
 * **Why this also checks `git ls-files`, not just `File.exists()`:** the `androidx.room` Gradle
 * plugin re-exports the schema JSON as a side effect of *any* build that compiles this module
 * (verified empirically while writing this test — bumping the version and simply re-running
 * `:app:testDebugUnitTest` silently regenerates the new file on disk before the test body ever
 * runs, which would make a plain existence check pass even though nobody committed anything).
 * The actual failure mode DATA-03 needs guarding against is "the export stopped being
 * *committed*", so the second assertion below fails unless the file is tracked by git — a
 * locally-regenerated-but-uncommitted schema still trips it.
 */
class SchemaExportTest {

    @Test
    fun exportedSchemaFileExists_forCurrentDatabaseVersion() {
        val schemaFile = schemaFileFor(APP_DATABASE_VERSION)

        assertTrue(
            "Expected an exported schema at ${schemaFile.path} for APP_DATABASE_VERSION=" +
                "$APP_DATABASE_VERSION. Run a build that triggers the Room Gradle plugin's " +
                "schema copy step and commit the result whenever the version is bumped.",
            schemaFile.exists(),
        )
    }

    @Test
    fun exportedSchemaFile_reportsTheSameVersionAsTheAnnotation() {
        val schemaFile = schemaFileFor(APP_DATABASE_VERSION)
        assertTrue("Schema file must exist before its contents can be checked", schemaFile.exists())

        val declaredVersion = extractDatabaseVersion(schemaFile.readText())
        assertEquals(APP_DATABASE_VERSION, declaredVersion)
    }

    @Test
    fun exportedSchemaFile_isCommittedToVersionControl() {
        val schemaFile = schemaFileFor(APP_DATABASE_VERSION)
        assertTrue("Schema file must exist before its git status can be checked", schemaFile.exists())

        assertTrue(
            "${schemaFile.path} exists on disk (the Room Gradle plugin regenerates it on every " +
                "build) but is NOT committed to git. Run `git add ${schemaFile.path}` and commit " +
                "it — an uncommitted schema export is functionally the same as a missing one the " +
                "moment someone else clones this repo or the local build output is cleaned.",
            isTrackedByGit(schemaFile),
        )
    }

    private fun schemaFileFor(version: Int): File =
        File(moduleRoot(), "schemas/com.achievementfactory.data.AppDatabase/$version.json")

    /** Gradle's `Test` task working directory is the module directory (`app/`) by default. */
    private fun moduleRoot(): File = File(System.getProperty("user.dir") ?: ".")

    /**
     * Extracts the `"version"` field nested inside the top-level `"database"` object — not the
     * sibling `"formatVersion"` field a naive first-match regex would catch instead.
     */
    private fun extractDatabaseVersion(json: String): Int {
        val databaseObjectStart = json.indexOf("\"database\"")
        require(databaseObjectStart >= 0) { "Schema file has no top-level \"database\" object" }

        val versionMatch = Regex("\"version\"\\s*:\\s*(\\d+)").find(json, databaseObjectStart)
        requireNotNull(versionMatch) { "No \"version\" field found after \"database\" in schema file" }

        return versionMatch.groupValues[1].toInt()
    }

    private fun isTrackedByGit(file: File): Boolean = try {
        val process = ProcessBuilder("git", "ls-files", "--error-unmatch", file.name)
            .directory(file.parentFile)
            .redirectErrorStream(true)
            .start()
        process.waitFor() == 0
    } catch (_: Exception) {
        // No git binary on PATH (e.g. an unusual CI image) — don't fail the whole suite over an
        // environment gap unrelated to schema-export correctness; the existence/version checks
        // above still run.
        true
    }
}
