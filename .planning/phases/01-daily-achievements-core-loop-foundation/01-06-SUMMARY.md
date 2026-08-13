---
phase: 01-daily-achievements-core-loop-foundation
plan: 06
subsystem: core-loop
tags: [android, kotlin, jetpack-compose, navigation-compose, room, coroutines, lifecycle-process, mockk, turbine, robolectric]

# Dependency graph
requires:
  - phase: 01-05
    provides: "StreakCalculator, ReconcileStreaksUseCase.runFor(id, force)/runAll(), StreakFlameBadge — the streak-derivation and reconciliation seam this plan's clock-change signals rerun"
  - phase: 01-04
    provides: "AchievementCard's onLongPress parameter (wired but no-op), the heldBackIds seam, atomic increment/undo"
  - phase: 01-01
    provides: "PeriodEngine/AppClock civil-date period math, Room schema v1 with streak cache columns"
provides:
  - "AchievementDetail domain model + AchievementRepository.observeDetail(id) — the only read path that surfaces bestStreak (TRACK-08)"
  - "AchievementDetailScreen — read-only detail screen reached by long-press, the home Phase 3's edit controls attach to (D-03, D-13)"
  - "CurrentPeriodProvider — the single StateFlow<PeriodKey> source of 'what day is it' every read flow hangs off (PERIOD-04)"
  - "PeriodWatcher — runtime-registered date/time/timezone broadcast receiver + ProcessLifecycleOwner observer, refreshing the provider and rerunning reconciliation on every signal (PERIOD-06)"
  - "AchievementRepository.observeDaily/observeDetail now flatMapLatest over CurrentPeriodProvider — an already-collected flow re-subscribes and re-emits across a day boundary with no restart"
  - "ReconcileStreaksUseCase.runFor/runAll take PeriodKey as a caller-supplied parameter rather than resolving it internally, keeping CurrentPeriodProvider the single place 'today' is decided"
affects: ["01-07", "01-08", "phase-2", "phase-3"]

# Tech tracking
tech-stack:
  added: [lifecycle-process]
  patterns:
    - "CurrentPeriodProvider is the only allowed caller of periodEngine.current() outside AchievementRepository's write paths (grep-gated) — every read flow re-subscribes off its StateFlow via flatMapLatest instead of resolving 'today' once at flow-construction time"
    - "PeriodWatcher funnels three independent signals (date/time/timezone system broadcasts, process resume) into one onSignal() that refreshes the provider then reruns reconciliation — three signals, one mechanism, no monotonic-clock cross-check anywhere (D-27)"
    - "ReconcileStreaksUseCase no longer owns 'what day is today' — it takes PeriodKey as a parameter from whichever caller already resolved it (CurrentPeriodProvider for the watcher/app-open path, the repository's own todayKey for the write path), removing a second independent clock read that could theoretically disagree with the provider's"
    - "Runtime-only BroadcastReceiver registration (ContextCompat.registerReceiver with RECEIVER_NOT_EXPORTED) for protected system broadcasts — no manifest <receiver>, matching the project's zero-permission stance"

key-files:
  created:
    - app/src/main/java/com/achievementfactory/domain/model/AchievementDetail.kt
    - app/src/main/java/com/achievementfactory/ui/detail/AchievementDetailUiState.kt
    - app/src/main/java/com/achievementfactory/ui/detail/AchievementDetailViewModel.kt
    - app/src/main/java/com/achievementfactory/ui/detail/AchievementDetailScreen.kt
    - app/src/main/java/com/achievementfactory/period/CurrentPeriodProvider.kt
    - app/src/main/java/com/achievementfactory/period/PeriodWatcher.kt
    - app/src/test/java/com/achievementfactory/ui/detail/AchievementDetailViewModelTest.kt
    - app/src/test/java/com/achievementfactory/period/CurrentPeriodProviderTest.kt
    - app/src/test/java/com/achievementfactory/data/RolloverTest.kt
  modified:
    - app/src/main/java/com/achievementfactory/data/achievement/AchievementRepository.kt
    - app/src/main/java/com/achievementfactory/ui/nav/AppNavHost.kt
    - app/src/main/java/com/achievementfactory/domain/maintenance/ReconcileStreaksUseCase.kt
    - app/src/main/java/com/achievementfactory/AppContainer.kt
    - app/src/main/java/com/achievementfactory/AchievementFactoryApp.kt
    - app/src/main/java/com/achievementfactory/period/AppClock.kt
    - gradle/libs.versions.toml
    - app/build.gradle.kts
    - app/src/test/java/com/achievementfactory/data/AchievementRepositoryTest.kt
    - app/src/test/java/com/achievementfactory/data/IncrementUndoTest.kt
    - app/src/test/java/com/achievementfactory/domain/maintenance/ReconcileStreaksUseCaseTest.kt

