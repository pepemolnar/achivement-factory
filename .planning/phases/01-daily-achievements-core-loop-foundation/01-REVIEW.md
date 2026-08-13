---
phase: 01-daily-achievements-core-loop-foundation
reviewed: 2026-08-13T11:39:49Z
depth: standard
files_reviewed: 58
files_reviewed_list:
  - app/build.gradle.kts
  - app/schemas/com.achievementfactory.data.AppDatabase/1.json
  - app/src/androidTest/java/com/achievementfactory/data/MigrationTest.kt
  - app/src/androidTest/java/com/achievementfactory/ui/celebration/CelebrationInstrumentedTest.kt
  - app/src/main/AndroidManifest.xml
  - app/src/main/java/com/achievementfactory/AchievementFactoryApp.kt
  - app/src/main/java/com/achievementfactory/AppContainer.kt
  - app/src/main/java/com/achievementfactory/data/AppDatabase.kt
  - app/src/main/java/com/achievementfactory/data/achievement/AccentSwatch.kt
  - app/src/main/java/com/achievementfactory/data/achievement/AchievementDao.kt
  - app/src/main/java/com/achievementfactory/data/achievement/AchievementEntity.kt
  - app/src/main/java/com/achievementfactory/data/achievement/AchievementRepository.kt
  - app/src/main/java/com/achievementfactory/data/progress/ProgressDao.kt
  - app/src/main/java/com/achievementfactory/data/progress/ProgressEntity.kt
  - app/src/main/java/com/achievementfactory/domain/maintenance/ReconcileStreaksUseCase.kt
  - app/src/main/java/com/achievementfactory/domain/model/AchievementDetail.kt
  - app/src/main/java/com/achievementfactory/domain/model/DailyAchievement.kt
  - app/src/main/java/com/achievementfactory/domain/model/IncrementOutcome.kt
  - app/src/main/java/com/achievementfactory/domain/model/UndoToken.kt
  - app/src/main/java/com/achievementfactory/domain/streak/StreakCalculator.kt
  - app/src/main/java/com/achievementfactory/domain/streak/StreakSnapshot.kt
  - app/src/main/java/com/achievementfactory/period/AppClock.kt
  - app/src/main/java/com/achievementfactory/period/CurrentPeriodProvider.kt
  - app/src/main/java/com/achievementfactory/period/PeriodEngine.kt
  - app/src/main/java/com/achievementfactory/period/PeriodKey.kt
  - app/src/main/java/com/achievementfactory/period/PeriodType.kt
  - app/src/main/java/com/achievementfactory/period/PeriodWatcher.kt
  - app/src/main/java/com/achievementfactory/ui/MainActivity.kt
  - app/src/main/java/com/achievementfactory/ui/achievement/AchievementCard.kt
  - app/src/main/java/com/achievementfactory/ui/achievement/ProgressRing.kt
  - app/src/main/java/com/achievementfactory/ui/achievement/StreakFlameBadge.kt
  - app/src/main/java/com/achievementfactory/ui/celebration/CelebrationEvent.kt
  - app/src/main/java/com/achievementfactory/ui/celebration/CelebrationStage.kt
  - app/src/main/java/com/achievementfactory/ui/celebration/CompletionCelebration.kt
  - app/src/main/java/com/achievementfactory/ui/create/AccentSwatchPicker.kt
  - app/src/main/java/com/achievementfactory/ui/create/AdvancedSection.kt
  - app/src/main/java/com/achievementfactory/ui/create/CreateAchievementScreen.kt
  - app/src/main/java/com/achievementfactory/ui/create/CreateAchievementUiState.kt
  - app/src/main/java/com/achievementfactory/ui/create/CreateAchievementViewModel.kt
  - app/src/main/java/com/achievementfactory/ui/daily/DailyScreen.kt
  - app/src/main/java/com/achievementfactory/ui/daily/DailyUiState.kt
  - app/src/main/java/com/achievementfactory/ui/daily/DailyViewModel.kt
  - app/src/main/java/com/achievementfactory/ui/detail/AchievementDetailScreen.kt
  - app/src/main/java/com/achievementfactory/ui/detail/AchievementDetailUiState.kt
  - app/src/main/java/com/achievementfactory/ui/detail/AchievementDetailViewModel.kt
  - app/src/main/java/com/achievementfactory/ui/nav/AppNavHost.kt
  - app/src/main/java/com/achievementfactory/ui/theme/AccentPalette.kt
  - app/src/main/java/com/achievementfactory/ui/theme/MotionSpecs.kt
  - app/src/main/java/com/achievementfactory/ui/theme/Theme.kt
  - app/src/test/java/com/achievementfactory/data/AchievementRepositoryTest.kt
  - app/src/test/java/com/achievementfactory/data/IncrementUndoTest.kt
  - app/src/test/java/com/achievementfactory/data/RolloverTest.kt
  - app/src/test/java/com/achievementfactory/data/SchemaExportTest.kt
  - app/src/test/java/com/achievementfactory/domain/maintenance/ReconcileStreaksUseCaseTest.kt
  - app/src/test/java/com/achievementfactory/domain/streak/StreakCalculatorTest.kt
  - app/src/test/java/com/achievementfactory/period/CurrentPeriodProviderTest.kt
  - app/src/test/java/com/achievementfactory/period/PeriodEngineTest.kt
  - app/src/test/java/com/achievementfactory/ui/achievement/AchievementCardTest.kt
  - app/src/test/java/com/achievementfactory/ui/create/CreateAchievementViewModelTest.kt
  - app/src/test/java/com/achievementfactory/ui/daily/CelebrationEventTest.kt
  - app/src/test/java/com/achievementfactory/ui/daily/DailyViewModelTest.kt
  - app/src/test/java/com/achievementfactory/ui/detail/AchievementDetailViewModelTest.kt
  - docs/RELEASE.md
  - gradle/libs.versions.toml
