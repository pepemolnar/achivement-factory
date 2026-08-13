---
phase: 01-daily-achievements-core-loop-foundation
plan: 02
subsystem: ui
tags: [android, kotlin, jetpack-compose, room, stateflow, navigation-compose, material3, mockk, turbine]

# Dependency graph
requires:
  - phase: 01-01
    provides: "Room schema v1 (AchievementEntity, ProgressEntity), the civil-date PeriodEngine, and AppContainer as the sole DI mechanism"
provides:
  - "AchievementRepository — the single read/write entry point between ViewModels and the DAOs, exposing observeDaily() and create()"
  - "DailyViewModel/DailyUiState — the StateFlow<UiState>-per-screen ViewModel shape every later screen copies"
  - "DailyScreen, AchievementCard, ProgressRing, AccentPalette — the resting card visual system (neutral body, accent-only ring/glyph)"
  - "AppNavHost/Routes — the single NavHost, currently hosting only the Daily destination"
affects: ["01-03", "01-04", "01-05", "01-06", "01-07", "01-08", "phase-2", "phase-3"]

# Tech tracking
tech-stack:
  added: [material-icons-extended]
  patterns:
    - "Repository maps Room entities to a single domain model (DailyAchievement) before crossing into ui/ — entities never cross that boundary"
    - "ViewModel pattern: StateFlow<UiState> built by map/catch/stateIn(WhileSubscribed(5_000)), Loading initial value, manual-DI Factory via viewModelFactory { initializer { ... APPLICATION_KEY ... } }"
    - "UiState sealed interface (Loading/Content/Error) never carries celebration/one-shot-event fields — that separation is required for the CELEB-04 channel in plan 01-07"
    - "AccentSwatch (data-layer enum) is shared vocabulary between data/ and ui/ — an explicit exception to the entity-never-crosses-boundary rule, since it carries no persistence shape"
    - "Progress rendering is a plain Float on ProgressRing's contract — animation is layered on top in later plans without changing the composable signature"

key-files:
  created:
    - app/src/main/java/com/achievementfactory/domain/model/DailyAchievement.kt
    - app/src/main/java/com/achievementfactory/data/achievement/AchievementRepository.kt
    - app/src/main/java/com/achievementfactory/ui/daily/DailyUiState.kt
    - app/src/main/java/com/achievementfactory/ui/daily/DailyViewModel.kt
    - app/src/main/java/com/achievementfactory/ui/daily/DailyScreen.kt
    - app/src/main/java/com/achievementfactory/ui/achievement/AchievementCard.kt
    - app/src/main/java/com/achievementfactory/ui/achievement/ProgressRing.kt
    - app/src/main/java/com/achievementfactory/ui/theme/AccentPalette.kt
    - app/src/main/java/com/achievementfactory/ui/nav/AppNavHost.kt
    - app/src/test/java/com/achievementfactory/data/AchievementRepositoryTest.kt
    - app/src/test/java/com/achievementfactory/ui/daily/DailyViewModelTest.kt
  modified:
    - app/src/main/java/com/achievementfactory/AppContainer.kt
    - app/src/main/java/com/achievementfactory/ui/MainActivity.kt
    - app/build.gradle.kts
    - gradle/libs.versions.toml

key-decisions:
  - "Added androidx.compose.material:material-icons-extended (BOM-managed, no explicit version) — UI-SPEC mandates Material Symbols for the FAB's Add icon, and no icon-bearing dependency was already on the classpath from plan 01-01"
  - "DailyViewModelTest uses MockK to mock the concrete AchievementRepository class rather than introducing a repository interface — CLAUDE.md's own stack guidance picks MockK specifically because it mocks final Kotlin classes with no extra config, so no interface extraction was needed for testability"
  - "AchievementRepository.observeDaily() resolves today's period key once, at flow-build time (not re-evaluated per combine emission) — matches ARCHITECTURE.md's 'period-key resolution happens once per read, in Kotlin' and PeriodDao query construction, which cannot itself take a dynamically-changing bound parameter mid-subscription; foreground rollover across midnight is out of scope for this plan (PERIOD-04 lands with the increment/undo work)"

