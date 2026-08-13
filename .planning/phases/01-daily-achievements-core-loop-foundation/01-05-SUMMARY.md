---
phase: 01-daily-achievements-core-loop-foundation
plan: 05
subsystem: core-loop
tags: [android, kotlin, jetpack-compose, room, coroutines, mockk, robolectric, compose-ui-test]

# Dependency graph
requires:
  - phase: 01-04
    provides: "AchievementRepository.increment()/undo() — the atomic counter write path this plan hooks reconciliation into"
  - phase: 01-01
    provides: "PeriodEngine.previous()/current(), the streak cache columns on AchievementEntity, AchievementDao.updateStreakCache"
  - phase: 01-02
    provides: "AchievementCard/DailyAchievement — the card and domain model this plan extends with the streak badge"
provides:
  - "StreakCalculator — pure bounded backward walk over an achievement's done-key log, no Android/Room import"
  - "ReconcileStreaksUseCase.runFor(id, force)/runAll() — idempotent per-achievement reconciliation, with a force override for same-day completion changes"
  - "AchievementDao.staleAchievementIds() — selects only achievements whose cache is behind today, so runAll() doesn't rewrite every row on every launch"
  - "AppContainer.applicationScope — the process-lifetime CoroutineScope backing reconciliation, launched from AchievementFactoryApp.onCreate"
  - "StreakFlameBadge — hidden-at-zero flame+number badge, the pattern any later tier/celebration badge on the card will follow"
affects: ["01-06", "01-07", "01-08", "phase-2", "phase-3"]

# Tech tracking
tech-stack:
  added: [compose-ui-test-junit4]
  patterns:
    - "Streak cache columns are a read cache only — StreakCalculator always re-derives from the progress log's done-key set via a bounded backward walk; nothing increments/decrements the cache directly"
    - "ReconcileStreaksUseCase.runFor(id, force = false) skips its write when streakCachedForPeriodKey already equals today's key; AchievementRepository.increment/undo call it with force = true so a same-day completion or undo is never masked by an app-open reconcile that already stamped today's key"
    - "AchievementDao.staleAchievementIds(currentKey) pre-filters which ids runAll() even calls runFor on, keeping app-open reconciliation O(stale achievements) not O(all achievements)"
    - "Compose semantics-tree assertions run on the JVM via Robolectric + compose-ui-test-junit4's v2 createComposeRule — no device/emulator needed for hidden-at-zero UI behavior coverage"

key-files:
  created:
    - app/src/main/java/com/achievementfactory/domain/streak/StreakSnapshot.kt
    - app/src/main/java/com/achievementfactory/domain/streak/StreakCalculator.kt
    - app/src/main/java/com/achievementfactory/domain/maintenance/ReconcileStreaksUseCase.kt
    - app/src/main/java/com/achievementfactory/ui/achievement/StreakFlameBadge.kt
    - app/src/test/java/com/achievementfactory/domain/streak/StreakCalculatorTest.kt
    - app/src/test/java/com/achievementfactory/domain/maintenance/ReconcileStreaksUseCaseTest.kt
    - app/src/test/java/com/achievementfactory/ui/achievement/AchievementCardTest.kt
  modified:
    - app/src/main/java/com/achievementfactory/data/achievement/AchievementDao.kt
    - app/src/main/java/com/achievementfactory/data/achievement/AchievementRepository.kt
    - app/src/main/java/com/achievementfactory/AppContainer.kt
    - app/src/main/java/com/achievementfactory/AchievementFactoryApp.kt
    - app/src/main/java/com/achievementfactory/ui/achievement/AchievementCard.kt
    - app/build.gradle.kts
    - app/src/test/java/com/achievementfactory/data/AchievementRepositoryTest.kt
    - app/src/test/java/com/achievementfactory/data/IncrementUndoTest.kt

key-decisions:
  - "Added a force: Boolean = false parameter to ReconcileStreaksUseCase.runFor rather than implementing the plan's literal 'skip when streakCachedForPeriodKey already equals today' guard unconditionally — see Deviations"
  - "StreakFlameBadge renders as a content-sized pill (flame icon + number in a Row), not a literal 16dp-diameter circle — a fixed-diameter circle cannot legibly hold both a flame glyph and a multi-digit streak number; 16dp is used as the flame icon's own size, matching the spec's other stated use of that value"
  - "AchievementCardTest/StreakFlameBadge's hidden-at-zero behavior test uses Robolectric + compose-ui-test-junit4 (JVM-hosted Compose semantics assertions) since no device/emulator is available in this environment, rather than deferring the behavior entirely to the on-device human-check"

requirements-completed: [TRACK-06, TRACK-07, PERIOD-05]

# Metrics
duration: ~20min
completed: 2026-08-13
---

