---
phase: 01-daily-achievements-core-loop-foundation
plan: 01
subsystem: infra
tags: [android, kotlin, jetpack-compose, room, gradle, java-time, agp9]

# Dependency graph
requires: []
provides:
  - "A building, installable Compose app (`./gradlew :app:assembleDebug`) on a provisioned JDK 21/Android SDK 36/Gradle 9.4.1 toolchain"
  - "A dependency-free `com.achievementfactory.period` package (PeriodType/PeriodKey/AppClock/PeriodEngine) with exhaustive DST/year-boundary/locale unit tests"
  - "Room schema v1 (AchievementEntity, ProgressEntity) with exported+committed schema JSON, unique (achievementId, periodKey) index, CASCADE FK"
  - "AppContainer — the project's sole hand-written DI mechanism, wired into AchievementFactoryApp.onCreate()"
affects: ["01-02", "01-03", "01-04", "01-05", "01-06", "01-07", "01-08", "phase-2", "phase-3", "phase-4"]

# Tech tracking
tech-stack:
  added: [kotlin-2.3.20, agp-9.2.0, gradle-9.4.1, compose-bom-2026.04.01, room-2.8.4, ksp-2.3.11, navigation-compose-2.9.8, lifecycle-2.10.0, robolectric-4.16.1, mockk-1.14.11, turbine-1.2.1, androidx-test-core-1.7.0]
  patterns:
    - "AGP 9 built-in Kotlin support — no org.jetbrains.kotlin.android plugin applied; Kotlin/KSP versions pinned via top-level buildscript classpath override"
    - "java.time civil types exclusively for period math; DayOfWeek.MONDAY hardcoded; week identity = that week's Monday LocalDate"
    - "Room streak/tier columns present in schema v1 but unpopulated until Phase 2 (avoids a future migration)"
    - "Hand-written AppContainer as the only DI mechanism (no Hilt/Koin/Dagger)"

key-files:
  created:
    - gradle/libs.versions.toml
    - app/build.gradle.kts
    - app/src/main/AndroidManifest.xml
    - app/src/main/java/com/achievementfactory/period/PeriodEngine.kt
    - app/src/test/java/com/achievementfactory/period/PeriodEngineTest.kt
    - app/src/main/java/com/achievementfactory/data/AppDatabase.kt
    - app/src/main/java/com/achievementfactory/AppContainer.kt
    - app/schemas/com.achievementfactory.data.AppDatabase/1.json
  modified: []

key-decisions:
  - "Gradle wrapper pinned to 9.4.1, not 9.1.x — AGP 9.2.0's own version-check plugin requires Gradle >= 9.4.1 at build time (research assumption was outdated)"
  - "No org.jetbrains.kotlin.android plugin applied — AGP 9.0+ built-in Kotlin support replaces it entirely; Kotlin 2.3.20 pinned via a top-level buildscript classpath override instead"
  - "No explicit kotlin { jvmToolchain(17) } — relies on compileOptions sourceCompatibility/targetCompatibility=17 with the JDK 21 build environment, avoiding a toolchain auto-provisioning dependency this sandboxed host cannot satisfy interactively"
  - "JDK 21 and the Android SDK cmdline-tools were installed via direct zip download/extract to user-writable paths (~/.jdks, %LOCALAPPDATA%/Android/Sdk) instead of the MSI-based winget install, which hung on an elevated, non-interactive UAC dialog"

patterns-established:
  - "Period math lives exclusively in com.achievementfactory.period, zero Android/Room imports, injectable AppClock for deterministic testing"
  - "Progress rows snapshot done/tierReached at write time — never derived live from the achievement's current thresholds"
  - "Counter mutation DAOs deliberately omit read-modify-write helpers; atomic UPDATE...SET count = count + 1 lands in 01-04"

requirements-completed: [DATA-01, DATA-02, DATA-03, DATA-04, PERIOD-01]

# Metrics
duration: 34min
completed: 2026-08-13
---

# Phase 1 Plan 1: Project Scaffold, Period Engine & Room Schema Summary

**Android Compose app scaffolded on AGP 9.2.0/Kotlin 2.3.20/Room 2.8.4, with a civil-date period engine (DST/year-boundary/locale-tested) and a Room schema v1 that has zero network permissions and a database-enforced one-progress-row-per-period constraint.**

## Performance

- **Duration:** ~34 min (most of it toolchain provisioning: JDK/Android SDK/Gradle download+extract)
- **Started:** 2026-08-13T08:03:41Z
- **Completed:** 2026-08-13T08:37:10Z
- **Tasks:** 3 / 3
- **Files modified:** 34 (24 in Task 1, 5 in Task 2, 10 in Task 3, one file — `AchievementFactoryApp.kt` — touched in both Task 1 and Task 3)