findings:
  critical: 4
  warning: 13
  info: 8
  total: 25
status: issues_found
---

# Phase 01: Code Review Report

**Reviewed:** 2026-08-13T11:39:49Z
**Depth:** standard
**Files Reviewed:** 58
**Status:** issues_found

## Summary

The data layer is the strongest part of this phase. The counter path really is compare-and-set
SQL (`ProgressDao.incrementAtomic` / `undoIfUnchanged`), the increment/read-back is genuinely
transactional, streaks are derived from the completion log rather than incremented, no
destructive-migration fallback exists anywhere, the schema export is committed and guarded, and
the manifest declares no network permission. Period math is `java.time`-only with Monday
hardcoded and is well covered by locale/DST/year-boundary tests.

The defects concentrate in the **write path's error handling** and in the **celebration /
one-shot-event machinery**, both of which are protected mainly by prose comments rather than by
code. Three specific themes:

1. **No write path anywhere handles failure.** Every `viewModelScope.launch { repository.… }` in
   this phase is naked. `AchievementRepository.increment` throws `IllegalStateException` by
   design for an unknown id, and any DAO call can throw `SQLiteException`. Either one takes the
   process down. The read paths carefully `.catch { Error }`; the write paths do nothing.
2. **The "one-shot events can never replay" invariant is documented, tested, and false.**
   `Channel(Channel.BUFFERED)` retains up to 64 undelivered elements; combined with
   `repeatOnLifecycle(STARTED)` collection, any event produced while the UI is stopped is
   delivered later. The instrumented test that claims to prove this waits for the celebration to
   settle first, so it cannot catch the case.
3. **The held-back set has no release path other than a successful animation.** If the
   celebrating card leaves composition before `SETTLED` (rotation, scroll-out of the
   `LazyColumn`, navigation), the id stays in `_heldBackIds` for the ViewModel's whole lifetime
   and the completed achievement never sinks into Done.

Comments throughout assert invariants ("never", "structural", "can never disagree") that the code
does not actually enforce. Several are demonstrably wrong (`DailyUiState.kt:26`,
`CelebrationEvent.kt:5`, `IncrementOutcome.kt:8`). Comments that overstate guarantees are worse
than no comment because the next reader will trust them.

## Critical Issues

### CR-01: Every failure on the increment/undo write path crashes the app

**File:** `app/src/main/java/com/achievementfactory/ui/daily/DailyViewModel.kt:62-84`
(with `app/src/main/java/com/achievementfactory/data/achievement/AchievementRepository.kt:135-136`)

**Issue:** `onIncrement` and `onUndo` launch into `viewModelScope` with no error handling.
`AchievementRepository.increment` deliberately throws on an unknown id
(`?: error("Cannot increment unknown achievement id=$achievementId")`), and every DAO call in
`increment`/`undo` can throw `SQLiteException` (disk full, corrupted DB, DB closed). An
exception in a `viewModelScope` coroutine is not caught by `uiState`'s `.catch` — that only
covers the *read* flow — so it propagates to the default uncaught-exception handler and kills
the process. On a device-local app with `allowBackup="false"` and no cloud restore, a crash loop
on the primary interaction is the worst available failure mode.

