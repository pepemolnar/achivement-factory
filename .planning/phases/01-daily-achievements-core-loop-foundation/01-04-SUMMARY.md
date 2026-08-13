---
phase: 01-daily-achievements-core-loop-foundation
plan: 04
subsystem: core-loop
tags: [android, kotlin, jetpack-compose, room, sqlite, coroutines, mockk, turbine, robolectric]

# Dependency graph
requires:
  - phase: 01-02
    provides: "AchievementRepository/DailyViewModel/DailyUiState/DailyScreen/AchievementCard/ProgressRing shapes this plan extends"
  - phase: 01-03
    provides: "The creation flow that produces real achievements to tap on"
provides:
  - "AchievementRepository.increment()/undo() — the only mutating write path in the app, atomic at the SQL layer, current-day-only by construction"
  - "ProgressDao.incrementAtomic/undoIfUnchanged/incrementAndRead — compare-and-set counter mutation with no read-modify-write anywhere"
  - "IncrementOutcome/UndoToken domain models — the shape every later increment/undo consumer (celebration events, streak recompute) reads"
  - "DailyViewModel.onIncrement/onUndo + undoRequests Channel — the one-shot event pattern plan 01-07's celebration channel reuses"
  - "DailyUiState.Content.partition()/heldBackIds — the seam plan 01-07 populates to hold a card in outstanding during its completion animation"
  - "AchievementCard's combinedClickable (D-01/D-02) and ProgressRing's closed/tween(150ms) contract — the interaction surface every later card treatment builds on"
