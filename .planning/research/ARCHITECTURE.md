# Architecture Research

**Domain:** Offline-first native Android habit/achievement tracker (Kotlin, Jetpack Compose, Room)
**Researched:** 2026-07-27
**Confidence:** HIGH (layering, UI-layer, Room patterns — official Android guidance); MEDIUM (streak/period-key design — synthesized from official patterns + domain reasoning, no single authoritative source for this exact shape of app)

## Standard Architecture

### System Overview

```
┌───────────────────────────────────────────────────────────────────────┐
│                              UI LAYER (Compose)                        │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐              │
│  │ MainActivity   │  │  NavHost       │  │ TabScreen x3   │            │
│  │ (single, hosts │→│  (Edit/Archive │→│ (Daily/Weekly/  │            │
│  │  NavHost)      │  │   destinations)│  │  Monthly pages) │            │
│  └───────────────┘  └───────────────┘  └───────┬───────┘              │
│                                                  │ collectAsStateWithLifecycle
│                                          ┌───────▼────────┐            │
│                                          │  TabViewModel   │            │
│                                          │  StateFlow<UiState>          │
│                                          │  SharedFlow<CelebrationEvent> │
│                                          └───────┬────────┘            │
├──────────────────────────────────────────────────┼─────────────────────┤
│                         DOMAIN LAYER (pure Kotlin) │                    │
│  ┌───────────────┐  ┌───────────────┐  ┌─────────▼───────┐            │
│  │ PeriodKey      │  │ StreakCalc-    │  │ TierEvaluator    │            │
│  │ Factory        │  │ ulator         │  │                  │            │
│  └───────┬───────┘  └───────┬───────┘  └─────────┬───────┘            │
│          │                   │                     │                    │
│  ┌───────▼─────────────────────────────────────────▼──────┐            │
│  │              TodoExpiry / AppMaintenanceUseCase          │            │
│  └───────────────────────────┬───────────────────────────┘            │
├──────────────────────────────┼─────────────────────────────────────────┤
│                       DATA LAYER              │                         │
│  ┌───────────────┐  ┌────────▼──────┐  ┌───────────────┐              │
│  │ Achievement    │  │ Progress       │  │ Todo           │              │
│  │ Repository     │  │ Repository     │  │ Repository     │              │
│  └───────┬───────┘  └───────┬───────┘  └───────┬───────┘              │
│          │                   │                   │                      │
│  ┌───────▼───────────────────▼───────────────────▼───────┐            │
│  │       Room DAOs (AchievementDao, ProgressDao, TodoDao)  │            │
│  └───────────────────────────┬───────────────────────────┘            │
│                       ┌───────▼────────┐                                │
│                       │  AppDatabase    │                                │
│                       │  (SQLite/Room)  │                                │
│                       └────────────────┘                                │
└───────────────────────────────────────────────────────────────────────┘
```