Note also that `_undoRequests.send(...)`/`_celebrations.send(...)` are suspending sends on a
bounded channel; if the buffer ever fills (see CR-04) these suspend indefinitely inside
`viewModelScope`, silently wedging all subsequent taps.

**Fix:**
```kotlin
fun onIncrement(id: Long) {
    viewModelScope.launch {
        val outcome = runCatching { repository.increment(id) }
            .onFailure { /* surface a transient error state or log */ }
            .getOrNull() ?: return@launch
        _undoRequests.trySend(outcome.undoToken)
        if (outcome.justCompleted) {
            _heldBackIds.update { it + id }
            _celebrations.trySend(
                CelebrationEvent.Completed(id, outcome.newStreak, outcome.newStreak == 1),
            )
        }
    }
}

fun onUndo(token: UndoToken) {
    viewModelScope.launch { runCatching { repository.undo(token) } }
}
```
Also change `AchievementRepository.increment` to return a typed failure (or a nullable
`IncrementOutcome`) instead of `error(...)` for the "unknown id" case — a row that vanished
between render and tap is a normal race, not a programming error.

### CR-02: `onSave` crashes on a DB failure and permanently latches the save button

**File:** `app/src/main/java/com/achievementfactory/ui/create/CreateAchievementViewModel.kt:76-81`

**Issue:** Two defects in five lines:
1. `repository.create(...)` is unguarded inside `viewModelScope.launch` — same process-kill path
   as CR-01.
2. `saveInFlight = true` is set before the launch but reset only on the success path. There is no
   `try/finally`. If `create` throws (or the coroutine is cancelled — e.g. the user backs out of
   the screen while the insert is in flight, clearing the ViewModel), `saveInFlight` stays
   `true`. Because `onSave` short-circuits on `if (saveInFlight …) return`, the Save button is
   dead for the rest of that ViewModel's life with no visible feedback: the user taps Save,
   nothing happens, forever.

**Fix:**
```kotlin
saveInFlight = true
viewModelScope.launch {
    try {
        repository.create(trimmedName, current.icon, current.accent, current.target)
        _uiState.update { it.copy(saved = true) }
    } catch (e: Exception) {
        _uiState.update { it.copy(nameError = "Couldn't save. Try again.") }
    } finally {
        saveInFlight = false
    }
}
```

### CR-03: An interrupted celebration strands the achievement in `outstanding` forever

**Files:**
- `app/src/main/java/com/achievementfactory/ui/daily/DailyViewModel.kt:39, 66-75, 92-94`
- `app/src/main/java/com/achievementfactory/ui/daily/DailyScreen.kt:74, 124-127`
- `app/src/main/java/com/achievementfactory/ui/achievement/AchievementCard.kt:104-107`
- `app/src/main/java/com/achievementfactory/ui/celebration/CompletionCelebration.kt:62-106`

**Issue:** `_heldBackIds` is added to on `justCompleted` and removed from **only** by
`onCelebrationSettled`, which is only ever called from `CompletionCelebration`'s
`LaunchedEffect` reaching `CelebrationStage.SETTLED`. That effect is tied to the card's
composition, and the sequence takes ~700 ms plus spring settle time. Any of the following
cancels it before `SETTLED`:

- Device rotation / `Activity` recreation during the animation (`DailyScreen`'s
  `activeCelebrations` is `remember`, not `rememberSaveable`, so it is lost — but `_heldBackIds`
  lives in the `ViewModel` and survives).
- The celebrating card scrolling out of the `LazyColumn` viewport (item disposal).
- Navigating to the create/detail destination during the animation.

Result: the id is never released. `DailyUiState.Content.partition` then keeps a **done**
achievement in the `outstanding` group indefinitely — it renders with a full ring but sits above
the Done divider, and the user has no way to clear it short of killing the process. In the
scroll-out case the inverse also happens: scrolling back re-enters composition, `remember(event)`
recreates the `Animatable`s and `LaunchedEffect(event)` restarts, so the whole celebration
replays for an already-completed card.

**Fix:** Do not make correctness of the list depend on an animation completing. Give the
held-back entry a self-expiring lifetime in the ViewModel and keep `onCelebrationSettled` as the
fast path:
```kotlin
fun onIncrement(id: Long) {
    viewModelScope.launch {
        …
        if (outcome.justCompleted) {
            _heldBackIds.update { it + id }
            launch {
                delay(CELEBRATION_MAX_HOLD_MS) // hard ceiling, e.g. 2_000
                _heldBackIds.update { it - id }
            }
            …
        }
    }
}
```
and, in `AchievementCard`, also release on disposal so a cancelled sequence settles immediately:
```kotlin
DisposableEffect(celebration) {
    onDispose { if (celebration != null) onCelebrationSettled() }
}
```