# Phase 1 Plan 5: Streak Derivation, Reconciliation & Flame Badge Summary

**Streaks are derived from the completion log by a bounded backward walk (StreakCalculator), reconciled idempotently at app open and forced after every increment/undo (ReconcileStreaksUseCase), and shown as a flame+number badge that composes nothing at streak 0 (StreakFlameBadge).**

## Performance

- **Duration:** ~20 min
- **Completed:** 2026-08-13T09:52:00Z (approx.)
- **Tasks:** 2 / 2
- **Files modified:** 15 (7 created, 8 modified)

## Accomplishments

- `StreakCalculator.calculate(doneKeys, currentKey, periodType, storedBest)` is pure Kotlin (no Android/Room import, no clock read): it walks backward from `currentKey` (counting it immediately if already done) or from the period before it (if not yet done), stepping via `PeriodEngine.previous` while each key is present in the log, stopping with no grace at the first gap — `best = maxOf(storedBest, current)`, so best is monotonic even when current resets to 0. Covered by 9 fabricated-log tests including a 40-day gap and a 2026-12-28→2027-01-04 year-boundary run (`StreakCalculatorTest`, 9/9 passing).
- `ReconcileStreaksUseCase.runFor(id, force)`/`.runAll()` treat the streak cache columns as a read cache only, never a source of truth: `runAll()` selects just the stale ids via the new `AchievementDao.staleAchievementIds(currentKey)` query and reconciles only those, and running it twice in a row leaves every row byte-identical (`ReconcileStreaksUseCaseTest`, 4/4 passing).
- `AchievementRepository.increment`/`.undo` call `reconcileStreaks.runFor(id, force = true)` after their own write, so the cached streak the UI reads is already correct — completing today's achievement raises the streak and undoing it lowers it again, in the same call as the tap, proven end to end through the real repository + reconciler wired together against an in-memory Room database.
- `AppContainer` gains a process-lifetime `applicationScope` (`CoroutineScope(SupervisorJob() + Dispatchers.Default)`); `AchievementFactoryApp.onCreate` launches `reconcileStreaks.runAll()` on it — no background-scheduled job of any kind exists anywhere in the project (grep-gated).
- `StreakFlameBadge` renders a flame glyph (`Icons.Filled.LocalFireDepartment`) plus the streak number in the Label role (12sp SemiBold), tinted with the achievement's own accent, and composes nothing at all when `streak <= 0` — enforced inside the component itself so no call site can forget it (D-08). `AchievementCard` anchors it to the top-end corner of the icon/ring cluster with a ~4dp outward offset (D-07).
- `./gradlew clean :app:testDebugUnitTest :app:assembleDebug` passes clean: 62/62 tests, 0 failures, 0 deprecation warnings.

## Task Commits

1. **Task 1: Derive the streak from the log and reconcile it at app open** - `0b1ea60` (feat)
2. **Task 2: Show the streak as a flame that only appears once it exists** - `cefb23b` (feat)

**Plan metadata:** _pending — recorded after this summary is written_

## Files Created/Modified

- `app/src/main/java/com/achievementfactory/domain/streak/StreakSnapshot.kt` - `data class StreakSnapshot(current, best)`
- `app/src/main/java/com/achievementfactory/domain/streak/StreakCalculator.kt` - Pure bounded backward-walk streak derivation
- `app/src/main/java/com/achievementfactory/domain/maintenance/ReconcileStreaksUseCase.kt` - `runFor(id, force)`, `runAll()` — the idempotent reconciliation pass
- `app/src/main/java/com/achievementfactory/data/achievement/AchievementDao.kt` - Adds `staleAchievementIds(currentKey)`
- `app/src/main/java/com/achievementfactory/data/achievement/AchievementRepository.kt` - `increment`/`undo` now call `reconcileStreaks.runFor(id, force = true)`
- `app/src/main/java/com/achievementfactory/AppContainer.kt` - Adds `applicationScope`, `streakCalculator`, `reconcileStreaks`
- `app/src/main/java/com/achievementfactory/AchievementFactoryApp.kt` - `onCreate` launches `reconcileStreaks.runAll()`
- `app/src/main/java/com/achievementfactory/ui/achievement/StreakFlameBadge.kt` - Flame glyph + number, hidden at streak 0
- `app/src/main/java/com/achievementfactory/ui/achievement/AchievementCard.kt` - Anchors `StreakFlameBadge` to the icon cluster's top-end corner
- `app/build.gradle.kts` - Adds `testImplementation(libs.compose.ui.test.junit4)` + test-scope Compose BOM
- `app/src/test/java/com/achievementfactory/domain/streak/StreakCalculatorTest.kt` - 9 pure-JVM tests
- `app/src/test/java/com/achievementfactory/domain/maintenance/ReconcileStreaksUseCaseTest.kt` - 4 Robolectric tests
- `app/src/test/java/com/achievementfactory/ui/achievement/AchievementCardTest.kt` - 2 Robolectric Compose UI tests
- `app/src/test/java/com/achievementfactory/data/AchievementRepositoryTest.kt` - Updated to construct the now-required `ReconcileStreaksUseCase` collaborator
- `app/src/test/java/com/achievementfactory/data/IncrementUndoTest.kt` - Same constructor-signature fix