## Accomplishments

- A greenfield Gradle/Android project that builds a debug APK end to end (`./gradlew :app:assembleDebug`) and passes 15/15 unit tests (`./gradlew :app:testDebugUnitTest`)
- `com.achievementfactory.period`: `PeriodType`, `PeriodKey`, `AppClock`/`SystemAppClock`, `PeriodEngine` — all three period types derived exclusively from `java.time` civil dates, `DayOfWeek.MONDAY` hardcoded, zero Android/Room dependency
- Room schema version 1 (`AchievementEntity`, `ProgressEntity`) with a committed, exported schema JSON baseline, a unique `(achievementId, periodKey)` index, and a `CASCADE` foreign key — no `fallbackToDestructiveMigration` anywhere in source
- `AppContainer` wired into `AchievementFactoryApp.onCreate()` as the project's sole DI mechanism
- Local dev toolchain fully provisioned on a machine that started with only JDK 1.8: Temurin 21, Android SDK (platform-tools, platform 36, build-tools 36.0.0), Gradle 9.4.1 wrapper

## Task Commits

Each task was committed atomically:

1. **Task 1: Provision the toolchain and scaffold a building Compose app** - `bcf87f2` (feat)
2. **Task 2: Build the period engine on civil dates, with the edge cases under test** - `c20b9c7` (feat)
3. **Task 3: Persist schema v1 in Room with export and no destructive fallback** - `df8e29d` (feat)

**Plan metadata:** _pending — recorded after this summary is written_

## Files Created/Modified

- `gradle/libs.versions.toml` - Version catalog pinning Kotlin 2.3.20, AGP 9.2.0, Room 2.8.4, Compose BOM 2026.04.01, KSP 2.3.11, and all supporting test libraries
- `build.gradle.kts` - Top-level plugin declarations + buildscript classpath override pinning KGP/KSP versions under AGP 9's built-in Kotlin support
- `app/build.gradle.kts` - App module config: compileSdk/targetSdk 36, minSdk 26, Room schema export, dependency wiring
- `app/src/main/AndroidManifest.xml` - Zero permissions, `allowBackup=false`, single exported `MainActivity`
- `app/src/main/java/com/achievementfactory/ui/theme/{Color,Type,Spacing,Theme}.kt` - Material3 baseline theme from UI-SPEC (no dynamic color)
- `app/src/main/java/com/achievementfactory/ui/MainActivity.kt` - Static "Daily" Scaffold shell (01-02 replaces the body)
- `app/src/main/java/com/achievementfactory/period/{PeriodType,PeriodKey,AppClock,PeriodEngine}.kt` - The civil-date period engine
- `app/src/test/java/com/achievementfactory/period/PeriodEngineTest.kt` - 10 tests: DST spring-forward/fall-back, 2026→2027 week boundary, month lengths incl. leap February, previous/next round-trips, US/UK locale independence
- `app/src/main/java/com/achievementfactory/data/AppDatabase.kt` - Room database v1, `exportSchema = true`
- `app/src/main/java/com/achievementfactory/data/achievement/{AccentSwatch,AchievementEntity,AchievementDao}.kt` - Achievement schema + DAO
- `app/src/main/java/com/achievementfactory/data/progress/{ProgressEntity,ProgressDao}.kt` - Progress log schema + DAO, unique index, CASCADE FK
- `app/src/main/java/com/achievementfactory/{AppContainer,AchievementFactoryApp}.kt` - Hand-written DI container wired into the Application
- `app/src/test/java/com/achievementfactory/data/AppDatabaseTest.kt` - 5 Robolectric tests against an in-memory Room database
- `app/schemas/com.achievementfactory.data.AppDatabase/1.json` - Committed schema v1 baseline for future `MigrationTestHelper` tests

## Decisions Made