### CR-04: `Channel(Channel.BUFFERED)` replays one-shot events, contradicting CELEB-04

**Files:**
- `app/src/main/java/com/achievementfactory/ui/daily/DailyViewModel.kt:48-59`
- `app/src/main/java/com/achievementfactory/ui/daily/DailyScreen.kt:76-99`
- `app/src/androidTest/java/com/achievementfactory/ui/celebration/CelebrationInstrumentedTest.kt:59-82`

**Issue:** The comments claim "a Channel keeps no buffered history, so a snackbar never re-shows
on rotation/process restoration" (DailyViewModel.kt:48-50, repeated at 54-57 and in
`CelebrationEvent.kt:5`). That is not what `Channel(Channel.BUFFERED)` does — it is a
64-element buffer of *undelivered* elements, and `receiveAsFlow` hands every buffered element to
the next collector. `DailyScreen` collects inside `repeatOnLifecycle(Lifecycle.State.STARTED)`,
so collection stops whenever the screen is stopped.

Reproducible sequence: tap a card to complete it, then immediately rotate the device (or press
Home). The DB write and `send` happen asynchronously in `viewModelScope`, which outlives the
recreation. The collector is gone at `send` time, so the event is buffered; the new collector
attaches after recreation and receives it. The full celebration replays on an
already-completed card, and the "Progress updated / Undo" snackbar re-raises for a tap the user
made before the rotation.

`CelebrationInstrumentedTest` cannot catch this: `createAndCompleteAchievement()` waits for the
celebration to fully settle *before* calling `scenario.recreate()`, i.e. it only tests the case
where the buffer is already drained. `CelebrationEventTest.lateCollector_…` has the same blind
spot (it awaits the item before re-subscribing).

**Fix:** Use an event stream that drops when nobody is listening, and a non-suspending emit:
```kotlin
private val _celebrations = MutableSharedFlow<CelebrationEvent>(
    replay = 0,
    extraBufferCapacity = 0,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
)
val celebrations: SharedFlow<CelebrationEvent> = _celebrations.asSharedFlow()
// emit with _celebrations.tryEmit(event) — returns false (drops) when there is no subscriber
```
Same change for `_undoRequests`. Then extend the instrumented test to rotate *during* the
sequence (before the celebrating node disappears) and assert no replay.

## Warnings

### WR-01: `rememberReducedMotion()` remembers nothing and does a Settings read every frame

**File:** `app/src/main/java/com/achievementfactory/ui/theme/MotionSpecs.kt:79-87`

**Issue:** Despite the `remember` prefix (a strong Compose naming convention meaning "cached
across recomposition") the function has no `remember` at all — it calls
`Settings.Global.getFloat(context.contentResolver, …)` on every invocation. `CompletionCelebration`
calls it in its composable body, and that body reads five `Animatable.value`s, so it recomposes
on **every animation frame**; each frame therefore performs a `ContentResolver` settings lookup
on the main thread. `DailyList` calls it too, on every list recomposition. The KDoc's claim
"Reads the system setting once per composition" is false.

**Fix:**
```kotlin
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
```
(If live reaction to the setting is wanted, back it with a `ContentObserver` in a
`produceState`/`DisposableEffect` instead of polling.)

### WR-02: Write path and read path resolve "today" from two different sources

**Files:** `app/src/main/java/com/achievementfactory/data/achievement/AchievementRepository.kt:137, 168`
vs `:46, 77`

**Issue:** Reads hang off `currentPeriodProvider.currentDailyKey`, which only changes when
`refresh()` is called (a system broadcast or process resume). Writes call
`periodEngine.current(PeriodType.DAILY)`, which reads the live clock every time. Between real
midnight and the arrival of `ACTION_DATE_CHANGED` (or the next `onStart`), the two disagree: a
tap writes a progress row keyed to the **new** day while `observeDaily` is still joined against
the **old** day's key. The user sees the tap do nothing (count stays at yesterday's value), the
undo snackbar carries a token for a row the UI never shows, and a completion celebration can fire
for a card that visibly stays incomplete. The comment at `CurrentPeriodProvider.kt:12-14` frames
this divergence as deliberate but does not acknowledge the inconsistency window it creates.

**Fix:** Have the write path refresh the provider first and then use the provider's key, so read
and write agree by construction:
```kotlin
suspend fun increment(achievementId: Long): IncrementOutcome {
    currentPeriodProvider.refresh()
    val todayKey = currentPeriodProvider.currentDailyKey.value
    …
}
```
(`refresh()` is a `MutableStateFlow` assignment — cheap, and a no-op when the day is unchanged.)