patterns-established:
  - "Doc comments describing grep-gated banned substrings must avoid the literal substring itself (celebrat*, flame) — same trap plan 01-01 documented for fallbackToDestructiveMigration/Hilt/Koin/Dagger; reworded on sight in both files this plan touched"

requirements-completed: [NAV-04, DATA-01]

# Metrics
duration: ~25min
completed: 2026-08-13
---

# Phase 1 Plan 2: Daily Tab Read Path — Repository, ViewModel, Cards & Empty State Summary

**Room rows reach the screen through a `Flow<List<DailyAchievement>>` → `StateFlow<DailyUiState>` pipeline with no manual refresh, rendered as neutral Material3 cards with an accent-only progress ring and a two-line NAV-04 empty state.**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-08-13T08:45:00Z (approx.)
- **Completed:** 2026-08-13T08:56:48Z
- **Tasks:** 2 / 2
- **Files modified:** 15 (11 created, 4 modified)

## Accomplishments

- `AchievementRepository.observeDaily()` reactively joins `achievementDao.observeActive("DAILY")` with `progressDao.observeForPeriod(todayKey)`, resolving today's period key once in Kotlin and never in SQL; achievements with no progress row for today report `count = 0, isDone = false`, and a progress row keyed to yesterday never leaks into today's numbers
- `DailyViewModel` exposes `StateFlow<DailyUiState>` via `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loading)`, splitting achievements into `outstanding`/`done` and mapping upstream failures to the exact UI-SPEC error copy — this is the ViewModel shape every later screen-level ViewModel in the project will copy
- `DailyUiState` carries no celebration-shaped field by construction (grep-gated), which is what keeps the one-shot completion-event channel possible in plan 01-07
- The Daily tab renders live: `AccentPalette` (all ten UI-SPEC swatches, light+dark), `ProgressRing` (Canvas-drawn accent ring, D-09), `AchievementCard`/`AchievementGlyph` (neutral body, accent confined to ring+glyph per D-10, first-letter fallback per D-18), and `DailyScreen` (Loading/Error/empty/list states, Done-section divider per D-05, exact NAV-04/D-20 empty-state copy)
- `AppNavHost` replaces `MainActivity`'s static Scaffold with a real single-destination `NavHost`, wiring `DailyViewModel.Factory` (manual DI via `viewModelFactory { initializer { ... } }` reaching `AppContainer` through `APPLICATION_KEY`)
- `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest` both pass; all plan-specified behaviors are covered by `AchievementRepositoryTest` (Robolectric, fixed clock) and `DailyViewModelTest` (plain JVM, MockK + Turbine)

## Task Commits

Each task was committed atomically:

1. **Task 1: Wire Room to a StateFlow through the repository and the Daily ViewModel** - `9394f68` (feat)
2. **Task 2: Render the Daily tab — cards, Done section and the empty state** - `c95c111` (feat)

**Plan metadata:** _pending — recorded after this summary is written_

## Files Created/Modified

