// Top-level build file where you can add configuration options common to all sub-projects/modules.
//
// AGP 9.0+ has built-in Kotlin support (see https://kotl.in/gradle/agp-built-in-kotlin), which
// replaces the org.jetbrains.kotlin.android plugin entirely — applying it now throws
// "The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since
// AGP 9.0." Built-in Kotlin defaults to a runtime dependency on Kotlin Gradle Plugin 2.2.10;
// CLAUDE.md pins Kotlin 2.3.20 exactly, so that version is forced here via the documented
// buildscript classpath override (the org.jetbrains.kotlin.plugin.compose plugin, which is
// unaffected by built-in Kotlin, still needs to be applied for the Compose compiler).
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.11")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