### WR-03: Two concurrent full reconciliation passes on every cold start

**Files:** `app/src/main/java/com/achievementfactory/AchievementFactoryApp.kt:22-25`,
`app/src/main/java/com/achievementfactory/period/PeriodWatcher.kt:38-45, 55`

**Issue:** `onCreate` calls `periodWatcher.start()` (which registers a `DefaultLifecycleObserver`
whose `onStart` runs `refresh()` + `runAll` as soon as the first Activity starts) *and then*
launches `reconcileStreaks.runAll(...)` itself. On every cold start both run, concurrently, on
the multi-threaded `Dispatchers.Default`, over the same rows. `runFor` is idempotent so the
result is correct, but every stale achievement is read and rewritten twice, and the two passes
can interleave `staleAchievementIds` against each other's writes.

**Fix:** Delete the explicit `applicationScope.launch { … runAll … }` in `onCreate` — the
`ProcessLifecycleOwner` `onStart` signal already covers "app opened", which is exactly what
PERIOD-05 asks for. If a pre-first-Activity pass is genuinely wanted, guard `onSignal` with a
`Mutex` or a conflated actor so overlapping passes collapse into one.

### WR-04: `PeriodWatcher.stop()` is dead code and unsafe to call

**File:** `app/src/main/java/com/achievementfactory/period/PeriodWatcher.kt:58-62`

**Issue:** `stop()` has no call site anywhere in the repository (verified by grep across
`app/src`). Its KDoc — "nothing is left listening once the process stops" — describes behavior
nothing implements. It is also not idempotent: calling it twice, or before `start()`, throws
`IllegalArgumentException("Receiver not registered")` from `unregisterReceiver`, and it must be
called on the main thread because of `removeObserver`.

**Fix:** Either delete `stop()` (registration for the process lifetime is the intended design,
so this is honest), or make it safe and actually wire it:
```kotlin
private var started = false
fun stop() {
    if (!started) return
    started = false
    runCatching { context.unregisterReceiver(receiver) }
    ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
}
```

### WR-05: Reconciliation hardcodes DAILY and ignores `periodType` / `archived`

**Files:** `app/src/main/java/com/achievementfactory/domain/maintenance/ReconcileStreaksUseCase.kt:44-65`,
`app/src/main/java/com/achievementfactory/data/achievement/AchievementDao.kt:35-39`

**Issue:** `runFor` loads the full `AchievementEntity` (line 45) and then discards its
`periodType`, passing `PeriodType.DAILY` unconditionally to the calculator (line 52).
`staleAchievementIds` selects **every** row in `achievements` with no `periodType` and no
`archived = 0` filter. Today that is harmless because only DAILY rows exist and nothing is
archived — but `PeriodType.WEEKLY`/`MONTHLY` are already implemented and documented as
"turned on in Phase 2 without touching this package", which is exactly the change that will
silently compute weekly streaks with daily period math (`previous()` stepping one day at a time
over Monday-anchored keys, producing `current = 1` forever). Archived rows also get pointlessly
rewritten on every pass.

**Fix:**
```kotlin
val achievement = achievementDao.getById(achievementId) ?: return
…
val snapshot = streakCalculator.calculate(
    doneKeys = doneKeys,
    currentKey = currentKey,
    periodType = PeriodType.valueOf(achievement.periodType),
    storedBest = achievement.bestStreak,
)
```
and add `AND archived = 0` to `staleAchievementIds`. (Once period types diverge, `currentKey`
must also be resolved per period type rather than passed as a single daily key.)

### WR-06: The counter-target field silently rewrites and cannot be cleared

**Files:** `app/src/main/java/com/achievementfactory/ui/create/AdvancedSection.kt:65-73`,
`app/src/main/java/com/achievementfactory/ui/create/CreateAchievementViewModel.kt:52-55`

**Issue:** `CounterTargetField` is a fully controlled field (`value = target.toString()`) whose
`onValueChange` coerces anything unparseable to `1`. Consequences:
- Deleting the contents to type a new number immediately snaps the field back to `"1"`; the user
  then types `5` and gets `15`. There is no way to type an arbitrary target without
  select-all-then-replace.
- Any value above `Int.MAX_VALUE` (e.g. a long paste, or `99999999999`) makes `toIntOrNull()`
  return null and is silently rewritten to `1` — the opposite of what the user asked for, with
  no error shown.
- There is no upper bound, so a target of `2_000_000_000` is accepted and produces an
  effectively uncompletable achievement.

