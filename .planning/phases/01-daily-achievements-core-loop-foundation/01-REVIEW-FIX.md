---
phase: 01-daily-achievements-core-loop-foundation
fixed_at: 2026-08-13T13:20:00Z
review_path: .planning/phases/01-daily-achievements-core-loop-foundation/01-REVIEW.md
iteration: 1
findings_in_scope: 17
fixed: 17
skipped: 0
status: all_fixed
---

# Phase 01: Code Review Fix Report

**Fixed at:** 2026-08-13T13:20:00Z
**Source review:** `.planning/phases/01-daily-achievements-core-loop-foundation/01-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope: 17 (4 Critical, 13 Warning; the 8 Info findings were out of scope)
- Fixed: 17
- Skipped: 0

**Verification gates (all green after every commit):**
- `./gradlew :app:testDebugUnitTest` — 98 tests, 0 failures, 0 errors (baseline was 81/81)
- `./gradlew :app:assembleDebug`
- `./gradlew :app:assembleDebugAndroidTest` (compile-only; no device attached, so no instrumented
  test was executed and none is claimed to have passed)

17 unit tests were added. Two of them were explicitly proven able to fail — see CR-04.

## Fixed Issues

### CR-01: Every failure on the increment/undo write path crashes the app

**Files modified:** `app/src/main/java/com/achievementfactory/data/achievement/AchievementRepository.kt`,
`app/src/main/java/com/achievementfactory/ui/daily/DailyViewModel.kt`,
`app/src/test/java/com/achievementfactory/data/IncrementUndoTest.kt`,
`app/src/test/java/com/achievementfactory/domain/maintenance/ReconcileStreaksUseCaseTest.kt`,
`app/src/test/java/com/achievementfactory/ui/daily/DailyViewModelTest.kt`
**Commit:** `c069b19`
**Applied fix:** `AchievementRepository.increment` now returns `IncrementOutcome?` and returns
`null` for an id that no longer resolves, instead of `error(...)` — a row deleted between render
and tap is an ordinary race on a live-Room-backed list, not a programming error. `onIncrement` and
`onUndo` wrap the repository call in `runCatching` with a `Log.w`, so a `SQLiteException` drops the
tap rather than reaching the uncaught-exception handler. Three tests added, including
`onIncrement_whenTheWriteThrows_isSwallowed_andTheNextTapStillWorks`, which asserts the *next* tap
still produces its undo token — that is the part that fails if the guard is removed.

### CR-02: `onSave` crashes on a DB failure and permanently latches the save button

**Files modified:** `app/src/main/java/com/achievementfactory/ui/create/CreateAchievementViewModel.kt`,
`app/src/main/java/com/achievementfactory/ui/create/CreateAchievementUiState.kt`,
`app/src/main/java/com/achievementfactory/ui/create/CreateAchievementScreen.kt`,
`app/src/test/java/com/achievementfactory/ui/create/CreateAchievementViewModelTest.kt`
**Commit:** `5849c25`
**Applied fix:** `try/catch/finally` around the insert, with `saveInFlight = false` in the
`finally` so no failure path can leave the Save button dead. `CancellationException` is rethrown
rather than swallowed, so structured concurrency is intact.

Deviation from the review's suggested fix: it proposed reporting the failure through `nameError`.
I added a separate `saveError` field and render it above the Save button instead — a failed
database write is not a name-validation problem and should not point the user at a field that is
not what went wrong. The test asserts `nameError` stays null in that case.

### CR-03: An interrupted celebration strands the achievement in `outstanding` forever

**Files modified:** `app/src/main/java/com/achievementfactory/ui/daily/DailyViewModel.kt`,
`app/src/main/java/com/achievementfactory/ui/achievement/AchievementCard.kt`,
`app/src/main/java/com/achievementfactory/ui/theme/MotionSpecs.kt`,
`app/src/test/java/com/achievementfactory/ui/daily/CelebrationEventTest.kt`
**Commit:** `5406676`
**Applied fix:** Both halves of the reviewer's suggestion.
1. `DailyViewModel.holdBack` starts a per-id release timer bounded by
   `MotionSpecs.CELEBRATION_MAX_HOLD_MS` (2 s); `onCelebrationSettled` remains the fast path and
   cancels the timer. List correctness no longer depends on an animation completing.
2. `AchievementCard` releases on disposal via `DisposableEffect(celebration)`, which covers all
   three cited cases (rotation, `LazyColumn` item disposal, navigation) immediately rather than
   after the ceiling — and, as a side effect, fixes the inverse bug the review noted where
   scrolling back into view restarted the whole sequence on an already-completed card.

The constant lives in `MotionSpecs.kt` per the project's animation convention.

### CR-04: `Channel(Channel.BUFFERED)` replays one-shot events, contradicting CELEB-04

**Files modified:** `app/src/main/java/com/achievementfactory/ui/daily/DailyViewModel.kt`,
`app/src/main/java/com/achievementfactory/ui/celebration/CelebrationEvent.kt`,
`app/src/test/java/com/achievementfactory/ui/daily/CelebrationEventTest.kt`,
`app/src/androidTest/java/com/achievementfactory/ui/celebration/CelebrationInstrumentedTest.kt`
**Commit:** `54a1436`
**Applied fix:** Both channels replaced with `MutableSharedFlow(replay = 0, extraBufferCapacity =
8, onBufferOverflow = DROP_OLDEST)`, emitted with `tryEmit`. With `replay = 0` an emission is
dropped outright when there are no subscribers, which is what CELEB-04 actually requires; the
buffer only ever covers a *present but slow* collector (the undo collector suspends for the whole
snackbar duration), never one that has not attached yet. `tryEmit` also removes the suspending-send
wedge CR-01 mentioned. The false "a Channel keeps no buffered history" comments in
`DailyViewModel` and `CelebrationEvent` were rewritten.

**Deviation from the suggested fix, and why it matters:** the review's snippet specifies
`replay = 0, extraBufferCapacity = 0, onBufferOverflow = DROP_OLDEST`. That combination throws
`IllegalArgumentException` from the `MutableSharedFlow` constructor — a non-SUSPEND overflow
strategy requires `replay + extraBufferCapacity > 0`. And with a genuinely zero-capacity
`SharedFlow`, `tryEmit` returns `false` whenever a subscriber is present, so events would be
dropped even when someone *is* listening. `extraBufferCapacity = 8` is what makes the intended
behaviour actually hold.

**On the tests, per the explicit instruction that a test which cannot fail is worse than none:**
`lateCollector_...` and `tapPastTheTarget_...` were both vacuous — they called `expectNoEvents()`
without ever letting the scheduler dispatch the coroutine that would have delivered an event, so
they passed on any implementation. Both now drain the scheduler while subscribed. A new test,
`eventEmittedWhileNothingIsSubscribed_isNotDeliveredToTheNextCollector`, emits with nothing
subscribed and then subscribes — the exact `repeatOnLifecycle(STARTED)` window.

I verified these are not vacuous by temporarily flipping `replay` to 1 and re-running:
`eventEmittedWhileNothingIsSubscribed_...` and `lateCollector_...` both FAILED, then passed again
once reverted. The instrumented suite gained
`recreatingTheActivityMidCelebration_doesNotReplayTheCelebrationAfterwards`, which recreates the
Activity *without* first waiting for the sequence to settle — the blind spot the review identified.
It compiles; it has not been executed, because no device or emulator is attached.

### WR-01: `rememberReducedMotion()` remembers nothing

**Files modified:** `app/src/main/java/com/achievementfactory/ui/theme/MotionSpecs.kt`
**Commit:** `7214399`
**Applied fix:** Wrapped in `remember(resolver)`, keyed on the `ContentResolver`. The KDoc's false
"reads the system setting once per composition" claim was replaced with what the code now does,
including an explicit note that the value does not react live and what to do if that is ever wanted.

### WR-02: Write path and read path resolve "today" from two different sources

**Files modified:** `app/src/main/java/com/achievementfactory/data/achievement/AchievementRepository.kt`,
`app/src/main/java/com/achievementfactory/period/CurrentPeriodProvider.kt`,
`app/src/main/java/com/achievementfactory/AppContainer.kt`, plus the four test files that
construct the repository
**Commit:** `79d1d7c`
**Applied fix:** New private `resolveTodayKeyForWrite()` calls `currentPeriodProvider.refresh()`
and then reads `currentDailyKey`. `refresh()` recomputes from the live clock, so writes still
resolve today at the moment of the write (TRACK-05/TRACK-10 and D-28 unaffected, and an increment
still cannot land anywhere but the current period) — but the read flows are now keyed to the same
day by construction. The now-unused `periodEngine` constructor parameter was removed.
`CurrentPeriodProvider`'s KDoc, which asserted the opposite, was corrected. New regression test
`increment_afterMidnightWithNoRefreshSignal_isVisibleToTheAlreadyCollectedReadFlow` in
`RolloverTest` reproduces the window with no `refresh()` call of its own.

### WR-03: Two concurrent full reconciliation passes on every cold start

**Files modified:** `app/src/main/java/com/achievementfactory/AchievementFactoryApp.kt`,
`app/src/main/java/com/achievementfactory/AppContainer.kt`
**Commit:** `8becf71`
**Applied fix:** Deleted the explicit `applicationScope.launch { runAll(...) }` from `onCreate`.
`PeriodWatcher`'s `ProcessLifecycleOwner.onStart` observer already covers "app opened", which is
what PERIOD-05 asks for. Both KDocs updated to say so, including a note to serialize with a `Mutex`
rather than reintroduce a second unguarded launch.

### WR-04: `PeriodWatcher.stop()` is dead code and unsafe to call

**Files modified:** `app/src/main/java/com/achievementfactory/period/PeriodWatcher.kt`
**Commit:** `68cc3bd`
**Applied fix:** Took the reviewer's first option and deleted `stop()` (grep-confirmed zero call
sites). Registration for the process lifetime is the intended design, so this is the honest
version. `start()`'s KDoc now explains why there is no `stop()` and what a future one would need.

### WR-05: Reconciliation hardcodes DAILY and ignores `periodType` / `archived`

**Files modified:** `app/src/main/java/com/achievementfactory/domain/maintenance/ReconcileStreaksUseCase.kt`,
`app/src/main/java/com/achievementfactory/data/achievement/AchievementDao.kt`,
`app/src/test/java/com/achievementfactory/domain/maintenance/ReconcileStreaksUseCaseTest.kt`
**Commit:** `26a115a`
**Applied fix:** `staleAchievementIds` now filters on `periodType = :periodType AND archived = 0`,
and `runAll`/`runFor` take a `keyPeriodType` (defaulting to DAILY, so no call site changed).

Deviation from the suggested fix: the review proposed simply passing the entity's `periodType`
through to the calculator. On its own that would be *incoherent* today — `currentKey` is always a
DAILY key at every call site, so a WEEKLY row would get weekly math against a daily key. Instead
`runFor` parses the stored value tolerantly and **skips** any row whose recurrence unit does not
match the key it was handed, with a comment stating that Phase 2 must resolve a `currentKey` per
unit. A visibly stale cache is a much better failure than a confidently wrong streak. Two tests
added covering the archived case and the mismatched-period-type case.

### WR-06: The counter-target field silently rewrites and cannot be cleared

**Files modified:** `app/src/main/java/com/achievementfactory/ui/create/CreateAchievementUiState.kt`,
`app/src/main/java/com/achievementfactory/ui/create/CreateAchievementViewModel.kt`,
`app/src/main/java/com/achievementfactory/ui/create/AdvancedSection.kt`,
`app/src/main/java/com/achievementfactory/ui/create/CreateAchievementScreen.kt`,
`app/src/test/java/com/achievementfactory/ui/create/CreateAchievementViewModelTest.kt`
**Commit:** `a16bd90`
**Applied fix:** State holds `targetInput: String`; `target: Int` is a derived property coercing
into 1..999 at read time. `onTargetChange` rejects non-digits and over-long input outright rather
than substituting a different number. The field's supporting text now states the range. Existing
tests still assert against `state.target` unchanged; three new tests cover clear-and-retype, the
reject-rather-than-rewrite behaviour, and the 999 cap reaching the repository.

### WR-07: `allowBackup="false"` alone does not block device-to-device transfer

**Files modified:** `app/src/main/AndroidManifest.xml`,
`app/src/main/res/xml/data_extraction_rules.xml` (new),
`app/src/test/java/com/achievementfactory/LocalOnlyBoundaryTest.kt` (new)
**Commit:** `c096c31`
**Applied fix:** Added the extraction-rules resource excluding every domain from both
`<cloud-backup>` and `<device-transfer>`, and referenced it from the manifest. Verified present in
the merged manifest. Also added `LocalOnlyBoundaryTest`, which fails the build if a
`<uses-permission>` ever appears, if `allowBackup` stops being false, or if the extraction rules
stop excluding a domain — turning the hardest product boundary in this project into a gate rather
than a line in a document.

### WR-08: Rapid taps push duplicate navigation destinations

**Files modified:** `app/src/main/java/com/achievementfactory/ui/nav/AppNavHost.kt`
**Commit:** `4041f5b`
**Applied fix:** Added the standard `NavHostController.navigateOnce` lifecycle guard and routed
both the FAB and the long-press through it.

### WR-09: `increment` is four separate un-transacted round trips, and its KDoc overclaims

**Files modified:** `app/src/main/java/com/achievementfactory/data/achievement/AchievementRepository.kt`,
`app/src/main/java/com/achievementfactory/AppContainer.kt`, plus the four test files that
construct the repository
**Commit:** `855d3c9`
**Applied fix:** `AppDatabase` is now injected and both `increment` and `undo` wrap their counter
write plus forced reconcile in `database.withTransaction { }`, so process death between the two
can no longer leave `streakCachedForPeriodKey` stamped with a value the log does not support. The
KDoc no longer claims `newStreak` "can never be racing the same write" — it now states precisely
what holds (a post-reconcile read inside this call's own transaction; correct under concurrency
because both reconciles compute from the same log, but not an exclusively-owned snapshot).

`resolveTodayKeyForWrite()` is deliberately called *before* the transaction opens: `refresh()`
assigns a `StateFlow` that read flows are subscribed to, and firing that while holding a write
transaction would have collectors querying a database they cannot read from yet.

The atomicity invariants are intact — `incrementAndRead` is still the same compare-and-set SQL,
now nested inside an outer transaction. `fiftyConcurrentIncrements_produceExactlyFifty` still
passes.

### WR-10: An unrecognized `accentSwatch` value permanently kills the Daily tab

**Files modified:** `app/src/main/java/com/achievementfactory/data/achievement/AchievementRepository.kt`,
`app/src/test/java/com/achievementfactory/data/AchievementRepositoryTest.kt`
**Commit:** `fa16a28`
**Applied fix:** Both mapping sites use a new `swatchOf(name)` that falls back to
`AccentSwatch.INDIGO`. Two tests added, one per read flow, using the exact `'BLUE'` value the
project's own migration fixture used to write.

### WR-11: `SchemaExportTest`'s git check passes on every failure

**Files modified:** `app/src/test/java/com/achievementfactory/data/SchemaExportTest.kt`
**Commit:** `3f5e11f`
**Applied fix:** The catch is narrowed to `IOException` from `ProcessBuilder.start` — the only
failure that genuinely means "no git binary". Git present-but-failing, `SecurityException`, and an
interrupted wait now all fail the test. The merged output stream is drained before `waitFor()`, so
a verbose git error can no longer deadlock the test until the JUnit timeout.

### WR-12: `androidx.core` is used in production code but never declared

**Files modified:** `gradle/libs.versions.toml`, `app/build.gradle.kts`
**Commit:** `a9629e6`
**Applied fix:** Added `androidx-core-ktx` to the version catalog and `implementation(...)` to the
module. Version pinned to `1.18.0`, which is what the dependency graph already resolves to today
(confirmed via `:app:dependencies --configuration debugRuntimeClasspath`), so this declares the
existing reality rather than silently changing it.

### WR-13: `MigrationTest` seeds a row that the application can never produce or read

**Files modified:** `app/src/androidTest/java/com/achievementfactory/data/MigrationTest.kt`
**Commit:** `50cedca`
**Applied fix:** Fixture changed from `'BLUE'` to `'SKY'`, plus assertions that the round-tripped
row's `accentSwatch` and `periodType` are actual enum members — which is what keeps the fixture
realistic as those enums evolve.

Deviation from the suggested fix: the review asked for an assertion that the row round-trips
through `AchievementRepository`. After WR-10 that mapping deliberately no longer throws on an
unrecognized name (it falls back), so a repository round-trip would pass for `'BLUE'` too and
would not keep the fixture honest. The enum-membership assertions do. This is androidTest, so it
was compiled (`assembleDebugAndroidTest`) but not executed.

## Skipped Issues

None.

## Notes for the human

**One follow-up I deliberately did not make.** `CLAUDE.md`'s "State versus events" convention
block still reads:

> One-shot signals [...] travel on a dedicated `Channel(Channel.BUFFERED)` exposed as
> `.receiveAsFlow()` [...] A `Channel` holds no buffered history, so a collector that subscribes
> after rotation or process restoration receives nothing.

That second sentence is the exact false claim CR-04 is about, and the first now contradicts the
code. It needs to be rewritten to describe the zero-replay `SharedFlow` pattern, since it is the
rule every later phase's one-shot signal is supposed to follow. I did not edit it: `CLAUDE.md` is
project configuration, and that block is generated from `.planning/CONVENTIONS.md`, so it should be
changed at the source by you rather than by me. **Phase 2 will otherwise copy the broken pattern.**

**The 8 Info findings (IN-01 … IN-08) were out of scope** for this pass (`fix_scope:
critical_warning`) and remain open. IN-06 and IN-07 are worth a look — IN-07 is another stale
comment that now asserts the opposite of the code, and this review's own summary calls that class
of comment out as the phase's recurring problem.

**Not verifiable in this environment:** no device or emulator is attached, so every instrumented
test — including the new mid-celebration recreation case — has only been compiled. The on-device
pass at end of phase still needs to run them.

---

_Fixed: 2026-08-13T13:20:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