- **Gradle wrapper pinned to 9.4.1, not the plan's suggested 9.1.x.** AGP 9.2.0's own `com.android.internal.version-check` plugin refused to apply under Gradle 9.1.0 with `Minimum supported Gradle version is 9.4.1`. This is a direct, first-party error from the actual toolchain, overriding STACK.md's research assumption. 9.4.1 is the documented minimum, so it was pinned exactly.
- **Dropped the `org.jetbrains.kotlin.android` plugin entirely.** AGP 9.0+ ships built-in Kotlin support and applying `kotlin-android` on top now fails outright (`The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0`). Per the official migration guide, the plugin was removed from both build files and the version catalog; Kotlin 2.3.20 (CLAUDE.md's pin, above AGP's built-in default of 2.2.10) is instead forced via a top-level `buildscript { dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20") ... } }` override, exactly as Google's migration doc recommends. `org.jetbrains.kotlin.plugin.compose` is unaffected by built-in Kotlin (it only replaces `kotlin-android`) and remains applied for the Compose compiler.
- **No explicit `kotlin { jvmToolchain(17) }`.** That call requires Gradle's toolchain service to locate a literal JDK 17 installation; with no toolchain auto-provisioning configured (and none requested by the plan), this failed with `Cannot find a Java installation... Toolchain download repositories have not been configured`. Since the build's actual JVM (Temurin 21, set via user-level `org.gradle.java.home`) already satisfies "JDK 17+", and `compileOptions { sourceCompatibility/targetCompatibility = VERSION_17 }` already sets the Java bytecode target, the toolchain block was removed rather than wiring up auto-provisioning.
- **JDK 21 and the Android SDK cmdline-tools were installed via direct zip download + extract**, not `winget install` for the JDK. The winget/MSI path launched an interactive installer dialog that hung indefinitely waiting for user input in this non-interactive sandbox (and left an orphaned, un-killable elevated `msiexec` process blocking subsequent installs). Downloading the official Temurin 21 Windows x64 zip and the Android `commandlinetools-win` zip directly to user-writable paths (`~/.jdks/jdk-21.0.12+8`, `%LOCALAPPDATA%\Android\Sdk`) avoided the MSI/elevation path entirely and is fully reproducible.
- **`org.gradle.java.home` was written to the user-level `~/.gradle/gradle.properties`** (outside the repo, never committed), exactly as the plan specified — the project's own `gradle.properties` has no JDK path in it.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Gradle wrapper version corrected from 9.1.x to 9.4.1**
- **Found during:** Task 1, first `./gradlew :app:assembleDebug` attempt
- **Issue:** AGP 9.2.0 refused to apply under Gradle 9.1.0 (`Minimum supported Gradle version is 9.4.1`) — a direct contradiction of STACK.md's stated "Gradle >= 9.1.0" compatibility claim
- **Fix:** Re-ran `gradlew wrapper --gradle-version 9.4.1`, regenerating `gradle/wrapper/gradle-wrapper.properties`
- **Files modified:** `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`
- **Verification:** `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest` both succeed
- **Committed in:** `bcf87f2` (Task 1 commit)

**2. [Rule 3 - Blocking] Removed the `org.jetbrains.kotlin.android` plugin (AGP 9 built-in Kotlin conflict)**
- **Found during:** Task 1, build failure applying `org.jetbrains.kotlin.android`
- **Issue:** `Failed to apply plugin 'org.jetbrains.kotlin.android' ... no longer required for Kotlin support since AGP 9.0` — the plan's plugin list predates AGP 9's built-in Kotlin support
- **Fix:** Removed the plugin from `app/build.gradle.kts`, the top-level `build.gradle.kts`, and `gradle/libs.versions.toml`; added a `buildscript` classpath override in the top-level build file to pin Kotlin Gradle Plugin 2.3.20 (CLAUDE.md's exact version) and KSP 2.3.11, per the official AGP migration guide
- **Files modified:** `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`
- **Verification:** Build succeeds; `org.jetbrains.kotlin.plugin.compose` (unaffected by this change) still applies correctly and Compose compiles
- **Committed in:** `bcf87f2` (Task 1 commit)

**3. [Rule 3 - Blocking] Removed `kotlin { jvmToolchain(17) }`**
- **Found during:** Task 1, build failure resolving a JDK 17 toolchain
- **Issue:** `Cannot find a Java installation... matching {languageVersion=17}. Toolchain download repositories have not been configured` — no literal JDK 17 exists on this host (only the newly-installed JDK 21) and toolchain auto-provisioning wasn't configured
- **Fix:** Removed the explicit toolchain request; `compileOptions.sourceCompatibility/targetCompatibility = VERSION_17` already sets the correct bytecode target and is satisfied by the JDK 21 build environment
- **Files modified:** `app/build.gradle.kts`
- **Verification:** `./gradlew :app:assembleDebug` succeeds
- **Committed in:** `bcf87f2` (Task 1 commit)

**4. [Rule 1 - Bug] Removed literal banned-token substrings from doc comments**
- **Found during:** Task 3, running the plan's own grep-gated acceptance criteria
- **Issue:** Explanatory doc comments in `AppDatabase.kt`, `AppContainer.kt`, and `AchievementFactoryApp.kt` that *described* the ban on `fallbackToDestructiveMigration()` / Hilt / Koin / Dagger literally contained those substrings, tripping the same acceptance-criteria greps meant to catch real usage
- **Fix:** Reworded the comments to convey the same intent without the literal banned tokens (e.g. "the destructive-migration-fallback builder options" instead of naming the method; "no DI framework" instead of naming each one)
- **Files modified:** `app/src/main/java/com/achievementfactory/data/AppDatabase.kt`, `app/src/main/java/com/achievementfactory/AppContainer.kt`, `app/src/main/java/com/achievementfactory/AchievementFactoryApp.kt`
- **Verification:** `grep -rn "fallbackToDestructiveMigration" app/src/` and `grep -rni "hilt|dagger|koin" app/src app/build.gradle.kts` both return no matches
- **Committed in:** `bcf87f2` (Task 1) for the App files, `df8e29d` (Task 3) for `AppDatabase.kt`/`AppContainer.kt`

**5. [Rule 1 - Bug] Removed a `.gitignore` comment that literally contained "schemas"**
- **Found during:** Task 1, running the plan's `.gitignore` acceptance criteria
- **Issue:** A comment explaining `app/schemas/` is intentionally not ignored contained the substring "schemas", which tripped `grep -c "schemas" .gitignore` returning 1 instead of the required 0
- **Fix:** Removed the comment; the absence of a `schemas` ignore rule already achieves the intent
- **Files modified:** `.gitignore`
- **Verification:** `grep -c "schemas" .gitignore` returns `0`
- **Committed in:** `bcf87f2` (Task 1 commit)

---

**Total deviations:** 5 auto-fixed (2 Rule 1 bug fixes on doc-comment/gitignore wording, 3 Rule 1/3 build-tooling corrections)
**Impact on plan:** All five were required for the plan's own stated acceptance criteria to pass and for the build to succeed at all. No scope creep — no functionality was added or removed beyond what Task 1–3 specified; only build-tool version/plugin realities (AGP 9's built-in Kotlin, its real Gradle minimum, and JDK toolchain resolution) were corrected against the actual tool behavior, and doc-comment wording was adjusted to not self-defeat the grep gates it was documenting.