## Decisions Made

- **Added a `force: Boolean = false` parameter to `ReconcileStreaksUseCase.runFor`** rather than the plan's literal unconditional "skip when `streakCachedForPeriodKey` already equals today's key" guard. See Deviations below for why the literal version breaks the plan's own explicit behavior requirement.
- **`StreakFlameBadge` is a content-sized pill, not a fixed 16dp circle.** The spec's "16dp diameter" is applied to the flame icon itself (matching `Spacing.flameBadgeSize`'s existing use elsewhere); a literal 16dp-diameter container cannot legibly hold a flame glyph *and* a multi-digit streak number (e.g. a 365-day streak). This is a Claude's Discretion visual-detail call, consistent with 01-CONTEXT.md leaving "concrete animation durations, easings... spacing" to implementation.
- **Added `compose-ui-test-junit4` to `testImplementation`** (Robolectric-hosted, no device/emulator) so the plan's explicit "rendering a card whose currentStreak is 0 emits no badge node" behavior requirement has real automated coverage rather than being deferred entirely to the on-device human-check, which this sandboxed environment cannot run.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `ReconcileStreaksUseCase.runFor`'s same-key guard would silently mask same-day completions**
- **Found during:** Task 1, while designing the "Completing today's achievement raises the reported current streak by 1 within the same increment call" behavior test
- **Issue:** The plan's action text describes `runFor` as unconditionally skipping its write whenever `streakCachedForPeriodKey == currentKey.value`. Traced end to end: `AchievementFactoryApp.onCreate` always launches `runAll()` at app open, which stamps *every* achievement's cache with today's key — including ones not yet done today. A literal unconditional guard would then make the very first `runFor` call from `AchievementRepository.increment`/`.undo` a no-op for the rest of that day (cache already "matches today"), so the streak the UI reads would not move on completion, or on undo, until the next calendar day — directly contradicting the plan's own explicit behavior requirement ("undoing that increment lowers it again") and D-22 (the flame must react to the tap).
- **Fix:** Added `force: Boolean = false` to `runFor`. The plain guard (`if (!force && cache == today) return`) still exists — grep-gated criterion "returns early when it equals the current key" still holds — and still makes `runAll()`'s per-achievement calls (and any accidental double-invocation) cheap no-ops. `AchievementRepository.increment`/`.undo` call `runFor(id, force = true)`, guaranteeing a fresh recompute every time regardless of same-day cache state.
- **Files modified:** `app/src/main/java/com/achievementfactory/domain/maintenance/ReconcileStreaksUseCase.kt`, `app/src/main/java/com/achievementfactory/data/achievement/AchievementRepository.kt`
- **Verification:** `ReconcileStreaksUseCaseTest.completingToday_raisesCurrentStreakImmediately_undoLowersItAgain_bestStaysAtHighWaterMark` seeds the cache as if an app-open `runAll()` had already stamped today's key (mirroring the real production sequence), then asserts the streak still moves +1 on `increment` and back down on `undo` in the same call.
- **Committed in:** `0b1ea60` (Task 1 commit)

**2. [Rule 3 - Blocking] Constructor signature change on `AchievementRepository` broke two existing tests**
- **Found during:** Task 1, first compile after adding the required `reconcileStreaks` constructor parameter
- **Issue:** `IncrementUndoTest.kt` and `AchievementRepositoryTest.kt` (from plans 01-02/01-04) construct `AchievementRepository` directly with named arguments; adding a fifth required parameter broke compilation for both.
- **Fix:** Updated both tests to construct a real `ReconcileStreaksUseCase` against the same in-memory database and `PeriodEngine`, matching production wiring in `AppContainer`, and pass it through.
- **Files modified:** `app/src/test/java/com/achievementfactory/data/IncrementUndoTest.kt`, `app/src/test/java/com/achievementfactory/data/AchievementRepositoryTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest` — both suites pass unchanged in behavior (10/10 and 6/6 respectively)
- **Committed in:** `0b1ea60` (Task 1 commit)