- `app/src/main/java/com/achievementfactory/domain/model/DailyAchievement.kt` - The one domain model that crosses into `ui/`; entities never do
- `app/src/main/java/com/achievementfactory/data/achievement/AchievementRepository.kt` - `observeDaily()` (combine-joined Flow) and `create()` (zeroed streaks, null tier thresholds)
- `app/src/main/java/com/achievementfactory/AppContainer.kt` - Gains a lazily-built `achievementRepository`
- `app/src/main/java/com/achievementfactory/ui/daily/DailyUiState.kt` - Sealed `Loading`/`Content`/`Error`, `Content.isEmpty` computed once
- `app/src/main/java/com/achievementfactory/ui/daily/DailyViewModel.kt` - `StateFlow<DailyUiState>` + manual-DI `Factory`
- `app/src/main/java/com/achievementfactory/ui/daily/DailyScreen.kt` - Scaffold + FAB, Loading/Error/empty/list bodies, Done-section divider
- `app/src/main/java/com/achievementfactory/ui/achievement/AchievementCard.kt` - Resting card + `AchievementGlyph` (emoji-or-first-letter fallback)
- `app/src/main/java/com/achievementfactory/ui/achievement/ProgressRing.kt` - `Canvas`/`drawArc` accent ring, plain `Float` contract
- `app/src/main/java/com/achievementfactory/ui/theme/AccentPalette.kt` - Ten `AccentSwatch` → light/dark `Color` pairs
- `app/src/main/java/com/achievementfactory/ui/nav/AppNavHost.kt` - `Routes` object + single `NavHost` destination
- `app/src/main/java/com/achievementfactory/ui/MainActivity.kt` - Now renders `AppNavHost` instead of a static Scaffold
- `app/build.gradle.kts`, `gradle/libs.versions.toml` - Added `material-icons-extended` for the FAB's Material Symbols "Add" icon
- `app/src/test/java/com/achievementfactory/data/AchievementRepositoryTest.kt` - 6 Robolectric tests covering every behavior in the plan's list
- `app/src/test/java/com/achievementfactory/ui/daily/DailyViewModelTest.kt` - 3 plain-JVM tests (MockK + Turbine): Loading-first, done/outstanding split, error mapping

## Decisions Made

- **Added `material-icons-extended`** (no explicit version, managed by the Compose BOM already pinned in 01-01) — required by 01-UI-SPEC.md's Design System table for the FAB's Material Symbols "Add" icon; nothing from plan 01-01 pulled `material-icons-core`/`extended` transitively, confirmed by inspecting the Gradle module cache before adding it.
- **Mocked the concrete `AchievementRepository` class directly with MockK** rather than introducing a repository interface for testability — this is exactly the reason CLAUDE.md's stack pins MockK over plain Mockito (mocks final Kotlin classes with zero extra Gradle config), so the plan's literal `DailyViewModel(repository: AchievementRepository)` signature could stay as specified without an unrequested interface extraction.
- **`observeDaily()` resolves today's period key once, at flow-build time**, not re-evaluated inside the `combine` lambda per emission — this is the only place the key *can* be resolved, since it must be bound as a parameter to `progressDao.observeForPeriod(key)` when that Flow-returning query is constructed; a Room `@Query` cannot re-bind its own parameter mid-subscription. Foreground midnight rollover (PERIOD-04) is explicitly out of this plan's scope per 01-CONTEXT.md's requirement mapping.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Reworded `DailyUiState.kt`'s doc comment to remove the literal substring "celebrat"**
- **Found during:** Task 1, running the plan's own grep-gated acceptance criteria
- **Issue:** The doc comment explaining *why* `Content` carries no celebration-shaped field literally contained the word "celebration", tripping the same acceptance-criteria grep meant to catch a real `shouldAnimate`/`showConfetti`-style field — an identical trap to the one plan 01-01 documented for `fallbackToDestructiveMigration`/Hilt/Koin/Dagger doc comments.
- **Fix:** Reworded to "a 'should animate'/'show confetti' style field" and "one-shot completion-event channel", conveying the same intent without the banned substring.
- **Files modified:** `app/src/main/java/com/achievementfactory/ui/daily/DailyUiState.kt`
- **Verification:** `grep -riE "celebrat|shouldAnimate|showConfetti" app/src/main/java/com/achievementfactory/ui/daily/DailyUiState.kt` returns no matches
- **Committed in:** `9394f68` (Task 1 commit)

