package com.achievementfactory

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the project's hardest product boundary — "everything lives in local storage on one
 * device, no account, no network, no sync" — as a build failure rather than as a line in a
 * document somebody has to remember to re-read.
 *
 * Two separate things are asserted because they fail independently:
 *
 *  1. No network permission is declared. Omitting `android.permission.INTERNET` is not merely
 *     policy; it is an OS-enforced guarantee that the app *cannot* make a network call even if a
 *     bug tried to. That guarantee evaporates the moment anyone adds the line "just in case".
 *  2. Local data is excluded from both off-device channels. `allowBackup="false"` covers cloud
 *     backup only — on Android 12+ (this app targets 36) device-to-device transfer is governed
 *     separately by `android:dataExtractionRules`, so without that attribute the Room database
 *     rides along to a new phone during setup transfer.
 *
 * Deliberately dependency-free: reads the source manifest and rules XML as text rather than
 * pulling in a manifest-parsing library for three assertions.
 */
class LocalOnlyBoundaryTest {

    /** Gradle's `Test` task working directory is the module directory (`app/`) by default. */
    private fun moduleFile(relativePath: String): File = File(System.getProperty("user.dir") ?: ".", relativePath)

    private val manifest: String get() = moduleFile("src/main/AndroidManifest.xml").readText()

    @Test
    fun manifest_declaresNoPermissionsAtAll() {
        assertFalse(
            "AndroidManifest.xml declares a <uses-permission>. This app is offline-only and " +
                "single-user by product constraint; it needs no permission of any kind, and the " +
                "absence of android.permission.INTERNET is what makes 'no network calls' an " +
                "OS-enforced guarantee rather than a convention.",
            manifest.contains("uses-permission"),
        )
    }

    @Test
    fun manifest_disablesCloudBackupAndPointsAtDeviceTransferRules() {
        assertTrue(
            "android:allowBackup must stay false — there is no cloud restore path for this app " +
                "(docs/RELEASE.md).",
            manifest.contains("android:allowBackup=\"false\""),
        )
        assertTrue(
            "android:dataExtractionRules is missing. allowBackup=\"false\" does NOT block " +
                "device-to-device transfer on Android 12+; without this attribute the Room " +
                "database is copied to a new phone during setup transfer.",
            manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""),
        )
    }

    @Test
    fun dataExtractionRules_excludeEveryDomainFromBothChannels() {
        val rules = moduleFile("src/main/res/xml/data_extraction_rules.xml").readText()

        val cloudBackup = rules.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")
        val deviceTransfer = rules.substringAfter("<device-transfer>").substringBefore("</device-transfer>")

        for (domain in listOf("root", "file", "database", "sharedpref", "external")) {
            val exclusion = "<exclude domain=\"$domain\" />"
            assertTrue("cloud-backup does not exclude the '$domain' domain", cloudBackup.contains(exclusion))
            assertTrue("device-transfer does not exclude the '$domain' domain", deviceTransfer.contains(exclusion))
        }
    }
}