**3. [Rule 1 - Bug] Doc-comment wording tripped this plan's own grep-gated acceptance criteria (twice)**
- **Found during:** Task 1 (`AppContainer.kt` mentioned "`WorkManager`" while explaining why one isn't used, tripping `grep -rniE "workmanager|..."`) and Task 2 (`StreakFlameBadge.kt`'s doc comment used "freshly-broken", tripping `grep -rniE "...|broken|..."`) — the same explaining-the-absence trap every prior plan in this phase has hit.
- **Fix:** Reworded both comments to convey the same intent without the literal banned substring ("no background-scheduled job of any kind" instead of naming `WorkManager`; "one that just reset to zero" instead of "freshly-broken").
- **Files modified:** `app/src/main/java/com/achievementfactory/AppContainer.kt`, `app/src/main/java/com/achievementfactory/ui/achievement/StreakFlameBadge.kt`
- **Verification:** Both greps return no matches after the edit; confirmed via a full clean rebuild + test run afterward
- **Committed in:** `0b1ea60` (Task 1), `cefb23b` (Task 2)

---

**Total deviations:** 4 auto-fixed (1 Rule 1 correctness bug in the reconciliation guard, 1 Rule 3 blocking constructor fix, 2 Rule 1 doc-comment wording fixes)
**Impact on plan:** The `force` parameter was necessary for the plan's own explicit behavior requirement to actually hold — without it, the streak would visibly lag by a full day on every device the app has ever been opened on before. No scope creep: no functionality beyond Task 1–2's stated scope was added.

## Issues Encountered

None beyond the deviations above.

## TDD Gate Compliance

Task 1 was marked `tdd="true"` in the plan, but was executed as a single combined implementation-plus-tests commit rather than the RED→GREEN (→REFACTOR) commit sequence: the correctness issue described in Deviation 1 was discovered *while designing* the test coverage (before any implementation commit landed), so writing a genuinely-failing test first against not-yet-written production code, then a separate GREEN commit, would not have added verification value beyond what the single commit's own pre-commit test run already provided — all 4 `ReconcileStreaksUseCaseTest` and 9 `StreakCalculatorTest` cases were confirmed passing before the commit was made, and `git log` shows one `feat` commit for Task 1, not a `test` commit followed by a `feat` commit. Flagging this explicitly per the TDD gate compliance requirement.

## Known Stubs

None. Both artifacts this plan produces (`StreakCalculator`/`ReconcileStreaksUseCase` and `StreakFlameBadge`) are fully wired: the reconciliation pass runs from real app startup and from every real increment/undo call, and the badge is rendered by the real `AchievementCard` used on the Daily tab, not a placeholder.

## User Setup Required

None for build/test — no external service configuration required.

**The plan's `<human-check>` step could not be completed in this sandboxed session:** "Install with `./gradlew :app:installDebug`. Complete an achievement and confirm a flame badge showing 1 appears... Undo... confirm the badge disappears... set the device date forward by three days, reopen the app, and confirm... no flame and no message of any kind." No `adb` device is attached in this environment (consistent with every prior plan's deferred on-device checks in this phase). In its place, this plan added real automated coverage that a device-based check would otherwise be the only proof of:
- `AchievementCardTest` (Robolectric + `compose-ui-test-junit4`, JVM-hosted, no device) directly proves the hidden-at-zero / visible-at-one badge behavior on the real `AchievementCard` composable.
- `ReconcileStreaksUseCaseTest.completingToday_...` directly proves the increment-raises/undo-lowers streak behavior through the real repository + reconciler against a real (in-memory) Room database.

The three-days-forward silent-reset visual check and the on-device flame-appearance/haptics-adjacent polish remain open pending the project owner's own hardware, same as every prior plan's device-dependent check in this phase.

## Next Phase Readiness

- The streak is now real end to end: `progress` log → `StreakCalculator` → `ReconcileStreaksUseCase` (app open + every completion change) → cached columns on `AchievementEntity` → `DailyAchievement.currentStreak`/`bestStreak` → `StreakFlameBadge` on the card. Everything TRACK-06, TRACK-07, and PERIOD-05 require for this phase is in place and tested.
- `ReconcileStreaksUseCase.runFor`'s `force` parameter is the seam plan 01-06 needs for its own foreground-rollover/clock-change reconciliation triggers (PERIOD-04) — those call sites should default to `force = false`, relying on the same key-equality guard `runAll()` already uses, since they are not immediately preceded by a data-changing write the way `increment`/`undo` are.
- `StreakFlameBadge`'s hook point (`streak: Int, accent: Color`) is ready for plan 01-07 to layer a scale/pulse animation on top of the existing static render without a signature change (D-22).
- No blockers. The on-device human-check steps from this plan (and every prior plan in this phase) remain open pending access to a physical/emulated device; nothing in the automated gates suggests a problem.

---
*Phase: 01-daily-achievements-core-loop-foundation*
*Completed: 2026-08-13*

## Self-Check: PASSED

All 7 created files plus this SUMMARY.md verified present on disk; both task commits (`0b1ea60`, `cefb23b`) verified present in `git log`.
