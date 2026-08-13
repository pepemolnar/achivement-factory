---
phase: 01-daily-achievements-core-loop-foundation
plan: 08
subsystem: infra
tags: [android, gradle, room, signing, migration-testing, documentation]

# Dependency graph
requires:
  - phase: 01-01
    provides: "Room schema v1, exported+committed schema JSON, AppContainer, the toolchain deviations (Gradle 9.4.1, no kotlin.android plugin, JDK/SDK paths)"
  - phase: 01-07
    provides: "The completed celebration/MotionSpecs/CurrentPeriodProvider stack this plan's CLAUDE.md Conventions section documents"
provides:
  - "A signed, apksigner-verified release APK (app/build/outputs/apk/release/app-release.apk), built from a local gitignored keystore"
  - "APP_DATABASE_VERSION — the single named constant driving both the @Database annotation and SchemaExportTest"
  - "SchemaExportTest — fails if the current version's schema is missing OR present-but-uncommitted (see Deviations for why a bare existence check was insufficient)"
  - "MigrationTest — a MigrationTestHelper harness anchored on the version-1 export, plus the written rule for every future version bump"
  - "docs/RELEASE.md — keystore generation, build, verify, install, and the standing migration rule"
  - "CLAUDE.md Conventions and Architecture sections, replacing the Phase 1 placeholders, for Phases 2-5 to inherit"