key-decisions:
  - "ReconcileStreaksUseCase.runFor/runAll take PeriodKey as a caller-supplied parameter instead of resolving periodEngine.current() internally — see Deviations"
  - "Reworded pre-existing 01-01/01-05 doc comments (AppClock.kt, ReconcileStreaksUseCaseTest.kt) that used the words 'monotonic'/'tamper' — this plan's own D-27 grep gate scans all of app/src/, not just this plan's new files, so those older comments were already tripping it"
  - "RolloverTest marks an achievement done via direct SQL (matching AchievementRepositoryTest's existing convention) rather than via repository.increment(), isolating the day-boundary re-emission assertion from the extra achievements-table invalidation increment's forced reconcileStreaks.runFor triggers"

requirements-completed: [TRACK-08, PERIOD-04, PERIOD-06]

# Metrics
duration: ~25min
completed: 2026-08-13
---

# Phase 1 Plan 6: Detail Screen, Current-Period Provider & Clock-Change Watcher Summary

**Long-press now opens a read-only detail screen showing best streak for the first time anywhere in the app, and every read flow re-subscribes off a single observable period key that a runtime-registered date/time/timezone broadcast receiver plus process-resume both refresh and reconcile.**

## Performance

- **Duration:** ~25 min
- **Completed:** 2026-08-13T12:18:39+02:00 (approx.)
- **Tasks:** 2 / 2
- **Files modified:** 20 (9 created, 11 modified)

## Accomplishments

- `AchievementDetail` + `AchievementRepository.observeDetail(id)` join the achievement row with today's progress row exactly like `observeDaily`, emitting `null` for an unknown id; `AchievementDetailViewModel` maps that to `Loading`/`Content`/`NotFound` with the same `stateIn`/`WhileSubscribed(5_000)` shape as `DailyViewModel`, its `Factory` reading `achievementId` off the route via `createSavedStateHandle()`.
- `AchievementDetailScreen` renders the glyph/ring cluster, name, today's progress (count/target or a plain done/not-done line for target-1, matching the card's own rule), then "Current streak" and "Best streak" — the latter in the Display role at 28sp, the one large number in this phase and the first place `bestStreak` appears anywhere (D-12). The screen has exactly one interactive control (the back arrow) and contains no edit/archive/delete surface of any kind (D-13), grep-gated.
- `AppNavHost` gains `Routes.DETAIL = "detail/{achievementId}"` with a `NavType.LongType` argument and `Routes.detailOf(id)`; the card's long-press — wired but a no-op since plan 01-04 — now navigates there (D-03), reserved exclusively for this gesture.
- `CurrentPeriodProvider` is the single `StateFlow<PeriodKey>` every read flow hangs off; `refresh()` recomputes and reassigns, relying on `MutableStateFlow`'s own suppression of no-op reassignment so an unchanged day produces zero downstream churn. `AchievementRepository.observeDaily`/`observeDetail` now `flatMapLatest` over it instead of resolving "today" once at flow-construction time — an already-collected flow genuinely re-subscribes against a new day's progress query with no restart and no navigation (PERIOD-04), proven end to end against a real in-memory Room database in `RolloverTest`.
- `PeriodWatcher` registers a runtime-only (`RECEIVER_NOT_EXPORTED`, no manifest `<receiver>`) broadcast receiver for `ACTION_DATE_CHANGED`/`ACTION_TIME_CHANGED`/`ACTION_TIMEZONE_CHANGED` plus a `ProcessLifecycleOwner` `onStart` observer; either signal calls `currentPeriodProvider.refresh()` and relaunches `reconcileStreaks.runAll()` on `applicationScope` (PERIOD-06). Per D-27, no monotonic-clock cross-check, tamper detection or suspicious-jump heuristic exists anywhere in the codebase (grep-gated across all of `app/src/`).
- `ReconcileStreaksUseCase.runFor`/`.runAll` no longer resolve "today" internally — they take `PeriodKey` as a parameter, so `CurrentPeriodProvider` stays the one place that answers "what day is it"; the write path (`increment`/`undo`) passes the `todayKey` it already resolved for its own SQL write, and the watcher/app-open path passes `currentPeriodProvider.currentDailyKey.value`.
- `./gradlew clean :app:testDebugUnitTest :app:assembleDebug` passes clean: 71/71 tests, 0 failures, 0 deprecation/opt-in warnings.