This is the standard three-layer Android app-architecture shape (UI / domain / data) from the [official Android architecture guide](https://developer.android.com/topic/architecture) and the [Now in Android](https://github.com/android/nowinandroid) reference app, specialized for this domain: the domain layer here is small, pure-Kotlin calculators (period math, streak, tier) rather than a large use-case graph, because the app has one real domain concept (a graded, streaked, periodic achievement) rather than many cross-cutting business flows.

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| `PeriodKeyFactory` | Turn "now" into a comparable period identity; compute adjacency (previous/next period) | Pure Kotlin object wrapping `java.time.LocalDate` / `WeekFields.ISO` |
| `AchievementDao` / `ProgressDao` / `TodoDao` | Persist and observe rows; expose `Flow<List<Entity>>` for reactive reads | Room `@Dao` interfaces |
| `AchievementRepository` | CRUD on achievements, increment/undo transactions, orchestrates tier snapshotting | Class wrapping DAOs + domain calculators, exposes `Flow` of domain models |
| `TodoRepository` | CRUD on todos, expiry sweep | Class wrapping `TodoDao` |
| `StreakCalculator` | Given a period-ordered completion log, compute current + best streak | Pure function, unit-testable, no Android deps |
| `TierEvaluator` | Given a count and a threshold set, compute which tier (if any) was reached | Pure function |
| `AppMaintenanceUseCase` | Expire stale todos, refresh cached streak columns — run at app start/resume | Suspend function invoked from `Application`/`ProcessLifecycleOwner` |
| `TabViewModel` | Combine achievements+todos for one period type into `UiState`; expose one-shot `CelebrationEvent`s | `ViewModel` + `StateFlow` + `Channel`/`SharedFlow` |
| Compose screens | Render `UiState`, forward user intents to ViewModel | Stateless composables driven by `collectAsStateWithLifecycle` |
| `CelebrationOverlay` | Play the tiered/full-tab animation + haptics in response to events | Composable collecting the event flow in a `LaunchedEffect` |

## Recommended Project Structure

Single Gradle module (`app/`), package-by-layer at the top, package-by-feature underneath each layer. This matches the official guidance's explicit small-app recommendation ("place data layer types in a `data` package or module... place UI layer types in a `ui` package or module") — multi-module is a build-time/team-scaling optimization this project has no use for (one developer, no CI fan-out concerns, no dynamic feature delivery, no code-sharing across other apps).

```
app/src/main/java/.../
├── period/                     # Pure Kotlin period-key domain logic — zero Android deps
│   ├── PeriodType.kt           # enum DAILY, WEEKLY, MONTHLY
│   ├── PeriodKey.kt            # value class wrapping the normalized ISO-ish string
│   └── PeriodKeyFactory.kt     # current(type), previous(key), isImmediatelyBefore(a, b)
├── data/
│   ├── AppDatabase.kt          # Room database, entity list, version, migrations
│   ├── Converters.kt           # TypeConverters (enums <-> String, if not using native enum cols)
│   ├── achievement/
│   │   ├── AchievementEntity.kt
│   │   ├── AchievementDao.kt
│   │   └── AchievementRepository.kt
│   ├── progress/
│   │   ├── ProgressEntity.kt
│   │   ├── ProgressDao.kt
│   │   └── ProgressRepository.kt   # or folded into AchievementRepository — see rationale below
│   └── todo/
│       ├── TodoEntity.kt
│       ├── TodoDao.kt
│       └── TodoRepository.kt
├── domain/
│   ├── streak/StreakCalculator.kt
│   ├── tier/TierEvaluator.kt
│   └── maintenance/AppMaintenanceUseCase.kt
├── ui/
│   ├── MainActivity.kt             # single Activity, sets Compose content + NavHost
│   ├── theme/
│   ├── nav/AppNavHost.kt
│   ├── tabs/
│   │   ├── TabScreen.kt            # shared composable for Daily/Weekly/Monthly content
│   │   ├── TabPagerScaffold.kt     # TabRow + HorizontalPager hosting the three TabScreens
│   │   ├── TabViewModel.kt
│   │   └── TabUiState.kt
│   ├── achievement/
│   │   ├── AchievementCard.kt
│   │   ├── AchievementEditScreen.kt
│   │   └── AchievementEditViewModel.kt
│   ├── todo/
│   │   ├── TodoRow.kt
│   │   └── TodoEditSheet.kt
│   ├── archive/
│   │   ├── ArchiveScreen.kt
│   │   └── ArchiveViewModel.kt
│   └── celebration/
│       ├── CelebrationEvent.kt     # sealed class: Tick, Bronze, Silver, Gold, FullTab
│       └── CelebrationOverlay.kt   # confetti/Lottie + haptics, collects the event flow
└── di/                              # Hilt modules (Database, Repositories)
```

### Structure Rationale

- **`period/` is isolated and dependency-free on purpose.** This is the module the whole app's correctness hinges on. Keeping it pure Kotlin (no `android.*`, no Room) means it can be exhaustively unit-tested (year boundaries, ISO week-year edge cases, month-length edge cases, DST-adjacent local-midnight rollovers) without an emulator, and every other layer treats it as ground truth.
- **`data/progress/` is its own package but its repository can be folded into `AchievementRepository`.** Progress rows have no independent lifecycle — they only exist attached to an achievement — so exposing a separate public `ProgressRepository` is optional API surface. Keep the DAO separate (clean schema boundary) but let `AchievementRepository` own the read/write orchestration (it already needs `TierEvaluator` and `StreakCalculator` in the same transaction).
- **`domain/` holds only pure calculators**, not a large use-case graph. This app has one core domain concept, not many cross-cutting flows — a full Clean Architecture use-case-per-action layer would be ceremony without payoff for a solo developer.
- **`ui/celebration/` is separated from `ui/tabs/`** even though it's driven by tab state, because it is explicitly an event/animation concern (see "One-Shot Celebration Events" below) and benefits from being replaceable/testable independently of the data-bound tab screen.
- **Single Activity + single `NavHost`.** Compose Navigation handles the *hierarchical* transitions (tab screen → edit sheet, tab screen → archive); it should not model the Daily/Weekly/Monthly switch, which is *lateral*, same-screen state (see below).

## Architectural Patterns

### Pattern 1: Layer-then-feature packaging, single module

**What:** One Gradle module; top-level packages are `period/`, `data/`, `domain/`, `ui/`; each layer package is further split by feature (`achievement/`, `todo/`, `tabs/`).
**When to use:** Small-to-medium apps (roughly under 20-30 screens, one developer or a very small team). This app is ~8-15 screens' worth of surface with one developer — squarely in this band.
**Trade-offs:** Multi-module buys parallel build times, enforced boundaries via Gradle visibility, and dynamic-feature delivery — none of which pay off here. Single module costs nothing at this size and removes an entire category of Gradle/DI wiring busywork that would otherwise eat into solo-developer time better spent on the celebration/animation work that is this app's actual differentiator.

**Example:**
```kotlin
// data/achievement/AchievementRepository.kt
class AchievementRepository(
    private val achievementDao: AchievementDao,
    private val progressDao: ProgressDao,
    private val periodKeyFactory: PeriodKeyFactory,
    private val streakCalculator: StreakCalculator,
    private val tierEvaluator: TierEvaluator,
)
```

### Pattern 2: Unidirectional data flow with `StateFlow<UiState>` per screen-level ViewModel

**What:** ViewModel exposes a single `StateFlow<TabUiState>` built by `combine()`-ing repository flows; Composables are stateless, read `uiState`, and call ViewModel methods for intents (`onIncrement(achievementId)`, `onToggleTodo(todoId)`).
**When to use:** Always, for screen-level composables — this is the official recommendation, not a judgment call.
**Trade-offs:** None material at this scale; the only discipline required is not leaking one-shot events into the state object (see the animation section).

**Example:**
```kotlin
@HiltViewModel(assistedFactory = TabViewModel.Factory::class)
class TabViewModel @AssistedInject constructor(
    @Assisted private val periodType: PeriodType,
    achievementRepository: AchievementRepository,
    todoRepository: TodoRepository,
) : ViewModel() {

    val uiState: StateFlow<TabUiState> = combine(
        achievementRepository.observeActive(periodType),
        todoRepository.observeForCurrentPeriod(periodType),
    ) { achievements, todos -> TabUiState.Content(achievements, todos) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TabUiState.Loading)
}
```

### Pattern 3: Tabs as a Pager inside one nav destination, not three nav routes

**What:** Daily/Weekly/Monthly are pages of a `HorizontalPager` + `TabRow` living inside a single `NavHost` route (`"tabs"`). Edit screens, the achievement detail (if any), and the archive are separate `NavHost` destinations reached by navigation, not swiping.
**When to use:** Whenever the "tabs" are lateral views over the *same kind* of content (they are — all three are "period buckets of achievements+todos"), as opposed to distinct app sections.
**Trade-offs:** A Pager keeps each tab's ViewModel instance alive while swiping (good — avoids re-subscribing Flows on every swipe) but means all three `TabViewModel` instances exist concurrently; at this data scale (tens of rows) that is immaterial. Using three separate `NavHost` routes instead would be more ceremony for no benefit and would make "swipe between tabs" awkward to animate.

## Data Layer — Concrete Room Schema

### `AchievementEntity`

```kotlin
@Entity(
    tableName = "achievements",
    indices = [Index("periodType"), Index("archived")]
)
data class AchievementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val periodType: String,          // "DAILY" | "WEEKLY" | "MONTHLY"
    val counterTarget: Int,          // default 1, enforced in repository/UI, not DB
    val bronzeThreshold: Int?,       // nullable; tier set must fill downward from Gold
    val silverThreshold: Int?,
    val goldThreshold: Int?,
    val archived: Boolean = false,
    val createdAtEpochDay: Long,
    // Denormalized cache — see "Streak Computation" below. Never the source of truth.
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val streakCachedForPeriodKey: String? = null,
)
```

### `ProgressEntity` (one row per achievement per period instance)

```kotlin
@Entity(
    tableName = "progress",
    indices = [Index(value = ["achievementId", "periodKey"], unique = true)],
    foreignKeys = [ForeignKey(
        entity = AchievementEntity::class,
        parentColumns = ["id"], childColumns = ["achievementId"],
        onDelete = ForeignKey.CASCADE,
    )]
)
data class ProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val achievementId: Long,
    val periodKey: String,           // "2026-07-27" | "2026-W31" | "2026-07"
    val count: Int = 0,
    val done: Boolean = false,       // snapshotted at write time — see rationale below
    val tierReached: String? = null, // "BRONZE" | "SILVER" | "GOLD" | null — snapshotted
    val lastUpdatedAtEpochDay: Long,
)
```

**Why snapshot `done`/`tierReached` on the row instead of deriving them on read from the achievement's *current* target/thresholds:** PROJECT.md leaves open "what happens when target/thresholds are edited mid-streak." Snapshotting at write time means a completed period stays completed and a Gold period stays Gold even if the owner later lowers or raises the target — consistent with the product's existing "no rewriting history" stance on completion (undo is allowed only in the current period; the past is immutable). This is the recommended default answer to that open question. The trade-off: editing an achievement's target does not retroactively change past periods' tier/done status, only future ones — which matches "a streak you can rewrite is not a streak."

### `TodoEntity`

```kotlin
@Entity(
    tableName = "todos",
    indices = [Index(value = ["periodType", "periodKey"]), Index("archived")]
)
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val periodType: String,   // bucket kind: "DAILY" | "WEEKLY" | "MONTHLY"
    val periodKey: String,    // the specific instance it's filed into, e.g. "2026-07-27"
    val done: Boolean = false,
    val archived: Boolean = false,  // true = expired-or-manually-archived
    val createdAtEpochDay: Long,
)
```

No counter/tier/streak columns on `TodoEntity` — deliberately, per PROJECT.md ("Todos have no counter, no tiers, and no streak").

### DAO shape (representative, not exhaustive)

```kotlin
@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements WHERE archived = 0 AND periodType = :type ORDER BY createdAtEpochDay ASC")
    fun observeActive(type: String): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements WHERE archived = 1 ORDER BY createdAtEpochDay DESC")
    fun observeArchived(): Flow<List<AchievementEntity>>

    @Upsert suspend fun upsert(entity: AchievementEntity)
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE achievementId = :id ORDER BY periodKey DESC")
    fun observeForAchievement(id: Long): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress WHERE achievementId = :id AND periodKey = :key")
    suspend fun getForPeriod(id: Long, key: String): ProgressEntity?

    @Upsert suspend fun upsert(entity: ProgressEntity)
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos WHERE archived = 0 AND periodType = :type AND periodKey = :key ORDER BY createdAtEpochDay ASC")
    fun observeForPeriod(type: String, key: String): Flow<List<TodoEntity>>

    @Query("""UPDATE todos SET archived = 1
              WHERE archived = 0 AND done = 0 AND periodType = :type AND periodKey != :currentKey""")
    suspend fun expireStale(type: String, currentKey: String): Int
}
```

## Data Flow

### Read flow (tab render)

```
Room tables
   ↓ (Flow, reactive)
AchievementDao / ProgressDao / TodoDao
   ↓ (mapped to domain models; streak/tier attached from cached columns)
AchievementRepository.observeActive(type) / TodoRepository.observeForCurrentPeriod(type)
   ↓ combine()
TabViewModel.uiState : StateFlow<TabUiState>
   ↓ collectAsStateWithLifecycle()
TabScreen (Compose) — recomposes on new state
```

### Write flow (increment an achievement)

```
User taps "+1" on AchievementCard
   ↓
TabViewModel.onIncrement(achievementId)
   ↓ suspend, viewModelScope
AchievementRepository.increment(achievementId)
   ├─ read current ProgressEntity for (achievementId, currentPeriodKey) — insert if absent
   ├─ count += 1
   ├─ TierEvaluator.evaluate(count, thresholds) → tierReached snapshot
   ├─ done = count >= counterTarget
   ├─ ProgressDao.upsert(...)                      [Room transaction]
   └─ if tier/done crossed a new threshold: emit CelebrationEvent via TabViewModel's event channel
   ↓
Room Flow re-emits → uiState recomputes → UI recomposes with new count/tier
   (celebration event delivered independently — see below — not via uiState)
```

### Key Data Flows

1. **Period-key resolution happens once per read, in Kotlin, never in SQL.** `PeriodKeyFactory.current(periodType)` is computed from `LocalDate.now(zoneId)` and passed as a bound query parameter. SQLite never computes "what week is it" — it only ever compares the precomputed string.
2. **Streak and tier are always computed from the immutable `progress` log**, never trusted as a live-mutated counter. The columns on `AchievementEntity` are a read-optimization cache, reconciled from the log at defined checkpoints (app open/resume), so correctness survives arbitrarily long periods of the app being closed.
3. **Todo expiry is a pull-based sweep**, run at the same checkpoints, not a push from a scheduler.

## The Period Model (Critical)

### Recommendation: a normalized **string period key**, computed via `java.time`, one column per row

- Daily: `LocalDate` ISO form, e.g. `"2026-07-27"`.
- Weekly: ISO week-based form via `java.time.temporal.WeekFields.ISO`, e.g. `"2026-W31"` — `weekBasedYear` + `weekOfWeekBasedYear`, zero-padded. ISO 8601 weeks already start Monday and end Sunday, matching PROJECT.md's stated rule exactly, so no custom week arithmetic is needed.
- Monthly: `"2026-07"` (year-month, zero-padded).

All three formats are zero-padded and lexicographically sortable in the exact chronological order, so `ORDER BY periodKey` and simple string equality/`!=` in SQL do the right thing without any date parsing inside SQLite.

### Why this beats the alternatives

**vs. epoch-day boundaries (storing `periodStartEpochDay`/`periodEndEpochDay` longs instead of a key):** You still have to compute the boundary from the calendar once, at write time — the "hard part" (where does this week start, does this month have 28/30/31 days, which ISO week does Dec 31 belong to) is identical work either way. Boundaries buy you range-scan queries ("all periods between two dates"), which this app never needs — every query is either "rows for this exact period" or "rows for this achievement, ordered by period, walked backward for a streak." A string key gives equality/ordering for free and is directly legible when inspecting the database (`"2026-W31"` vs. two epoch longs), which matters for a solo developer debugging their own data.

**vs. compute-on-read from raw timestamps (store only `completedAt: Instant`, always recompute which bucket it falls into when querying):** This pushes date math into every query and every streak walk, is exactly the design most prone to the classic ISO-week edge-case bugs (a `Jan 1` can belong to `W52`/`W53` of the *previous* year; a Dec 29-31 can belong to `W01` of the *next* year), and makes "did I already complete this period" an expensive/error-prone scan instead of a single indexed equality lookup. It also can't cleanly express a `UNIQUE(achievementId, periodKey)` constraint, which is exactly the constraint that gives "at most one progress row per achievement per period" for free at the database level.

**Practical rule:** period-key computation itself (turning "now" into `"2026-W31"`, or a key into "the previous key") lives in exactly one place — `PeriodKeyFactory`, pure Kotlin using `LocalDate`/`WeekFields.ISO`/`YearMonth`. "Previous period" is computed by stepping the underlying `LocalDate`/`YearMonth` back and re-deriving the key string — never by string-parsing and decrementing the key text itself (e.g. never naively do `"2026-W31"` → `"2026-W30"` by string math; go through `LocalDate.minusWeeks(1)` and reformat, so year-boundary weeks are handled by the JDK, not hand-rolled logic).

**Querying "current period":** trivial — `PeriodKeyFactory.current(periodType)` computed in Kotlin at query time, passed as a bound parameter to `WHERE periodKey = :key`. No stored "is this the current period" flag is ever needed or correct (it would go stale the instant midnight passes while the app is closed).

**minSdk / library choice:** recommend `java.time` (not `kotlinx-datetime`, which itself just wraps `java.time` on Android and still needs desugaring below API 26) and setting `minSdk = 26` — this is a personal app installed directly on the owner's own, presumably modern, phone, so there is no compatibility reason to support API < 26 and take on `coreLibraryDesugaring`. If a lower `minSdk` is later required, `coreLibraryDesugaring` with `desugar_jdk_libs` makes `java.time` fully available on older APIs at negligible cost.

## Streak Computation

### Recommendation: computed from the log, cached for render speed, reconciled lazily — not incrementally maintained

Two naive options and why both are wrong alone:

- **Pure on-the-fly computation, every render:** correct, but means every list recomposition re-walks the progress table for every achievement. At this data scale (tens of achievements, at most a few thousand progress rows for a multi-year daily streak) this is not a *performance* problem, but it is wasted work for a value that only changes when data changes — Compose best practice is to derive from `StateFlow`, not recompute on every recomposition pass.
- **Naive `streak++` on completion, hoping to `streak = 0` on miss:** this is the trap the question calls out. There is no background worker (deliberately — see below), so nothing runs *at* the moment a period is missed. If the app isn't opened for three weeks, an incrementally-maintained counter has no event that tells it "you just missed a week" — it will silently show a stale, wrong streak until something explicitly reconciles it against the log.

**The recommended shape:** `currentStreak`/`bestStreak`/`streakCachedForPeriodKey` on `AchievementEntity` are a **read cache, not a source of truth**. Source of truth is always the `progress` log. Reconciliation:

1. On app start (and on process resume, via `ProcessLifecycleOwner`), and lazily whenever a `TabViewModel` notices `streakCachedForPeriodKey != PeriodKeyFactory.current(periodType)` for an achievement it's about to render, `StreakCalculator` runs a **backward walk**: starting at the period immediately before the current one, step backward one period at a time (`PeriodKeyFactory.previous(key)`), checking for a `ProgressEntity` with `done = true` at each step, stopping at the first gap. The count of consecutive `done` periods found is `currentStreak`; `bestStreak = max(existing bestStreak, currentStreak)`.
2. This walk is bounded by the length of the actual streak (a multi-year daily streak is ~1825 rows — trivial for SQLite/Room to page through with `ORDER BY periodKey DESC`), so it is cheap even in the worst realistic case, and it is **self-healing regardless of how long the app was closed** — there's no "catch-up from where we left off" bookkeeping to get wrong, because it always re-derives from the log's actual gaps rather than trusting a previously-cached position.
3. The result is written back to `AchievementEntity` (cache) so subsequent renders in the same period are O(1) reads, and the cache is invalidated by nothing more than "the current period key changed since we last computed" — a single string comparison.
4. Whether the *current, still-open* period counts toward the displayed streak before it's done is a UX choice, not a technical one — recommend it does **not** count until `done = true` for that period (consistent with "no backfilling, no rewriting history": a streak number should only ever describe completed periods).

**Undo interacts safely with this design** because undo is only ever legal on the current period's row (per PROJECT.md), and the current period's `done` state is not yet part of any cached streak count until it flips to `done = true` — so undo before completion is a no-op for the streak, and undo after completion (if ever allowed) simply reruns step 1's cheap backward walk.

## Tier Evaluation

**Recommendation: a single pure function, `TierEvaluator.evaluate(count: Int, thresholds: TierThresholds): Tier?`, in `domain/tier/`, with no Android or Room dependency.**

- Called by `AchievementRepository.increment(...)` at write time, to compute the `tierReached` value snapshotted onto the `ProgressEntity` row for the current period (see schema rationale above — snapshotted, not derived live from the achievement's current thresholds).
- Called again by `TabViewModel` when diffing the *previous* tier against the *new* tier after a write, purely to decide which `CelebrationEvent` to emit (Bronze/Silver/Gold/no-op) — this diff is UI-orchestration logic, not domain logic, and belongs in the ViewModel, not the evaluator itself.
- This keeps tier logic in exactly one testable place instead of scattered across a DAO query, a Composable's conditional, and a ViewModel — the classic way this kind of "which visual state am I in" logic rots.
- `TierThresholds` should encode the "fills downward from Gold" validation (`{Gold}`, `{Silver, Gold}`, `{Bronze, Silver, Gold}` only) as a constructor/factory-level invariant in this same package, so an invalid tier set is structurally unrepresentable rather than checked ad hoc in a form validator.

## Todo Expiry Without a Background Worker

**Recommendation: lazy, pull-based reconciliation at app-open/app-resume, not `WorkManager`.**

- `AppMaintenanceUseCase.run()` — called once from `Application.onCreate` (or a lightweight startup ViewModel) via `applicationScope.launch`, and again from a `ProcessLifecycleOwner` `onStart` observer (covers the case where the app is left open in the background across a midnight/week/month rollover and then resumed) — does two idempotent things:
  1. `TodoDao.expireStale(type, currentKey)` for each period type: any todo whose `periodKey` no longer matches the current key for its type, and is not `done`, gets `archived = true`.
  2. Invalidate/recompute the streak cache for achievements whose `streakCachedForPeriodKey` is behind the current key (folds into the same checkpoint as streak reconciliation above).
- **Why not `WorkManager`:** `WorkManager` exists to guarantee work runs even if the app isn't foregrounded (survives process death, reboots, respects Doze/battery constraints) — that guarantee is exactly what this app's product decisions explicitly reject ("No notifications in v1... the core loop is opening the app"). If the app is never opened during a given period, there is nothing to show the user anyway; the *only* moment expiry needs to be correct is the next time the app is opened, which is precisely when the lazy sweep runs. Adding `WorkManager` here would mean taking on periodic-work scheduling, Doze-mode constraints, and a battery-optimization surface for a correctness guarantee the product doesn't need — pure complexity tax for a solo, personal, open-only app.
- This same reasoning is why the streak cache reconciliation (previous section) piggybacks on the identical checkpoint rather than getting its own scheduling mechanism.

## One-Shot Celebration Events (the animation trap)

**The trap, concretely:** if "should I show confetti right now" is modeled as a boolean/enum field inside `TabUiState` (a `StateFlow`), then *any* new collector — a configuration change (rotation), a process recreation, or simply the Composable leaving and re-entering composition — immediately receives the **last emitted value** on subscribe, because that is what `StateFlow` is *for*. If that last value happens to be "Gold tier reached," the confetti fires again on rotation, or fires again when the user swipes back to a tab they already celebrated on. This is the single most common bug in this exact architecture shape.

**Recommendation:** model celebrations as **events on a separate, non-replaying stream**, not as state.

```kotlin
class TabViewModel(...) : ViewModel() {
    private val _celebrationEvents = Channel<CelebrationEvent>(Channel.BUFFERED)
    val celebrationEvents = _celebrationEvents.receiveAsFlow()   // no replay

    val uiState: StateFlow<TabUiState> = ...  // never carries celebration info

    private fun onIncrement(id: Long) = viewModelScope.launch {
        val prevTier = ...
        val newTier = repository.increment(id)
        if (newTier > prevTier) _celebrationEvents.trySend(CelebrationEvent.Tier(id, newTier))
        if (repository.isTabFullyCleared(periodType)) _celebrationEvents.trySend(CelebrationEvent.FullTab)
    }
}
```

```kotlin
@Composable
fun TabScreen(viewModel: TabViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.celebrationEvents.collect { event -> /* trigger CelebrationOverlay + haptics */ }
        }
    }
    // ...renders uiState only; celebration is fire-and-forget, not derived from state
}
```

Why this is safe against the two known failure modes:

- **Config-change replay:** `Channel`/`receiveAsFlow()` has no replay buffer — a new collector after rotation sees nothing until the *next* emission, so the same celebration never fires twice from a single increment.
- **Edge-triggering, not level-triggering:** the event is emitted by the ViewModel at the moment it observes a *transition* (`newTier > prevTier`, or "tab just became fully cleared" computed by comparing to a private previous-value it holds, not derived by the Composable from `uiState`). The Composable never infers "should I celebrate" from the current state snapshot — it only ever reacts to an explicit, one-time signal. This also directly gives the "full-tab celebration is distinct from per-achievement celebration" requirement: it's simply a different `CelebrationEvent` variant, computed once, at the edge where the tab's aggregate "everything done" boolean flips false → true.

**Known caveat, deliberately accepted:** `Channel`-based events can theoretically be dropped if the consumer isn't collecting at the exact moment of emission (e.g., app backgrounded mid-tap) — this is the reason some official guidance has moved away from event channels for *navigation*, where a dropped event is a real correctness bug. For a purely cosmetic "play confetti" signal, occasionally missing an animation on an edge case (backgrounding mid-tap) is an acceptable trade-off against the alternative (state-based replay causing *wrong* animations, which is worse — a confetti burst that fires when nothing happened undermines the "the celebration means something" core value more than an occasional missed one). `repeatOnLifecycle(STARTED)` collection, combined with the single-Activity/no-deep-navigation shape of this app, keeps the loss window negligible in practice.

## Suggested Build Order

Dependency order between the components above, useful for phase sequencing:

1. **`period/` package** — `PeriodType`, `PeriodKeyFactory`, unit tests for daily/ISO-week/monthly edge cases (year-end week rollover, month lengths, local-midnight boundary). Nothing else can be correctly built or tested without this being right first.
2. **Room schema** — `AchievementEntity`, `ProgressEntity`, `TodoEntity`, DAOs, `AppDatabase`. No business logic yet — just persistence and the `UNIQUE(achievementId, periodKey)` constraint.
3. **Repositories** — CRUD, increment/undo transaction, expiry sweep — wired to DAOs and `PeriodKeyFactory`, still without `StreakCalculator`/`TierEvaluator` (return raw counts first).
4. **Domain calculators** — `StreakCalculator`, `TierEvaluator`, `AppMaintenanceUseCase` — layered on top of the now-stable repositories; unit-testable against fixture progress logs (including "app not opened for N periods" scenarios).
5. **ViewModels** — `TabViewModel`, `AchievementEditViewModel`, `TodoEditViewModel`, `ArchiveViewModel` — combine repositories + calculators into `UiState`; still no animation, just correct data and correct one-shot event *plumbing* (the `Channel`, emitting placeholder events).
6. **Compose UI shell** — single Activity, `NavHost`, `TabPagerScaffold`, static screens rendering `UiState` with plain checkmarks/counters — no confetti yet. This is the point at which the app is fully *usable* end to end.
7. **Celebration/animation layer** — `CelebrationEvent` variants wired to real tier-escalating animations + haptics, `CelebrationOverlay` collecting the already-correct event stream from step 5. Deliberately last: it is purely reactive to state transitions that steps 1-6 already compute correctly, so there is nothing left to get wrong about *when* to fire, only *what* to render.

This ordering also implies a natural phase boundary for the roadmap: a "data + core loop" phase (1-6, app is functionally complete but visually flat) followed by a "celebration polish" phase (7, where the product's actual core value — the loop feeling satisfying — gets built on a foundation that is already known to be correct).

## Anti-Patterns

### Anti-Pattern 1: Deriving "should celebrate" from `UiState` inside the Composable

**What people do:** Add a `shouldShowConfetti: Boolean` (or similar) field to the `StateFlow`-backed `UiState`, set it true on completion, and try to flip it back to false somewhere to "consume" it.
**Why it's wrong:** `StateFlow` replays its last value to every new subscriber by design — rotation, tab re-entry, or process recreation will redeliver `true` and re-fire the animation. The "consume and reset" workaround is itself a race (a fast recomposition can read `true` twice before the reset write lands) and conflates state (what's on screen) with events (what just happened).
**Do this instead:** A non-replaying `Channel`/`SharedFlow(replay = 0)` for events, collected with `repeatOnLifecycle`, exactly as described above.

### Anti-Pattern 2: Incrementally-maintained streak counters with no reconciliation path

**What people do:** `streak += 1` on completion, and rely on *something* (a scheduled job, a check "next time this achievement is touched") to notice a miss and reset to 0.
**Why it's wrong:** Without a background worker (which this app deliberately doesn't have), there is no event that fires *at* the moment of a miss. If the app isn't opened, the counter is simply wrong until something reconciles it — and it's easy to ship this bug because it's invisible in day-to-day testing (developer opens the app every day).
**Do this instead:** Treat the `progress` log as the only source of truth; recompute the streak with a bounded backward walk whenever the cached value is behind the current period key (see Streak Computation above).

### Anti-Pattern 3: Recomputing "which tier" in the UI layer

**What people do:** Put `if (count >= goldThreshold) Gold else if (...)` logic directly in a Composable or in a DAO's `@Query` (via SQL `CASE WHEN`).
**Why it's wrong:** This is domain logic (it encodes the product rule about tiers filling downward from Gold) that then exists in two or three places and drifts — e.g., the animation-selection code and the badge-rendering code silently disagree after an edit.
**Do this instead:** One `TierEvaluator` in the domain layer; everything else consumes its output.

## Scaling Considerations

Not meaningfully applicable — this is a single-user, single-device, local-only app with a data volume bounded by "one person's daily/weekly/monthly habits over years" (realistically low hundreds of achievements/todos, low thousands of progress rows even after a decade of daily use). No scaling tier table is warranted; the only "scale" axis that matters is streak length, which is already addressed (bounded backward walk, cheap even at ~2000 rows).

## Integration Points

### External Services

None. The app has no network permission and no external service integration by explicit product constraint.

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| UI ↔ ViewModel | `StateFlow<UiState>` (state) + `Channel`/`SharedFlow` (events) | Never mix the two — see Anti-Pattern 1 |
| ViewModel ↔ Repository | Suspend functions (writes) + `Flow` (reads) | Repository owns transaction boundaries (e.g. increment = read-modify-write in one `@Transaction`) |
| Repository ↔ Domain calculators | Direct function calls, in-process, pure | No async boundary needed — calculators are pure and fast |
| Repository ↔ DAO | Direct calls | DAO is Room-generated, thin |
| `period/` ↔ everything | Everything depends on it; it depends on nothing | Enforced by package structure, not Gradle module boundaries, since this is a single module — discipline substitutes for compiler enforcement at this scale |

## Sources

- [Guide to app architecture — Android Developers](https://developer.android.com/topic/architecture) (HIGH — official)
- [Recommendations for Android architecture — Android Developers](https://developer.android.com/topic/architecture/recommendations) (HIGH — official)
- [UI layer — Android Developers](https://developer.android.com/topic/architecture/ui-layer) (HIGH — official)
- [Build an offline-first app — Android Developers](https://developer.android.com/topic/architecture/data-layer/offline-first) (HIGH — official)
- [Common modularization patterns — Android Developers](https://developer.android.com/topic/modularization/patterns) (HIGH — official)
- [Task scheduling / WorkManager — Android Developers](https://developer.android.com/topic/libraries/architecture/workmanager) (HIGH — official; used to justify *not* using it here)
- [Now in Android sample app](https://github.com/android/nowinandroid) (HIGH — official Google sample, referenced for layering conventions)
- [StateFlow and SharedFlow — Android Developers (Kotlin docs)](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow) (HIGH — official)
- [ViewModel one-off event antipatterns — Manuel Vivo](https://manuelvivo.dev/viewmodel-events-antipatterns) (MEDIUM — Google engineer's personal guidance, widely cited; used for the nuance on Channel delivery guarantees, not as the primary recommendation source)
- [Exercises in futility: One-time events in Android — Costa Fotiadis](https://www.costafotiadis.com/exercises-in-futility-one-time-events-in-android/) (MEDIUM — corroborating community source on Channel/SharedFlow event pattern)
- [`WeekFields` — Java SE documentation (Oracle)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/temporal/WeekFields.html) (HIGH — official JDK reference for ISO week-based date fields)
- [`ISO week date` — Wikipedia](https://en.wikipedia.org/wiki/ISO_week_date) (MEDIUM — used only to corroborate the Monday-start/week-based-year edge cases described above)
- [Java 8+ APIs available through desugaring — Android Developers](https://developer.android.com/studio/write/java8-support-table) (HIGH — official, `java.time`/desugaring/minSdk guidance)
- Streak-algorithm framing (schedule-aware backward walk, current vs. best streak) synthesized from general habit-tracker community write-ups (MEDIUM/LOW individually; used only as directional color, not as the basis for the specific recommendation, which is original reasoning grounded in this app's own no-grace-period/no-backfill rules from PROJECT.md)

---
*Architecture research for: offline-first native Android achievement/habit tracker with periodic reset semantics*
*Researched: 2026-07-27*