**Fix:** Hold the raw text in `CreateAchievementUiState` and only coerce at save time:
```kotlin
data class CreateAchievementUiState(…, val targetInput: String = "1", …) {
    val target: Int get() = targetInput.toIntOrNull()?.coerceIn(1, 999) ?: 1
}
fun onTargetChange(raw: String) {
    if (raw.all(Char::isDigit) && raw.length <= 3) _uiState.update { it.copy(targetInput = raw) }
}
```

### WR-07: `allowBackup="false"` alone does not block device-to-device transfer

**File:** `app/src/main/AndroidManifest.xml:6`

**Issue:** The stated product boundary is "everything lives on one device, no sync". For apps
targeting Android 12+ (this app targets 36), `android:allowBackup="false"` disables cloud
backup but **not** device-to-device transfer — D2D is governed separately by
`android:dataExtractionRules`. Without that attribute the Room database can be copied to a new
phone during setup transfer, quietly violating the boundary `docs/RELEASE.md` treats as absolute
("there is no cloud restore path if the local database is ever wiped").

**Fix:** Add an extraction-rules resource and reference it:
```xml
<!-- res/xml/data_extraction_rules.xml -->
<data-extraction-rules>
    <cloud-backup><exclude domain="root" /></cloud-backup>
    <device-transfer><exclude domain="root" /></device-transfer>
</data-extraction-rules>
```
```xml
<application
    android:allowBackup="false"
    android:dataExtractionRules="@xml/data_extraction_rules"
    … >
```

### WR-08: Rapid taps push duplicate navigation destinations

**File:** `app/src/main/java/com/achievementfactory/ui/nav/AppNavHost.kt:38, 42`

**Issue:** `navController.navigate(Routes.CREATE)` and `navigate(Routes.detailOf(id))` are called
straight from click callbacks with no debounce and no lifecycle guard. A double-tap on the FAB
within one frame enqueues two `navigate` calls before the destination changes, pushing two
`create` entries onto the back stack — the user then has to press Back twice to return to Daily,
and two independent `CreateAchievementViewModel`s exist. Same for long-pressing two cards in
quick succession.

**Fix:** Guard on the current entry's lifecycle state (the standard Navigation-Compose idiom):
```kotlin
fun NavHostController.navigateOnce(route: String) {
    if (currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) {
        navigate(route)
    }
}
```

### WR-09: `increment` is four separate un-transacted round trips, and its KDoc overclaims

**File:** `app/src/main/java/com/achievementfactory/data/achievement/AchievementRepository.kt:134-154`

**Issue:** `increment` performs `getById` → `incrementAndRead` (transactional) →
`reconcileStreaks.runFor` (a read + an update) → `getById` as four independent database
operations. Only the middle one is atomic. Two consequences:
1. The KDoc at lines 143-145 claims `newStreak` "is always this call's own true post-write
   value — never a … snapshot that could still be racing the same write." Under two concurrent
   taps that is false: tap A's final `getById` can observe the cache tap B's reconcile wrote.
   The value is not *wrong* (both reconciles compute from the same log), but the stated
   guarantee is stronger than the code delivers.
2. A crash or process death between the counter write and the reconcile leaves
   `streakCachedForPeriodKey` stamped with a value that no longer matches the log. This
   self-heals on the next app open only because `runAll`'s staleness check happens to catch it —
   but if the cache was already stamped for today (the exact situation `force` exists for), the
   guard skips it and the stale streak persists for the rest of the day.

**Fix:** Wrap the counter write and the reconcile in one `database.withTransaction { … }` at the
repository level (inject the `AppDatabase` or a transaction runner), and soften the KDoc to
describe what is actually guaranteed.

### WR-10: An unrecognized `accentSwatch` value permanently kills the Daily tab

**Files:** `app/src/main/java/com/achievementfactory/data/achievement/AchievementRepository.kt:58, 88`,
`app/src/main/java/com/achievementfactory/ui/daily/DailyViewModel.kt:41-46`

**Issue:** `AccentSwatch.valueOf(achievement.accentSwatch)` throws `IllegalArgumentException` for
any string not in the enum. The column is plain `TEXT` with no `CHECK` constraint, and any future
schema migration, manual DB edit, or removed/renamed swatch produces such a row. The throw
propagates into `DailyViewModel.uiState`'s `.catch`, which emits `Error` — and because `catch`
*terminates* the upstream flow, the Daily tab is then permanently stuck showing "Restart the app
to try again" for as long as that row exists. Restarting will not help. The project's own test
fixture already writes an invalid value (`'BLUE'`, see WR-13), which shows how easy this is to
produce.