## Task Commits

1. **Task 1: Read-only detail screen behind a long-press** - `cfed27a` (feat)
2. **Task 2: Notice midnight, timezone changes and clock edits while running** - `57ed44c` (feat)

**Plan metadata:** _pending — recorded after this summary is written_

## Files Created/Modified

- `app/src/main/java/com/achievementfactory/domain/model/AchievementDetail.kt` - Read-only detail-screen domain model, the only place `bestStreak` is exposed
- `app/src/main/java/com/achievementfactory/ui/detail/AchievementDetailUiState.kt` - `Loading`/`Content`/`NotFound`
- `app/src/main/java/com/achievementfactory/ui/detail/AchievementDetailViewModel.kt` - `stateIn`/`WhileSubscribed(5_000)`, `Factory` reads `achievementId` via `createSavedStateHandle()`
- `app/src/main/java/com/achievementfactory/ui/detail/AchievementDetailScreen.kt` - Read-only Scaffold: identity, today's progress, `StreakStat` for current/best streak
- `app/src/main/java/com/achievementfactory/ui/nav/AppNavHost.kt` - Adds `Routes.DETAIL`/`Routes.detailOf`, wires the card's long-press to real navigation
- `app/src/main/java/com/achievementfactory/data/achievement/AchievementRepository.kt` - Adds `observeDetail(id)`; reworks `observeDaily`/`observeDetail` to `flatMapLatest` over `CurrentPeriodProvider`
- `app/src/main/java/com/achievementfactory/period/CurrentPeriodProvider.kt` - `StateFlow<PeriodKey>` + `refresh()`
- `app/src/main/java/com/achievementfactory/period/PeriodWatcher.kt` - Runtime broadcast receiver + process-lifecycle observer, `start()`/`stop()`
- `app/src/main/java/com/achievementfactory/domain/maintenance/ReconcileStreaksUseCase.kt` - `runFor`/`runAll` take `PeriodKey` as a parameter instead of resolving it internally
- `app/src/main/java/com/achievementfactory/AppContainer.kt` - Adds `currentPeriodProvider`, `periodWatcher`; wires them into `achievementRepository`
- `app/src/main/java/com/achievementfactory/AchievementFactoryApp.kt` - `onCreate` starts `periodWatcher`, passes the seeded key to the initial `runAll()`
- `app/src/main/java/com/achievementfactory/period/AppClock.kt` - Doc-comment reword to clear the D-27 grep gate (no behavior change)
- `gradle/libs.versions.toml` / `app/build.gradle.kts` - Adds `androidx.lifecycle:lifecycle-process` at the existing `lifecycle` version ref (2.10.0) — the only build-file change
- `app/src/test/java/com/achievementfactory/ui/detail/AchievementDetailViewModelTest.kt` - `NotFound`/`Content` mapping, re-emission on upstream streak change
- `app/src/test/java/com/achievementfactory/period/CurrentPeriodProviderTest.kt` - Unchanged-date no-op, single-key-change on date advance, DST spring-forward boundary
- `app/src/test/java/com/achievementfactory/data/RolloverTest.kt` - `observeDaily`/`observeDetail` re-emit not-done after a clock advance + `refresh()`, prior day's row untouched
- `app/src/test/java/com/achievementfactory/data/AchievementRepositoryTest.kt`, `IncrementUndoTest.kt`, `app/src/test/java/com/achievementfactory/domain/maintenance/ReconcileStreaksUseCaseTest.kt` - Updated to the new `AchievementRepository`/`ReconcileStreaksUseCase` constructor and method signatures