**2. [Rule 1 - Bug] Reworded `AchievementCard.kt`'s doc comment to remove the literal substring "flame"**
- **Found during:** Task 2, running the plan's own grep-gated acceptance criteria
- **Issue:** The doc comment noting that the streak badge arrives in plan 01-05 used the word "flame" (from 01-CONTEXT.md's own D-07 language), tripping `grep -rn "flame\|Flame"` which is meant to catch a real badge implementation landing early.
- **Fix:** Reworded to "that indicator arrives, hidden-at-zero, in plan 01-05".
- **Files modified:** `app/src/main/java/com/achievementfactory/ui/achievement/AchievementCard.kt`
- **Verification:** `grep -rn "flame\|Flame" app/src/main/java/com/achievementfactory/ui/achievement/AchievementCard.kt` returns no matches
- **Committed in:** `c95c111` (Task 2 commit)

**3. [Rule 3 - Blocking] Added `material-icons-extended` dependency**
- **Found during:** Task 2, writing `DailyScreen.kt`'s FAB
- **Issue:** 01-UI-SPEC.md's Design System table mandates `androidx.compose.material.icons.extended` (Material Symbols) for UI chrome including the FAB's Add icon, but no icon-bearing artifact existed on the classpath after plan 01-01 (confirmed via the Gradle module cache — only `material-ripple` was present under `androidx.compose.material`)
- **Fix:** Added `compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }` to the version catalog (BOM-managed, no explicit version) and `implementation(libs.compose.material.icons.extended)` to `app/build.gradle.kts`
- **Files modified:** `gradle/libs.versions.toml`, `app/build.gradle.kts`
- **Verification:** `./gradlew :app:assembleDebug` succeeds; this is an official first-party AndroidX artifact already resolvable from the same repositories every other Compose dependency in this project uses, not a third-party/unverified package
- **Committed in:** `c95c111` (Task 2 commit)

---

**Total deviations:** 3 auto-fixed (2 Rule 1 doc-comment wording fixes, 1 Rule 3 missing-dependency addition)
**Impact on plan:** All three were required for the plan's own stated acceptance criteria to pass or for the UI-SPEC's explicit icon requirement to be satisfiable at all. No scope creep beyond what Task 1–2 specified.

## Issues Encountered

None beyond the deviations above.

## Known Stubs

- **`DailyScreen`'s `onCreateClick` parameter is wired to a no-op lambda `{}` in `AppNavHost.kt`.** This is explicitly specified by the plan itself ("`onCreateClick` is wired to a no-op lambda in this plan and connected to the create route in plan 01-03 — the parameter exists now so that wiring is one line then, not a signature change"). Not a hidden gap — documented here per the summary's stub-tracking requirement. Resolved by plan 01-03.

## User Setup Required

None for build/test — no external service configuration required.

**One verification step could not be completed in this sandboxed session:** the plan's `<human-check>` step ("Run `./gradlew :app:installDebug` with the device connected... confirm the Daily screen shows 'No achievements yet'... toggling the system dark theme keeps the text legible") requires a physical/emulated Android device. `adb devices` returned an empty device list in this environment. All automated verification (`assembleDebug`, `testDebugUnitTest`, every acceptance-criteria grep) passed; the on-device visual check is deferred to the project owner's own hardware per DATA-05's "direct install on the owner's device" scope.

## Next Phase Readiness

- The Daily tab's full read path is real and tested: Room → repository → ViewModel → Compose, with no manual refresh path anywhere
- `AchievementCard`, `ProgressRing`, `AccentPalette`, and the `DailyViewModel`/`DailyUiState` shapes are the conventions plans 01-03 through 01-08 (and later phases' Weekly/Monthly tabs) will reuse
- No blockers. Plan 01-03 needs: the create screen (FAB target), wiring `onCreateClick`, and the detail screen route — `AppNavHost`'s `Routes` object is ready to grow additional destinations
- The on-device dark/light-theme visual check from Task 2's human-check step is still open — worth a quick manual pass once a device is available, though nothing in the automated gates suggests a problem

---
*Phase: 01-daily-achievements-core-loop-foundation*
*Completed: 2026-08-13*

## Self-Check: PASSED

All 11 created source files plus this SUMMARY.md verified present on disk; both task commits (`9394f68`, `c95c111`) verified present in `git log`.