**Fix:** Fail soft at the mapping boundary:
```kotlin
private fun swatchOf(name: String): AccentSwatch =
    AccentSwatch.entries.firstOrNull { it.name == name } ?: AccentSwatch.INDIGO
```

### WR-11: `SchemaExportTest`'s git check passes on every failure

**File:** `app/src/test/java/com/achievementfactory/data/SchemaExportTest.kt:83-94`

**Issue:** `isTrackedByGit` returns `true` from a bare `catch (_: Exception)`, so *any* problem —
git missing, git failing, a `SecurityException` from the process spawn, an interrupted wait —
makes the assertion vacuously pass. The comment justifies this for "no git binary on PATH", but
the catch is far broader than that one case. Separately, `process.waitFor()` is called without
draining the merged stdout/stderr stream; if git ever writes more than the pipe buffer (e.g. a
verbose error), the test deadlocks until the JUnit timeout. A guard that silently passes is not a
guard.

**Fix:** Narrow the failure mode and drain the stream:
```kotlin
private fun isTrackedByGit(file: File): Boolean {
    val process = try {
        ProcessBuilder("git", "ls-files", "--error-unmatch", file.name)
            .directory(file.parentFile).redirectErrorStream(true).start()
    } catch (e: IOException) {
        return true // genuinely no git binary — the only case worth excusing
    }
    process.inputStream.bufferedReader().use { it.readText() }
    return process.waitFor() == 0
}
```

### WR-12: `androidx.core` is used in production code but never declared

**Files:** `app/src/main/java/com/achievementfactory/period/PeriodWatcher.kt:7, 54`,
`app/build.gradle.kts:94-135`

**Issue:** `ContextCompat.registerReceiver` / `ContextCompat.RECEIVER_NOT_EXPORTED` come from
`androidx.core:core`, which appears nowhere in `build.gradle.kts` or `libs.versions.toml`. The
build only works because some other artifact (activity-compose / lifecycle) drags it in
transitively. That is an undeclared dependency on a security-relevant API: a future version bump
that drops the transitive edge breaks the build, or worse, resolves to an older `core` whose
`registerReceiver` overload behaves differently on API 33+.

**Fix:** Add `androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version = "…" }`
to the version catalog and `implementation(libs.androidx.core.ktx)` to the module.

### WR-13: `MigrationTest` seeds a row that the application can never produce or read

**File:** `app/src/androidTest/java/com/achievementfactory/data/MigrationTest.kt:63`

**Issue:** The fixture inserts `accentSwatch = 'BLUE'`, which is not a member of `AccentSwatch`
(the ten names are RED/ORANGE/AMBER/LIME/EMERALD/TEAL/SKY/INDIGO/VIOLET/PINK). The test passes
only because it reads the row through `achievementDao` and never through
`AchievementRepository`, where `AccentSwatch.valueOf` would throw (see WR-10). A migration test
whose fixture is not a realistic row does not prove that realistic rows survive — which is
precisely the property the surrounding 20-line comment insists on.

**Fix:** Use `'SKY'` (or any real swatch name), and add an assertion that the row round-trips
through the repository mapping so the fixture stays realistic as the enum evolves.

## Info

### IN-01: Route-argument name duplicated as a bare string literal

**File:** `app/src/main/java/com/achievementfactory/ui/nav/AppNavHost.kt:51` and
`app/src/main/java/com/achievementfactory/ui/detail/AchievementDetailViewModel.kt:40`

**Issue:** `"achievementId"` is written independently in the `navArgument` declaration and in the
`SavedStateHandle` read. A typo in either place produces a runtime `IllegalStateException` from
`checkNotNull` rather than a compile error.

**Fix:** `const val ARG_ACHIEVEMENT_ID = "achievementId"` in `Routes`, referenced from both.

### IN-02: `SystemAppClock.epochDay()` re-reads the wall clock

**File:** `app/src/main/java/com/achievementfactory/period/AppClock.kt:33-37`

**Issue:** `epochDay()` calls `today()`, which calls `Instant.now()` again. In
`AchievementRepository.increment`, `periodEngine.current(...)` and `clock.epochDay()` are two
separate reads; a call straddling midnight writes a row whose `periodKey` and
`updatedAtEpochDay` describe different days. Currently only an audit-column inconsistency.

**Fix:** Resolve `today()` once per operation and derive both from it, or have `epochDay()` be a
pure function of a passed-in `LocalDate`.

### IN-03: `Modifier.align` inside the celebration content lambda depends on lexical BoxScope