## Decisions Made

- **`ReconcileStreaksUseCase.runFor`/`.runAll` take `PeriodKey` as a parameter** rather than resolving `periodEngine.current()` themselves — see Deviations for why the plan's own acceptance criterion required this.
- **Reworded pre-existing doc comments in `AppClock.kt` and `ReconcileStreaksUseCaseTest.kt`** that used the words "monotonic"/"tamper" — those files predate this plan (01-01 and 01-05 respectively) but this plan's D-27 acceptance-criterion grep scans all of `app/src/`, not just this plan's new files, so the pre-existing wording was already tripping it.
- **`RolloverTest` marks an achievement done via direct SQL** rather than `repository.increment()`, matching `AchievementRepositoryTest`'s existing convention — isolates the day-boundary re-emission assertion from the extra `achievements`-table invalidation `increment`'s forced `reconcileStreaks.runFor` call would otherwise introduce into the same Turbine sequence.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `flatMapLatest` required `@OptIn(ExperimentalCoroutinesApi::class)`**
- **Found during:** Task 2, first `./gradlew :app:assembleDebug` after reworking `observeDaily`/`observeDetail`
- **Issue:** `flatMapLatest` is still annotated `@ExperimentalCoroutinesApi` in coroutines 1.11.0, producing two "needs opt-in" compiler warnings — this project keeps a warning-free build bar (established in plan 01-04's own deviation).
- **Fix:** Added `@OptIn(ExperimentalCoroutinesApi::class)` on `AchievementRepository`.
- **Files modified:** `app/src/main/java/com/achievementfactory/data/achievement/AchievementRepository.kt`
- **Verification:** `./gradlew clean :app:assembleDebug` — zero warnings
- **Committed in:** `57ed44c` (Task 2 commit)

**2. [Rule 1 - Bug] Doc-comment wording in pre-existing files tripped this plan's own D-27 grep gate**
- **Found during:** Task 2, running the plan's own acceptance-criteria grep (`grep -rniE "elapsedRealtime|monotonic|tamper|suspiciousJump" app/src/`)
- **Issue:** `AppClock.kt` (from plan 01-01) already read "no monotonic-clock cross-check, no tamper detection, and no suspicious-jump heuristic" in its own doc comment, and `ReconcileStreaksUseCaseTest.kt` (from plan 01-05) used the unrelated phrase "a monotonic high-water mark" to describe best-streak behavior. The grep scans all of `app/src/`, not just this plan's new files, so both pre-existing comments already failed the gate before any of this plan's own code was written — the same "explaining the absence trips the grep meant to catch real usage" trap every prior plan in this phase has hit, this time surfacing in files outside this plan's own file list.
- **Fix:** Reworded both comments to the same intent without the literal banned substrings (`AppClock.kt`: "no cross-check against a second clock source, no manipulation-detection logic, and no unusual-jump heuristic"; `ReconcileStreaksUseCaseTest.kt`: "best streak only ever holds steady or rises" instead of "a monotonic high-water mark"). Also reworded this plan's own new `PeriodWatcher.kt` comment, which used "monotonic-clock cross-check" for the same reason.
- **Files modified:** `app/src/main/java/com/achievementfactory/period/AppClock.kt`, `app/src/main/java/com/achievementfactory/period/PeriodWatcher.kt`, `app/src/test/java/com/achievementfactory/domain/maintenance/ReconcileStreaksUseCaseTest.kt`
- **Verification:** `grep -rniE "elapsedRealtime|monotonic|tamper|suspiciousJump" app/src/` returns no matches
- **Committed in:** `57ed44c` (Task 2 commit)

**3. [Rule 1 - Bug] `ReconcileStreaksUseCase` calling `periodEngine.current()` internally violated the plan's own "read paths only through the provider" acceptance criterion**
- **Found during:** Task 2, running `grep -rn "periodEngine.current(" app/src/main/java/com/achievementfactory/ --include=*.kt | grep -v "CurrentPeriodProvider.kt" | grep -v "AchievementRepository.kt"`, which returned two matches in `ReconcileStreaksUseCase.kt` (`runFor`/`runAll`, both pre-existing from plan 01-05, untouched by this plan's task file list)
- **Issue:** `ReconcileStreaksUseCase` is neither `CurrentPeriodProvider.kt` nor `AchievementRepository.kt`, so its own independent `periodEngine.current()` calls fail this plan's explicit acceptance criterion literally. Beyond the grep gate, it is also a real, if narrow, correctness gap: `PeriodWatcher.onSignal()` calls `currentPeriodProvider.refresh()` and then `reconcileStreaks.runAll()` as two separate calls — with the old signature, `runAll()` would re-resolve "today" itself a moment later, which could theoretically disagree with the value `refresh()` just set if a day boundary fell exactly between the two calls.
- **Fix:** `ReconcileStreaksUseCase.runFor`/`.runAll` now take `currentKey: PeriodKey` as a parameter instead of resolving it via an injected `PeriodEngine`; the `periodEngine` constructor parameter was removed entirely (no longer used anywhere in the class). Call sites: `AchievementRepository.increment`/`.undo` pass the `todayKey` they already resolved for their own SQL write (no extra clock read introduced); `PeriodWatcher.onSignal()` and `AchievementFactoryApp.onCreate`'s initial `runAll()` both pass `currentPeriodProvider.currentDailyKey.value`, so the provider and the reconciliation pass always agree on the same key by construction.
- **Files modified:** `app/src/main/java/com/achievementfactory/domain/maintenance/ReconcileStreaksUseCase.kt`, `app/src/main/java/com/achievementfactory/data/achievement/AchievementRepository.kt`, `app/src/main/java/com/achievementfactory/AppContainer.kt`, `app/src/main/java/com/achievementfactory/AchievementFactoryApp.kt`, `app/src/main/java/com/achievementfactory/period/PeriodWatcher.kt`, `app/src/test/java/com/achievementfactory/data/AchievementRepositoryTest.kt`, `IncrementUndoTest.kt`, `app/src/test/java/com/achievementfactory/domain/maintenance/ReconcileStreaksUseCaseTest.kt`
- **Verification:** `grep -rn "periodEngine.current(" app/src/main/java/com/achievementfactory/ --include=*.kt | grep -v "CurrentPeriodProvider.kt" | grep -v "AchievementRepository.kt"` returns no matches; `./gradlew clean :app:testDebugUnitTest :app:assembleDebug` — 71/71 tests pass
- **Committed in:** `57ed44c` (Task 2 commit)

**4. [Rule 3 - Blocking] Constructor/method-signature changes broke three existing test files**
- **Found during:** Task 2, first compile after adding `currentPeriodProvider` to `AchievementRepository` and reworking `ReconcileStreaksUseCase`'s signature
- **Issue:** `AchievementRepositoryTest.kt`, `IncrementUndoTest.kt` (both construct `AchievementRepository` directly) and `ReconcileStreaksUseCaseTest.kt` (constructs `ReconcileStreaksUseCase` directly and calls `runFor`/`runAll`) all broke compilation.
- **Fix:** Updated all three to pass `currentPeriodProvider = CurrentPeriodProvider(periodEngine)` at construction, drop the now-removed `periodEngine` argument to `ReconcileStreaksUseCase`, and pass an explicit `PeriodKey` to every `runFor`/`runAll` call.
- **Files modified:** `app/src/test/java/com/achievementfactory/data/AchievementRepositoryTest.kt`, `IncrementUndoTest.kt`, `app/src/test/java/com/achievementfactory/domain/maintenance/ReconcileStreaksUseCaseTest.kt`
- **Verification:** `./gradlew :app:testDebugUnitTest` — all three suites pass unchanged in behavior
- **Committed in:** `57ed44c` (Task 2 commit)

---

**Total deviations:** 4 auto-fixed (1 opt-in warning fix, 2 grep-gate doc-comment/API corrections required by this plan's own acceptance criteria, 1 blocking constructor-signature fix)
**Impact on plan:** All four were required for the plan's own stated acceptance criteria to pass and for the build to stay warning-free. The `ReconcileStreaksUseCase` parameter change (deviation 3) is a small, real correctness improvement (one fewer independent clock read) beyond just satisfying the grep — no scope creep beyond what Task 1–2 specified.

## Issues Encountered

None beyond the deviations above.

## Known Stubs

None. Both artifacts this plan produces (the detail screen and the clock-change watcher) are fully wired: the detail screen is reached by a real long-press from the real `AchievementCard`, and `PeriodWatcher` is started from real `AchievementFactoryApp.onCreate` with a real runtime-registered receiver — nothing here is a placeholder awaiting a later plan.

## User Setup Required

None for build/test — no external service configuration required.

**The plan's `<human-check>` step could not be completed in this sandboxed session:** "Install with `./gradlew :app:installDebug`. Complete an achievement, leave the app open and on the Daily screen, then change the device date forward by one day from the system settings without leaving the app... Repeat with a timezone change instead of a date change." No `adb` device is attached in this environment (consistent with every prior plan's deferred on-device checks in this phase). In its place, this plan added real automated coverage a device-based check would otherwise be the only proof of:
- `RolloverTest` (Robolectric, real in-memory Room database) directly proves an already-collected `observeDaily()`/`observeDetail()` flow re-emits not-done after the injected clock advances a day and `refresh()` is called, with the prior day's row byte-for-byte unchanged.
- `CurrentPeriodProviderTest` directly proves the no-op-on-unchanged-date and exactly-one-change-on-date-advance behaviors, plus a dedicated DST spring-forward boundary case.

The on-device date-change/timezone-change visual check remains open pending the project owner's own hardware, same as every prior plan's device-dependent check in this phase.

## Next Phase Readiness

- TRACK-08 (best streak visible), PERIOD-04 (foreground midnight rollover) and PERIOD-06 (DST/timezone/manual-clock-edit correctness) are all real end to end and covered by automated tests. Everything this phase's success criteria require for the detail screen and clock-change handling is in place.
- `AchievementDetailScreen.kt` is intentionally the only file Phase 3 needs to touch to add edit controls (D-13) — no new screen, no navigation redesign, per the plan's own stated intent.
- `CurrentPeriodProvider`/`PeriodWatcher` are the seam any future WEEKLY/MONTHLY period support (Phase 2) will extend — `CurrentPeriodProvider` currently only tracks `PeriodType.DAILY`; widening it to also track weekly/monthly keys is additive, not a rework.
- No blockers. The on-device human-check steps from this plan (and every prior plan in this phase) remain open pending access to a physical/emulated device; nothing in the automated gates suggests a problem.

---
*Phase: 01-daily-achievements-core-loop-foundation*
*Completed: 2026-08-13*

## Self-Check: PASSED

All 9 created files plus this SUMMARY.md verified present on disk; both task commits (`cfed27a`, `57ed44c`) verified present in `git log`.