affects: ["01-05", "01-06", "01-07", "01-08", "phase-2", "phase-3"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Counter mutation is single-statement atomic SQL (UPDATE ... SET count = count + 1 / count = count - 1 WHERE count = :expectedCount) — never a Kotlin-side read before a write, per PITFALLS 9"
    - "Undo is compare-and-set against the exact count a specific increment produced (UndoToken.expectedCount), not a blind decrement — a stale or replayed-past-midnight token is a structural no-op, not a runtime check"
    - "Room @Transaction default-interface-method pattern (ProgressDao.incrementAndRead) wraps row creation + atomic update + read-back in one DB transaction so a concurrent caller's reported count always matches exactly what its own call produced"
    - "Writes never accept a period key from the caller — AchievementRepository.increment/undo resolve periodEngine.current(DAILY) internally, which is what makes TRACK-05/TRACK-10/D-28 structural rather than a convention someone could violate"
    - "One-shot events live on a Channel(BUFFERED).receiveAsFlow(), collected via repeatOnLifecycle(STARTED) — never folded into a StateFlow-backed UiState (PITFALLS 8), extending the pattern DailyUiState.kt already established for celebration events"
    - "DailyUiState.Content exposes its done/outstanding split as a testable factory (Content.partition(achievements, heldBackIds)) rather than inlining the partition in the ViewModel, so the heldBackIds seam is unit-testable independent of what the ViewModel currently populates it with"

key-files:
  created:
    - app/src/main/java/com/achievementfactory/domain/model/IncrementOutcome.kt
    - app/src/main/java/com/achievementfactory/domain/model/UndoToken.kt
    - app/src/test/java/com/achievementfactory/data/IncrementUndoTest.kt
  modified:
    - app/src/main/java/com/achievementfactory/data/progress/ProgressDao.kt
    - app/src/main/java/com/achievementfactory/data/achievement/AchievementRepository.kt
    - app/src/main/java/com/achievementfactory/ui/daily/DailyUiState.kt
    - app/src/main/java/com/achievementfactory/ui/daily/DailyViewModel.kt
    - app/src/main/java/com/achievementfactory/ui/daily/DailyScreen.kt
    - app/src/main/java/com/achievementfactory/ui/achievement/AchievementCard.kt
    - app/src/main/java/com/achievementfactory/ui/achievement/ProgressRing.kt
    - app/src/main/java/com/achievementfactory/ui/nav/AppNavHost.kt
    - app/src/test/java/com/achievementfactory/ui/daily/DailyViewModelTest.kt

key-decisions:
  - "Reused the existing ProgressDao.insertIfAbsent (from plan 01-01) for row creation instead of adding a separately-named insertRowIfAbsent — identical INSERT OR IGNORE behavior already existed; duplicating it under a new name would have been dead-weight API surface"
  - "IncrementOutcome.justCompleted is derived arithmetically from newCount alone (previousCount = newCount - 1) rather than from a separate pre-increment read — removes a second read that could itself race a concurrent increment and report the wrong transition for 50-parallel-tap correctness"
  - "DailyUiState.Content.partition() is a testable companion factory rather than inline partition logic in DailyViewModel — lets the heldBackIds behavior (an achievement staying outstanding despite being done) be unit-tested directly, since DailyViewModel itself always passes an empty set in this plan and plan 01-07 is what will actually populate it"
  - "Undo snackbar body text is 'Progress updated' (not specified in 01-UI-SPEC.md, left to discretion) — the action label 'Undo' and SnackbarDuration.Short are the load-bearing, spec-mandated parts"

patterns-established:
  - "The clock-rollback guarantee (D-28) is structural, not a tamper check: because increment/undo only ever name periodEngine.current(DAILY) as the row to touch, and undo additionally revalidates its token's period key against that same call, a day that is not the trusted clock's current 'today' is never named by any write path — proven by a test that walks the clock forward across three days then back onto a day that already has a row, and asserts an even-earlier day's row is untouched by both an increment and an undo attempt"

requirements-completed: [TRACK-01, TRACK-02, TRACK-03, TRACK-04, TRACK-05, TRACK-10]

# Metrics
duration: ~45min
completed: 2026-08-13
---

# Phase 1 Plan 4: Increment/Undo Core Loop — Atomic Counter, Whole-Card Tap & Undo Snackbar Summary

**The whole achievement card is now the tap target: each tap is an atomic `count = count + 1` SQL write bound to today's period key only, undone via a compare-and-set snackbar action, with a 150ms ring sweep and a static closed/checkmark treatment on completion.**

## Performance

- **Duration:** ~45 min
- **Tasks:** 2 / 2
- **Files modified:** 12 (3 created, 9 modified)

## Accomplishments

- `ProgressDao.incrementAtomic`/`undoIfUnchanged` are each a single `UPDATE ... SET count = ...` statement — undo's `WHERE count = :expectedCount AND count > 0` is a compare-and-set that reverses one *specific* increment and can never drive the count negative, with no Kotlin-side read-modify-write anywhere in the DAO (PITFALLS 9)
- `ProgressDao.incrementAndRead` wraps row creation, the atomic update, and the count read-back in one `@Transaction` default-interface method, so under 50 concurrent taps each call's reported `newCount` matches exactly what that call produced — proven by `IncrementUndoTest.fiftyConcurrentIncrements_produceExactlyFifty`
- `AchievementRepository.increment()`/`undo()` resolve `periodEngine.current(PeriodType.DAILY)` internally and never accept a period key from the caller (grep-gated: no `fun (increment|undo)[^)]*periodKey` in the file) — this makes TRACK-05 structural. `undo()` additionally revalidates the token's period key against today, so a token that survived past midnight is refused rather than applied to a new day
- A dedicated clock-rollback test (`IncrementUndoTest.clockMovedBackward_previousDaysRowIsNeverTouchedByIncrementOrUndo`) walks the injected clock across three days and back, and proves an earlier day's row is untouched by both a later increment and a stale-relative-to-now undo attempt (D-28)
- `AchievementCard`'s entire surface is the tap target via `combinedClickable(onClick = onIncrement, onLongClick = onLongPress)` — no dedicated check button, no stepper, no swipe (D-01/D-02); `onLongPress` is a wired-but-no-op parameter, ready for plan 01-06's detail-screen navigation (D-03)
- `ProgressRing` gains a `closed: Boolean` parameter and animates every sweep change — partial tap or the completing one alike — with `tween(150ms, FastOutSlowInEasing)`, the exact per-tap spec from 01-UI-SPEC.md; completed cards render the closed ring + accent checkmark statically, with the bouncier ring-close/glyph-morph transition explicitly deferred to plan 01-07
- Target-1 achievements never show a fraction anywhere on the card; target>1 achievements show `count/target` in the Label role, on both resting and done cards
- `DailyUiState.Content.partition()` computes the outstanding/done split and carries `heldBackIds: Set<Long>` (always empty this plan) — the exact seam plan 01-07 needs to hold a just-completed card in outstanding for the duration of its celebration (D-05/D-06), unit-tested directly rather than only reachable through the ViewModel's hardcoded-empty path
- `DailyViewModel` exposes `onIncrement`/`onUndo` plus a `Channel(BUFFERED).receiveAsFlow()` `undoRequests` stream — undo is an event, never folded into `DailyUiState` (grep-gated: no `undoToken|showSnackbar|snackbar` in `DailyUiState.kt`)
- `DailyScreen` raises a `SnackbarHost` with the exact action label `"Undo"` at `SnackbarDuration.Short` on each `undoRequests` emission, collected inside `repeatOnLifecycle(Lifecycle.State.STARTED)` so a backgrounded app doesn't queue snackbars
- `./gradlew clean :app:testDebugUnitTest :app:assembleDebug` passes clean: 47/47 tests (10 new in `IncrementUndoTest`, 2 new in `DailyViewModelTest`), zero failures, zero deprecation warnings

## Task Commits

Task 1 followed the RED/GREEN TDD cycle (marked `tdd="true"`); Task 2 was a single commit.

1. **Task 1 (RED): add failing test for atomic increment/undo** - `5502e37` (test)
2. **Task 1 (GREEN): implement atomic increment/undo with clock-rollback guard** - `b5c6eff` (feat)
3. **Task 2: whole-card tap, filling ring, Done sink and undo snackbar** - `2c22331` (feat)

**Plan metadata:** _pending — recorded after this summary is written_

## Files Created/Modified

- `app/src/main/java/com/achievementfactory/domain/model/IncrementOutcome.kt` - Result of one atomic increment: `newCount`, `isDone`, `justCompleted`, `undoToken`
- `app/src/main/java/com/achievementfactory/domain/model/UndoToken.kt` - Identity of the specific mutation an undo call reverses (`achievementId`, `periodKey`, `expectedCount`)
- `app/src/main/java/com/achievementfactory/data/progress/ProgressDao.kt` - `incrementAtomic`, `undoIfUnchanged` (compare-and-set), `countFor`, `incrementAndRead` (`@Transaction`)
- `app/src/main/java/com/achievementfactory/data/achievement/AchievementRepository.kt` - `increment(achievementId)`/`undo(token)`, both current-day-only by construction
- `app/src/main/java/com/achievementfactory/ui/daily/DailyUiState.kt` - `Content.heldBackIds` + `Content.partition()` factory
- `app/src/main/java/com/achievementfactory/ui/daily/DailyViewModel.kt` - `onIncrement`/`onUndo`, `Channel`-backed `undoRequests`
- `app/src/main/java/com/achievementfactory/ui/daily/DailyScreen.kt` - `SnackbarHost`, `repeatOnLifecycle`-scoped undo collection, forwards tap/long-press to cards
- `app/src/main/java/com/achievementfactory/ui/achievement/AchievementCard.kt` - `combinedClickable`, checkmark-on-done glyph swap, conditional `count/target` label
- `app/src/main/java/com/achievementfactory/ui/achievement/ProgressRing.kt` - `closed` param, `animateFloatAsState`/`tween(150ms, FastOutSlowInEasing)`
- `app/src/main/java/com/achievementfactory/ui/nav/AppNavHost.kt` - Wires `onIncrement`/`onUndo`/`undoRequests`/no-op `onLongPress` into `DailyScreen`
- `app/src/test/java/com/achievementfactory/data/IncrementUndoTest.kt` - 10 Robolectric tests: create-on-first-tap, target-1/target-3 completion edges, past-target no-re-announce, 50-concurrent-increments, stale/replayed/twice-applied undo, floor-at-zero, clock-rollback
- `app/src/test/java/com/achievementfactory/ui/daily/DailyViewModelTest.kt` - Adds Turbine coverage for exactly-one-`UndoToken`-per-`onIncrement`, and a direct unit test of `Content.partition()`'s `heldBackIds` behavior

## Decisions Made

- **Reused the existing `ProgressDao.insertIfAbsent`** (already present from plan 01-01) instead of adding a separately-named `insertRowIfAbsent` — the plan's described behavior (`INSERT OR IGNORE` on the unique `(achievementId, periodKey)` index) was already implemented verbatim; adding a second, identically-behaved method under a new name would have been redundant API surface.
- **`IncrementOutcome.justCompleted` is computed arithmetically from `newCount` alone** (`previousCount = newCount - 1`) rather than via a separate pre-increment read of the row — this removes a second read that could itself race a concurrent increment on another coroutine and report an incorrect transition; since exactly one increment happens per call, `newCount - 1` is always that call's true "before" value.
- **`DailyUiState.Content.partition()` is a testable companion factory** rather than inline partition logic inside `DailyViewModel.uiState`'s `map {}` — `DailyViewModel` always calls it with an empty `heldBackIds` in this plan (plan 01-07 is what actually populates it), so the only way to unit-test "an achievement whose id is held back stays outstanding despite being done" is to test the factory directly, which is exactly what `DailyViewModelTest.contentPartition_achievementWhoseIdIsHeldBack_staysOutstandingDespiteBeingDone` does.
- **Undo snackbar body text is "Progress updated"** — 01-UI-SPEC.md's Copywriting Contract specifies the action label (`"Undo"`) and defers snackbar body text to planner/executor discretion; this phrase is short, calm, and correct regardless of whether the tap was a partial increment or the completing one.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Replaced the deprecated `androidx.compose.ui.platform.LocalLifecycleOwner` import with `androidx.lifecycle.compose.LocalLifecycleOwner`**
- **Found during:** Task 2, first `./gradlew :app:assembleDebug` after adding the `repeatOnLifecycle`-scoped undo collection in `DailyScreen.kt`
- **Issue:** The compiler warned `'val LocalLifecycleOwner: ProvidableCompositionLocal<LifecycleOwner>' is deprecated. Moved to lifecycle-runtime-compose library in androidx.lifecycle.compose package.` — the older `ui.platform` symbol still resolves and compiles, but is superseded.
- **Fix:** Switched the import to `androidx.lifecycle.compose.LocalLifecycleOwner`.
- **Files modified:** `app/src/main/java/com/achievementfactory/ui/daily/DailyScreen.kt`
- **Verification:** `./gradlew :app:assembleDebug` succeeds with zero deprecation warnings
- **Committed in:** `2c22331` (Task 2 commit — caught before commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 deprecation fix)
**Impact on plan:** Required to keep the build warning-free; no scope creep — no functionality added or removed beyond what Task 1–2 specified.

## Issues Encountered

None beyond the deviation above. The TDD RED phase for Task 1 was verified genuinely: `IncrementUndoTest.kt` was written and committed against the *pre-Task-1* `ProgressDao`/`AchievementRepository` (temporarily restored from their plan-01-02 state), confirmed to fail to compile (`Unresolved reference 'increment'`/`'undo'`/`'countFor'`), then the real implementation was restored and all 10 tests confirmed green before the GREEN commit.

## Known Stubs

- **`onLongPress` is wired to a no-op lambda `{}` in `AppNavHost.kt`.** Explicitly specified by the plan (D-03: long-press opens the detail screen, added in plan 01-06). The parameter exists on `AchievementCard`/`DailyScreen` now so that wiring is a one-line change then, not a signature change. Documented here per the stub-tracking requirement, not a hidden gap.

## User Setup Required

None for build/test — no external service configuration required.

**The plan's `<human-check>` step could not be completed in this sandboxed session:** "Install with `./gradlew :app:installDebug`. On a target-3 achievement, tap the card three times... On a target-1 achievement, confirm no '0/1' fraction is shown... confirm the Undo snackbar appears... Double-tap a card as fast as possible and confirm the count advances by exactly two." No `adb` device is attached in this environment (consistent with the deferred on-device checks already noted in the 01-01/01-02/01-03 summaries). All automated verification passed:
- `./gradlew clean :app:testDebugUnitTest :app:assembleDebug` — BUILD SUCCESSFUL, 47/47 tests passing, zero warnings
- Every acceptance-criteria grep in the plan (atomic-SQL substrings, no-periodKey-in-write-signatures, `@Transaction` presence, `combinedClickable`/`onLongClick`, `tween(...150...FastOutSlowInEasing...)`, no undo-in-state, `Channel`/`receiveAsFlow`, exact `"Undo"` label, `repeatOnLifecycle`, `heldBackIds`, no swipe/checkbox controls) passed exactly as specified

The on-device three-tap/undo/double-tap verification is deferred to the project owner's own hardware, same as every prior plan's device-dependent check in this phase.

## Next Phase Readiness

- The core loop is now real end to end: tap → atomic SQL write → reactive `Flow` re-render → ring sweep → (on the final tap) closed ring + checkmark → Done-section sink, with a working compare-and-set undo — everything TRACK-01 through TRACK-05 and TRACK-10 require for this phase.
- `IncrementOutcome`/`UndoToken` and the `Channel`-backed one-shot event pattern on `DailyViewModel` are the exact shapes plan 01-07's celebration-event channel will reuse (CELEB-04) — no rework anticipated.
- `DailyUiState.Content.heldBackIds` is the seam plan 01-07 needs to hold a card in outstanding for the duration of its completion animation; only that plan's celebration orchestration needs to populate it with real ids.
- `AchievementCard`'s `onLongPress` parameter is ready for plan 01-06 to wire real navigation with no signature change.
- No blockers. The on-device human-check steps from this plan (and every prior plan in this phase) remain open pending access to a physical/emulated device; nothing in the automated gates suggests a problem.

---
*Phase: 01-daily-achievements-core-loop-foundation*
*Completed: 2026-08-13*

## Self-Check: PASSED

All 3 created files plus this SUMMARY.md verified present on disk; all 3 task commits (`5502e37`, `b5c6eff`, `2c22331`) verified present in `git log`.