**File:** `app/src/main/java/com/achievementfactory/ui/achievement/AchievementCard.kt:124-132`

**Issue:** `StreakFlameBadge(modifier = Modifier.align(Alignment.TopEnd))` is written inside
`CompletionCelebration`'s `content` lambda, which is a plain `@Composable (CelebrationVisualState) -> Unit`
with no `BoxScope` receiver. It compiles and positions correctly only because the enclosing
`Box`'s scope is in lexical scope *and* `CompletionCelebration` invokes `content(...)` with no
intervening layout node. Adding any wrapper layout inside `CompletionCelebration` silently breaks
the alignment.

**Fix:** Declare `content: @Composable BoxScope.(CelebrationVisualState) -> Unit` and have
`CompletionCelebration` own the `Box`, making the contract explicit.

### IN-04: All user-facing strings are hardcoded in composables

**Files:** `app/src/main/java/com/achievementfactory/ui/daily/DailyScreen.kt:79-81, 103, 107, 167, 172, 235`,
`app/src/main/java/com/achievementfactory/ui/detail/AchievementDetailScreen.kt:52, 84, 134-135, 149, 154`,
`app/src/main/java/com/achievementfactory/ui/create/CreateAchievementScreen.kt:49, 52, 65, 81, 94-95`

**Issue:** `res/values/strings.xml` contains only `app_name`; every other label, snackbar
message, content description and empty-state line is a Kotlin literal. Content descriptions in
particular ("Add achievement", "Back", "Completed") are accessibility surface and are also used
as test selectors, so a copy change silently breaks tests.

**Fix:** Move to `strings.xml` and read via `stringResource(...)`. Low priority for a
single-user app, but the content descriptions at minimum belong in resources.

### IN-05: Unused production APIs

**Files:** `app/src/main/java/com/achievementfactory/data/progress/ProgressDao.kt:23, 26`,
`app/src/main/java/com/achievementfactory/period/PeriodEngine.kt:61-65`

**Issue:** `ProgressDao.observeForAchievement` has zero call sites anywhere (production or test).
`ProgressDao.getFor` and `PeriodEngine.startDateOf` are referenced only from tests. Each is a
Room-generated query or a code path carried without a consumer.

**Fix:** Delete `observeForAchievement` until a screen needs it; keep the test-only ones but note
in KDoc that they exist for verification, so a future reader does not assume they are load-bearing.

### IN-06: `CelebrationVisualState` defaults contradict `CompletionCelebration`'s null-event path

**File:** `app/src/main/java/com/achievementfactory/ui/celebration/CompletionCelebration.kt:21-27, 56-63`

**Issue:** The data class documents its defaults as "the settled 'nothing playing' state"
(`ringProgress = 1f`, `checkmarkProgress = 1f`). But when `event == null`, the composable creates
`Animatable(0f)` for both and returns early from the `LaunchedEffect`, so `content` receives
`ringProgress = 0f, checkmarkProgress = 0f` — an empty ring and no checkmark. Currently masked
because `AchievementCard` only calls it in the `celebration != null` branch, but the `event:
CelebrationEvent.Completed?` signature invites exactly that misuse.

**Fix:** Seed from the event's absence — `Animatable(if (event == null) 1f else 0f)` for
`ringProgress`/`checkmarkProgress` — or drop the nullability and require a non-null event.

### IN-07: Stale comment asserts the opposite of current behavior

**File:** `app/src/main/java/com/achievementfactory/ui/daily/DailyUiState.kt:26-29`

**Issue:** "In this plan `[heldBackIds]` is always empty at the call site (`DailyViewModel`)" is
no longer true — `DailyViewModel:39/66-75` populates it. The comment then describes the D-05
behavior ("a completed card sinks into Done as soon as the write lands") that the code no longer
exhibits.

**Fix:** Rewrite to describe current behavior and drop the plan-relative framing ("in this plan",
"plan 01-07 is what actually populates the set"), which goes stale the moment the plan lands.

### IN-08: `onCelebrationEvent` is a dead parameter

**File:** `app/src/main/java/com/achievementfactory/ui/celebration/CompletionCelebration.kt:51, 74, 82, 100, 105`

**Issue:** No caller ever supplies `onCelebrationEvent`; it defaults to `{}` and is invoked four
times purely to duplicate `onStage`. It is a speculative Phase-5 hook that currently doubles the
callback surface of the hottest animation path.

**Fix:** Remove it. When Phase 5 needs a second listener, `onStage` already carries the same
`CelebrationStage` values and can be composed at the call site.

---

_Reviewed: 2026-08-13T11:39:49Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