## Issues Encountered

- **`winget install` for Temurin 21 JDK hung indefinitely.** The MSI installer opened an interactive GUI dialog with no way to respond in this non-interactive sandboxed session, and the underlying `msiexec` process could not be force-killed (access denied — running under an elevated token). Recovered by abandoning the winget/MSI path and downloading+extracting the official Temurin 21 Windows x64 **zip** distribution directly to `~/.jdks/jdk-21.0.12+8` — no elevation, no installer, fully scriptable. Android SDK command-line tools were installed the same way (zip download + extract to `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest`), then `sdkmanager --licenses` and `sdkmanager platform-tools "platforms;android-36" "build-tools;36.0.0"` ran normally from that non-elevated location.
- **KSP version pin required live verification.** STACK.md flagged that the exact KSP tag needed to be resolved at implementation time. Checked `github.com/google/ksp/releases` and Maven Central directly: the latest available tag is `2.3.11` (no `2.3.20`-named tag exists — KSP's own versioning has decoupled from Kotlin's bare-version numbering, and `2.3.11`'s changelog already confirms forward-compatibility work for Kotlin 2.4.0), so `2.3.11` was pinned as the closest, verified-compatible release.
- **`androidx.test:core` (for `ApplicationProvider` in the Robolectric test) was not in the plan's dependency list** and had to be added — a small, necessary addition to make `AppDatabaseTest` compile, not a scope change.

## User Setup Required

None — no external service configuration required. Local build toolchain (JDK 21, Android SDK, Gradle 9.4.1) was provisioned entirely by the executor onto this machine at `~/.jdks/jdk-21.0.12+8` and `%LOCALAPPDATA%\Android\Sdk`; `org.gradle.java.home` is recorded in the user-level (non-repo) `~/.gradle/gradle.properties`, not committed.

## Next Phase Readiness

- The app builds, installs (`assembleDebug` produces `app-debug.apk`), and all 15 unit tests pass (10 period-engine, 5 Room/Robolectric)
- `PeriodEngine` and Room schema v1 are stable, tested foundations — plan 01-02 can now read real data through a repository `Flow` and render it in Compose
- No blockers. One thing for the next plan/session to be aware of: this session's toolchain (JDK 21 at `~/.jdks`, Android SDK at `%LOCALAPPDATA%\Android\Sdk`, Gradle 9.4.1 wrapper) lives only on this machine — a fresh clone on another machine needs the same manual JDK/SDK provisioning steps (`local.properties` and `~/.gradle/gradle.properties` are both gitignored/user-level by design, per DATA constraints)

---
*Phase: 01-daily-achievements-core-loop-foundation*
*Completed: 2026-08-13*

## Self-Check: PASSED

All 9 created files verified present on disk; all 3 task commits (`bcf87f2`, `c20b9c7`, `df8e29d`) verified present in `git log`.