affects: ["phase-2", "phase-3", "phase-4", "phase-5"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Release signing config reads a gitignored keystore.properties via java.util.Properties, guarded by a file-existence check — a clean checkout without a keystore still configures and builds (unsigned)"
    - "SchemaExportTest checks git-tracking (git ls-files --error-unmatch), not just File.exists() — the androidx.room Gradle plugin re-exports the schema JSON as a side effect of any compile, so a bare existence check can never fail via 'bump version, forget to commit'"
    - "MigrationTest populates a version-1 database via MigrationTestHelper.createDatabase, closes it, reopens through Room.databaseBuilder, and asserts every row survived — the pattern every future Migration(n, n+1) test extends"

key-files:
  created:
    - docs/RELEASE.md
    - app/src/test/java/com/achievementfactory/data/SchemaExportTest.kt
    - app/src/androidTest/java/com/achievementfactory/data/MigrationTest.kt
    - keystore.properties (gitignored, not committed)
  modified:
    - app/build.gradle.kts
    - app/src/main/java/com/achievementfactory/data/AppDatabase.kt
    - CLAUDE.md

key-decisions:
  - "SchemaExportTest asserts git-tracking, not just filesystem existence — see Deviations for the empirical discovery that forced this"
  - "keyPassword equals storePassword in keystore.properties — keytool's default PKCS12 keystore format doesn't support them differing; documented in docs/RELEASE.md so the owner doesn't try to separate them on a future regeneration"
  - "Release keystore stored outside the repository at a fixed user-writable path (~/.android-keystores/achievement-factory/release.jks on this machine), referenced by an absolute Windows path in keystore.properties"

requirements-completed: [DATA-03, DATA-05]

# Metrics
duration: ~35min
completed: 2026-08-13
---

# Phase 1 Plan 8: Release Signing, Migration Harness & Recorded Conventions Summary

**A signed, apksigner-verified release APK now builds from a local gitignored keystore; SchemaExportTest and a MigrationTestHelper harness make Room's migration discipline a red-test guarantee instead of a memory; and CLAUDE.md's Conventions/Architecture sections record everything Phase 1 actually built for Phases 2-5 to inherit.**

## Performance

- **Duration:** ~35 min
- **Completed:** 2026-08-13T13:27:58+02:00 (approx.)
- **Tasks:** 2 / 2
- **Files modified:** 7 (4 created, 3 modified) — plus `keystore.properties`, gitignored and never committed

## Accomplishments

- Generated a local RSA-2048, 10000-day-validity release keystore with `keytool`, stored outside the repository; `keystore.properties` (storeFile/storePassword/keyAlias/keyPassword) is gitignored and confirmed with `git check-ignore`. `app/build.gradle.kts` loads it via a file-existence-guarded `java.util.Properties` read, so `signingConfigs["release"]` only exists — and the release build type only signs — when the file is present; a clean checkout without a keystore still configures and produces an unsigned APK rather than failing at configuration time.
- `./gradlew :app:assembleRelease` builds `app/build/outputs/apk/release/app-release.apk`, verified with `apksigner verify` (v2 signature scheme, `Verifies`, exit 0). `isMinifyEnabled = false` for release. No `.aab` anywhere in the project; no Play App Signing configuration.
- `docs/RELEASE.md` documents one-time keystore generation (including the PKCS12 store/key-password-must-match caveat, discovered empirically from `keytool`'s own warning), the `keystore.properties` shape, `assembleRelease`, `apksigner verify`, `adb install -r`, and the standing rule that every schema change from the first real install onward requires a real `Migration` plus a migration test.
- `AppDatabase.kt` now exposes `const val APP_DATABASE_VERSION = 1`, driving both the `@Database` annotation and `SchemaExportTest`.
- `SchemaExportTest` (plain JVM test, three cases) asserts the current version's schema file exists, parses with a matching `"version"` field, and — the case that actually makes DATA-03's guarantee real — is tracked by git. See Deviations for why the third check was necessary.
- `MigrationTest` (`androidTest`) declares a `MigrationTestHelper` rule anchored on `AppDatabase::class.java`, creates a version-1 database via `helper.createDatabase(TEST_DB, APP_DATABASE_VERSION)`, populates one achievement row and one progress row with raw SQL, closes it, reopens through `Room.databaseBuilder(...)`, and asserts both rows survived with their exact values intact. A large comment block states the four-step rule for every future version bump (write the `Migration`, wire it into `AppContainer`'s builder, commit the new schema JSON, extend this test against a *populated* prior-version database).
- `grep -rn "fallbackToDestructiveMigration" app/ docs/` returns no real usage anywhere (only prohibitions in prose, phrased without the literal token per this phase's established convention).
- CLAUDE.md's **Conventions** section now documents: package layout, ViewModel/UiState shape, the state-vs-event Channel rule, manual `AppContainer` DI, repository/entity layering, period rules, atomic counter mutation, the migration rule, the `MotionSpecs`-centralized animation convention, and the testing convention (JUnit4/Robolectric/Turbine/MockK/androidTest split). The **Architecture** section documents the layer diagram (`ui` → `domain`/`data` → Room, `period/` depending on nothing), the two entities, the streak-cache-not-source-of-truth note, and the navigation route shape. The **Developer Profile** section (generated, GSD-managed) is untouched.
- `./gradlew clean :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:assembleDebug` passes clean: 81/81 tests, 0 failures.

## Task Commits

1. **Task 1: Sign and build the release APK** - `530f6f7` (feat)
2. **Task 2: Lock in migration discipline and record the conventions** - `e3b0de4` (feat)

## Files Created/Modified

- `app/build.gradle.kts` - Adds `signingConfigs["release"]` loaded from gitignored `keystore.properties`, guarded by a file-existence check; release build type signs only when present
- `keystore.properties` - Gitignored; storeFile/storePassword/keyAlias/keyPassword for the local release keystore. **Not committed.**
- `docs/RELEASE.md` - Keystore generation, build, verify, install, and the standing migration rule
- `app/src/main/java/com/achievementfactory/data/AppDatabase.kt` - Adds `const val APP_DATABASE_VERSION = 1`, used by the `@Database` annotation
- `app/src/test/java/com/achievementfactory/data/SchemaExportTest.kt` - Three-case JVM test: exists, matching version, committed to git
- `app/src/androidTest/java/com/achievementfactory/data/MigrationTest.kt` - `MigrationTestHelper` harness + the written rule for future version bumps
- `CLAUDE.md` - Conventions and Architecture sections replaced with what Phase 1 actually built

## Decisions Made

- **`SchemaExportTest` checks `git ls-files` tracking, not just `File.exists()`.** See Deviations — a bare existence check cannot fail the way the plan's acceptance criteria requires, because the Room Gradle plugin regenerates the schema JSON as a side effect of any build that compiles this module.
- **`keyPassword` equals `storePassword`** in the generated `keystore.properties`, because `keytool`'s default PKCS12 keystore format does not support them differing (it warns and silently reuses the store password). Documented in `docs/RELEASE.md` so a future keystore regeneration doesn't waste time trying to separate them.
- **The release keystore lives outside the repository** at a fixed path on this machine (`~/.android-keystores/achievement-factory/release.jks`), referenced by an absolute Windows-style path in `keystore.properties`. A machine-specific detail, consistent with this phase's other toolchain-path decisions (01-01).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `SchemaExportTest`'s literal design (existence + version check only) could not satisfy its own stated acceptance-criterion behavior**
- **Found during:** Task 2, verifying the plan's own acceptance criterion — "temporarily bumping `APP_DATABASE_VERSION` to 2 without adding a schema file makes `SchemaExportTest` fail"
- **Issue:** Bumping `APP_DATABASE_VERSION` to 2 and running `./gradlew :app:testDebugUnitTest` did **not** fail a plain-existence `SchemaExportTest`, because the `androidx.room` Gradle plugin (`room { schemaDirectory(...) }`, applied since plan 01-01) re-exports the schema JSON as a side effect of the `kspDebugKotlin`/`kspDebugUnitTestKotlin` tasks that the test task itself depends on — a fresh `schemas/.../2.json` appeared on disk *before the test body ran*, purely from recompiling, with nothing manually added or committed. This is a genuine toolchain behavior not stated in the plan's action text (which only specified an existence + version check), and it means the literal design as written can never fail via "bump version, forget to commit" — the real risk DATA-03 exists to catch.
- **Fix:** Added a third test case, `exportedSchemaFile_isCommittedToVersionControl`, that shells out to `git ls-files --error-unmatch <file>` (via `ProcessBuilder`, `cwd` = the schema file's directory) and fails if the file isn't tracked. Verified end to end: bumped `APP_DATABASE_VERSION` to 2 without running `git add`, confirmed the new test failed (`SchemaExportTest > exportedSchemaFile_isCommittedToVersionControl FAILED`), then reverted the bump, deleted the stray untracked `2.json`, and confirmed the full suite passes again (81/81).
- **Files modified:** `app/src/test/java/com/achievementfactory/data/SchemaExportTest.kt`
- **Verification:** Manual bump/revert cycle described above; `./gradlew clean :app:testDebugUnitTest` — 81/81 pass at version 1
- **Committed in:** `e3b0de4` (Task 2 commit)

**2. [Rule 3 - Blocking] `keystore.properties`'s `storeFile` needed a Windows-native path, not a Git-Bash POSIX-style path**
- **Found during:** Task 1, first attempt at writing `keystore.properties` with the raw `/c/Users/...` path Git Bash's own shell produces
- **Issue:** Gradle's `file(...)` resolution (JVM-level, not shell-level) does not understand Git Bash's `/c/Users/...` POSIX-style path remapping — that path form only exists inside the Bash tool's own process, and would silently fail to resolve once read by Gradle's Kotlin DSL.
- **Fix:** Used `cygpath -m` to convert to the Windows-native forward-slash form (`C:/Users/...`) before writing it into `keystore.properties`, which both `keytool`/Windows and Gradle's `file()` API accept correctly.
- **Files modified:** `keystore.properties` (gitignored, not committed)
- **Verification:** `./gradlew :app:assembleRelease` locates and signs with the keystore successfully; `apksigner verify` confirms the resulting APK's signature
- **Committed in:** N/A — file is gitignored by design

---

**Total deviations:** 2 auto-fixed (1 bug fix required by the plan's own stated behavior requirement, 1 blocking path-format fix)
**Impact on plan:** Deviation 1 is a real strengthening of DATA-03's guarantee, not scope creep — the plan's stated purpose ("turn 'stopped being committed' into a red test") only holds with the git-tracking check in place; a bare existence check would have silently satisfied the plan's file list while failing its actual intent. Deviation 2 was required for Task 1's build to succeed at all in this Windows/Git-Bash environment.

## Issues Encountered

None beyond the deviations above.

## Known Stubs

None. Both artifacts this plan produces — the signed release build and the migration/schema-export guard — are fully wired and verified: `assembleRelease` produces a real signed, `apksigner`-verified APK from real signing material, and `SchemaExportTest`/`MigrationTest` run against the real committed schema and the real `AppDatabase`, not placeholders.

## User Setup Required

**The owner must generate their own release keystore on whichever machine they intend to build releases from**, following `docs/RELEASE.md`'s "One-time: generate the release keystore" section. This session generated a keystore on this sandboxed executor's own machine (stored at `~/.android-keystores/achievement-factory/release.jks`, referenced from the gitignored `keystore.properties`) purely to prove the signing config and `assembleRelease`/`apksigner verify` path work end to end — it is **not** the keystore the owner should use for their real, permanent app identity. Before the owner treats any release as "the real one" they intend to keep signing future updates with, they should regenerate their own keystore (or explicitly decide to keep this session's) and store its passwords somewhere durable outside this repository; losing the release signing key means any future update can only be installed as a fresh reinstall (acceptable per this project's threat model, T-01-31, since there is only ever one device).

**The plan's `<human-check>` steps could not be completed in this sandboxed session — no device or emulator is attached, consistent with every prior plan in this phase:**
- Task 1's human-check: `adb install -r` the release APK onto the owner's phone, create/complete an achievement, confirm the celebration plays and streak reads 1, reboot, confirm data survives (DATA-04 on real hardware, DATA-05).
- Task 2's human-check: `./gradlew :app:connectedDebugAndroidTest` on the owner's device, confirming `MigrationTest` and the celebration instrumented suite (from plan 01-07) both pass.

In their place, this session verified everything reachable without a device:
- `./gradlew :app:assembleRelease` succeeds and the resulting APK is `apksigner`-verified (v2 scheme, exit 0) — proves the signed artifact itself is correct, independent of installing it.
- `./gradlew :app:assembleDebugAndroidTest` compiles `MigrationTest` (and the pre-existing `CelebrationInstrumentedTest` from plan 01-07) successfully — proves the migration harness is syntactically and structurally correct against the real `AppDatabase`/`AppDatabase::class.java`, even though its actual execution (which requires `MigrationTestHelper`'s instrumentation runtime) needs a device.
- `./gradlew :app:testDebugUnitTest` — 81/81 tests pass, including `SchemaExportTest`'s three cases, which directly and empirically prove the DATA-03 guard fires correctly on both the "missing" and "uncommitted" failure modes (see Deviations for the manual bump/revert verification performed).

All on-device verification from this plan (and every prior plan in this phase) remains open pending the project owner's own hardware.

## Next Phase Readiness

- DATA-03 (migration discipline enforced by an automated test) and DATA-05 (signed, installable release build) are both real and automatically verified to the extent this sandboxed environment allows — a real device pass is the only remaining step, and nothing in the automated gates suggests a problem.
- `MigrationTest` is deliberately positioned as the harness Phase 3's first real schema change (adding edit-related columns, if any) extends rather than invents — the four-step rule is written directly into its doc comment.
- CLAUDE.md's Conventions and Architecture sections are now populated for Phases 2-5 to read before they plan or implement, closing Phase 1's other standing obligation (SKELETON.md, CONTEXT.md's Established Patterns note).
- **This is the last plan in Phase 1.** All eight plans (01-01 through 01-08) are complete; Phase 1's success criteria (a working, installable daily-achievements app with a real celebration, real streaks, and migration-safe persistence) are met to the extent verifiable without a physical device. No blockers.

---
*Phase: 01-daily-achievements-core-loop-foundation*
*Completed: 2026-08-13*
