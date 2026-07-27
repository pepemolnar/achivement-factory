# Pitfalls Research

**Domain:** Offline Android habit/streak tracker — Kotlin, Jetpack Compose, Room, single-user, local-only
**Researched:** 2026-07-27
**Confidence:** HIGH (Room/Compose/java.time mechanics — official docs + widely corroborated community sources); MEDIUM (streak psychology/UX claims — blog and community sources, not peer-reviewed)

## Critical Pitfalls

### Pitfall 1: Period identity computed from epoch-millis/Instant arithmetic instead of civil calendar dates

**What goes wrong:**
Streak and period logic is built on `System.currentTimeMillis()` / `Instant` math — e.g. "is now more than 86,400,000ms since last completion?" or "add 7 days in millis to find next week." This looks correct in the emulator and breaks the moment a DST transition, timezone change, or a leap day is involved, because a calendar "day," "week," or "month" is not a fixed duration.

**Why it happens:**
Millis-based date math is the first thing that works in a quick prototype, and it's what most `Date`/`Calendar`-era Android tutorials still show. It also passes all manual testing done in one timezone with no DST boundary nearby.

**How to avoid:**
Never represent a period key as a millisecond count or an `Instant` offset. Use `java.time.LocalDate` (day period), a `LocalDate` anchored to Monday via `TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)` (week period), and `java.time.YearMonth` (month period) as the canonical, storable "period key" for every achievement. Convert `Instant.now()` to a civil date exactly once per read, via `Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()`, and do all period-boundary math with `LocalDate.plusDays(1)`, `YearMonth.plusMonths(1)`, etc. — these are calendar-aware and immune to 23/25-hour DST days by construction. Enable core-library desugaring (or use kotlinx-datetime) so `java.time` is available on the minSdk you target.

**Warning signs:**
Any code that does `+ 86_400_000L`, `+ TimeUnit.DAYS.toMillis(...)`, or compares two `Instant`/`Long` values directly to decide "same day" / "same week." Any date field typed `Long` that isn't explicitly an audit timestamp (`completedAt`).

**Phase to address:**
Data-model / period-engine phase, before any streak logic is written — this is a foundational type choice, not something to retrofit later.

---

### Pitfall 2: Locale-dependent week-start APIs silently override the product's Monday-start rule

**What goes wrong:**
`Calendar.getInstance().firstDayOfWeek`, `WeekFields.of(Locale.getDefault())`, and similar locale-aware APIs return Sunday-start in the US locale and Monday-start in most of Europe/ISO locales. If any of this code path is used — even once, even indirectly through a date-picker or formatting library — week boundaries silently shift depending on the device's region setting. This is a single-user personal app, but the owner's phone can still change region format (an OS update, a deliberate settings change, or importing settings) and quietly shift every future week boundary, breaking streak continuity without any code change.

**Why it happens:**
`WeekFields`/`Calendar` locale APIs are the "idiomatic-looking" answer that shows up in most Stack Overflow results for "get start of week in Java/Kotlin," and they work fine in a single dev/test locale.

**How to avoid:**
Hardcode `DayOfWeek.MONDAY` everywhere a week boundary is computed: `date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))` for week start, `.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))` for week end. Never call any `Locale`-based week API. Do not use ISO week-of-year numbers (`WeekFields.ISO.weekOfWeekBasedYear()`) as the period key at all — see Pitfall 3.

**Warning signs:**
Any usage of `Calendar.getInstance()`, `WeekFields.of(Locale.getDefault())`, or a date-picker/formatter library default without an explicit locale override. A code review checklist item: "grep for `Locale.getDefault()` near any date/week logic."

**Phase to address:**
Data-model / period-engine phase, alongside Pitfall 1.

---

### Pitfall 3: Using ISO week-of-year numbers as a period key breaks at year boundaries (week 53 / dual-year weeks)

**What goes wrong:**
If "current week" is represented as `(weekNumber, year)` instead of an actual date, the ISO week-numbering system creates real edge cases: some years have 53 ISO weeks (2026 is one of them), and the week spanning New Year's Day can belong to the *previous* ISO year (e.g. Jan 1 2021 is ISO week 53 of 2020). A naive `(weekOfYear, calendarYear)` tuple built from `LocalDate.getYear()` instead of `weekBasedYear` produces two different "week 1"s or skips/duplicates a week at the boundary, breaking streak-adjacency checks exactly once a year, which makes it very hard to reproduce and debug later.

**Why it happens:**
Week numbers feel like a natural, compact key, and the bug only manifests once per year at the December/January boundary, so it survives casual testing done any other week of the year.

**How to avoid:**
Do not use week numbers as the period key at all. Use the `LocalDate` of the Monday that starts the week (e.g. `2026-07-27`) as the canonical week-period identity. This sidesteps ISO week-of-year entirely: two weeks are "adjacent" iff their Monday dates differ by exactly 7 days, computed with `LocalDate.plusDays(7)` — no week-number/year-boundary logic needed anywhere.

**Warning signs:**
Any field or comparison involving `weekOfYear`, `weekOfWeekBasedYear`, or a `(week, year)` pair. Any streak-adjacency check that isn't a direct `LocalDate`/`YearMonth` comparison.

**Phase to address:**
Data-model / period-engine phase.

---

### Pitfall 4: Retroactive missed-period detection is never implemented as a reconciliation pass

**What goes wrong:**
The app has no background service and no notifications (explicitly out of scope), so a missed period is only detectable when the app is opened again. If the "did I miss a period?" check only compares "yesterday" to "today," it silently fails to break a streak (or expire a todo) correctly when the app wasn't opened for 3 weeks — the naive single-step check sees a gap but doesn't correctly re-derive "streak = 0" for every kind of period (daily/weekly/monthly) or correctly expire every stale todo bucket that closed while the app was untouched.

**Why it happens:**
It's easy to write and test "mark missed if `lastCompletedPeriod != previousPeriod`" and never test the multi-period-gap path, because during development the app is opened constantly.

**How to avoid:**
Implement a single, idempotent **reconciliation pass** that runs once per app foreground (app start and `ON_RESUME`, see Pitfall 6): for every achievement, compare its stored `lastCompletedPeriodKey` to the freshly computed `currentPeriodKey`; if they are not adjacent (current minus one period), the streak resets to 0 — this is a direct key comparison, not a loop over every missed day, because `LocalDate`/`YearMonth` period keys make "adjacent" a cheap check regardless of how many periods were actually skipped. For todos, the same pass expires any todo whose period bucket has closed (`periodEnd < today`) into the archive. Write this pass as a pure function taking "now" as an explicit parameter so it is unit-testable with fabricated gaps of 1, 2, 3+ weeks/months without waiting real time.

**Warning signs:**
No test exists that fast-forwards "now" by several periods and asserts the resulting streak/archive state. The missed-period check lives inline in a UI event handler rather than as a standalone, testable function invoked on every launch.

**Phase to address:**
Streak/period-engine phase (implementation) with explicit test coverage; wired into app-start lifecycle in the state/lifecycle phase.

---

### Pitfall 5: Midnight rollover while the app is open in the foreground is never handled

**What goes wrong:**
"Current period" is computed once (e.g. at ViewModel construction or screen composition) and cached. If the owner has the app open across local midnight, the UI keeps showing yesterday's period as "current" — a completion tap after midnight either logs against the wrong (stale) period, or a card that should reset to "not done" for the new day keeps showing yesterday's completed state until the screen is left and re-entered.

**Why it happens:**
There is no natural Compose recomposition trigger for "the wall clock crossed midnight" — nothing in the composition invalidates on its own just because time passed.

**How to avoid:**
Register a `BroadcastReceiver` for `Intent.ACTION_DATE_CHANGED` (fires on date change), `Intent.ACTION_TIME_CHANGED` (manual clock edit), and `Intent.ACTION_TIMEZONE_CHANGED` (travel/zone change) while the app is resumed (register in `onResume`, unregister in `onPause`, or via a `LifecycleEventObserver`), and on any of these broadcasts, re-run the reconciliation pass from Pitfall 4 and force the "current period" state to recompute. This is cheap (three sticky system broadcasts) and covers the midnight-open, manual-clock-edit, and cross-timezone-travel cases with one mechanism.

**Warning signs:**
"Current period" is stored as a `val` computed once instead of derived reactively/recomputed on resume. No manual test exists for "leave the app open, advance the emulator clock past midnight, confirm the card flips to not-done."

**Phase to address:**
State/lifecycle phase — wire the receiver into the app's root lifecycle owner alongside the reconciliation pass from Pitfall 4.

---

### Pitfall 6: Device clock changes treated as either fully trusted or requiring anti-tamper — without an explicit decision

**What goes wrong:**
Two failure modes at opposite extremes: (a) the app trusts the device clock completely and nobody notices that setting the date forward "completes tomorrow early" or setting it backward "un-misses" a day, quietly making the streak meaningless as a signal even to its own owner; or (b) the team over-engineers anti-tamper (monotonic-clock cross-checks, server time, etc.) for a single-user, offline, personal trust tool where "cheating" only defeats the owner's own motivation system and has no stakes.

**Why it happens:**
Clock-tamper handling is a well-known problem in streak/gamification products with real user bases and real support tickets, so it's tempting to import that concern wholesale into a product with a completely different threat model (one trusted user, no leaderboard, no rewards to fake).

**How to avoid:**
Make an explicit, documented decision rather than defaulting into either extreme: for this product, trust the device clock (`ZoneId.systemDefault()`, `Instant.now()`) as the source of truth, and do not build clock-tamper detection — it is not proportionate to a single-user offline tool. Do not spend phase time on `elapsedRealtime()` cross-checks or similar. Simply document this as an accepted limitation so it isn't "discovered" mid-build and doesn't trigger scope creep.

**Warning signs:**
A roadmap phase appears for "prevent streak cheating" with no corresponding product requirement for it. Conversely, silence on the topic with no documented decision at all.

**Phase to address:**
Decision belongs in the data-model/period-engine phase's design notes, not as build work.

---

### Pitfall 7: `fallbackToDestructiveMigration()` used as the default Room strategy, wiping real streak history

**What goes wrong:**
Room throws if it can't find a `Migration` from the installed schema version to the current one. The fastest way to make that crash go away during development is `fallbackToDestructiveMigration()`, which drops and recreates every table. Left in place (or reached because a real migration was forgotten) after the app has real data on the owner's phone, any schema change — adding a tier field, adding a todo table, changing a column type — silently deletes every achievement, every streak, and all history on next launch. There is no server backup to recover from; this is the single highest-cost data-loss bug this app can have.

**Why it happens:**
It's the officially documented "quick fix" for migration crashes during prototyping, and for a solo personal project the line between "still prototyping" and "has real data I care about" is blurry and easy to cross without noticing.

**How to avoid:**
Set `exportSchema = true` on the `@Database` annotation from day one and commit the generated schema JSON files to version control — without them, `MigrationTestHelper` cannot validate migrations against real prior schemas. Write an explicit `Migration(from, to)` for every schema version bump, however small, starting from the first schema change after the app is installed with real data (i.e., after Phase 1 ships to the owner's device). Add a `MigrationTestHelper`-based instrumented test that migrates a populated pre-migration database and asserts the data survives — run this test on every schema change, not just once. Treat any PR that touches `@Entity` classes as requiring a migration, as a hard rule.

**Warning signs:**
`fallbackToDestructiveMigration()` (or `fallbackToDestructiveMigrationOnDowngrade()`) present anywhere in the `Room.databaseBuilder(...)` chain once the app has shipped to the owner's device with real entries. `exportSchema` left at its default (`true` is default, but no `schemas/` directory is actually committed — meaning nobody is looking at it). No migration test exists.

**Phase to address:**
Persistence/Room phase for the initial setup (exportSchema + CI/test scaffolding); enforced as a standing rule for every subsequent phase that touches the schema.

---

### Pitfall 8: Celebration animation modeled as derived UI state instead of a one-shot event, re-fires on recomposition/rotation/process restoration

**What goes wrong:**
If "should I play the celebration?" is computed at composition time from persisted state (e.g. `if (achievement.isDoneToday) PlayCelebration()`), the animation replays every time that composable recomposes with `isDone == true` — on configuration change (rotation), on process-death restoration, when the item scrolls back into view in a `LazyColumn`, or on any unrelated recomposition. The single most delight-critical moment in the app becomes the most annoying one within a day of use.

**Why it happens:**
Deriving UI directly from persisted `State`/`Flow` is the correct default pattern for almost everything else in Compose, so it's an easy trap to fall into for the one thing that must NOT be derived that way — a celebration is an *event* (something that happened once), not a *state* (something that is currently true).

**How to avoid:**
Model celebration triggers as a one-shot event stream from the ViewModel — a `Channel`/`SharedFlow` with `replay = 0` — separate from the persisted `isDone`/tier `StateFlow`. Emit into it only from the exact increment/completion call site that crosses a done or tier threshold, never derived from the resulting state. Collect it in the composable with `LaunchedEffect(Unit) { viewModel.celebrations.collect { ... } }`. Because `replay = 0`, an event that fires while the process is backgrounded/killed is simply lost (not replayed) rather than firing twice — treat this as the correct, deliberate tradeoff (missing one animation is fine; a duplicate is not) rather than something to "fix" by adding replay.

**Warning signs:**
Any celebration trigger written as `if (state.isDone) { ... }` inside a composable body or a `derivedStateOf`. Manually test: complete an achievement, rotate the device — animation must NOT replay. Kill the process via Developer Options → "Don't keep activities," reopen — animation must NOT replay.

**Phase to address:**
Celebration/animation phase; the event-vs-state distinction should be settled in the state-management phase before celebration UI is built on top of it.

---

### Pitfall 9: Undo races the underlying Room write, producing an inconsistent counter/streak

**What goes wrong:**
A naive implementation reads the current counter value, computes `+1` or `-1` in Kotlin, and writes it back (`read → modify → write`). If the user taps increment and then immediately taps undo (from a Snackbar), or double-taps increment quickly, two writes can interleave: undo reads a stale pre-increment value and decrements from it, or two increments both read the same starting value and only one survives. On a personal app this shows up as "my streak says done but the counter says 0" — a confidence-destroying bug in the app's core promise.

**Why it happens:**
Read-modify-write in application code is the natural way to write "increment a counter" and works fine under manual, unhurried testing; the race only appears under rapid double-taps, which testers rarely reproduce deliberately.

**How to avoid:**
Perform increment/decrement as an atomic SQL statement (`UPDATE achievements SET count = count + 1 WHERE id = :id`) inside a Room `@Transaction`, never as Kotlin-side read-modify-write. Model undo as "reverse the specific last-recorded mutation" (store a small mutation log entry — achievement id + delta + timestamp — and have undo consume the most recent entry for that achievement) rather than "blindly decrement by 1," so a stray extra undo tap or an undo issued after a subsequent increment can't push the counter negative or undo the wrong action. Debounce/disable the increment control while its write is in flight, or ensure taps are serialized per achievement (e.g., through a single-flight `Mutex` per achievement id in the ViewModel) so overlapping writes can't interleave. Wrap counter update + streak recompute + tier recompute in a single `@Transaction` so a process death mid-write can't leave the three fields inconsistent with each other.

**Warning signs:**
Any DAO method that does a `SELECT` followed by an `UPDATE` in separate statements for a counter change. No test exercises rapid double-tap or "undo after navigating away and back." Undo implemented as `count - 1` rather than as reversal of a specific logged mutation.

**Phase to address:**
Persistence phase (atomic SQL, transactions) and completion/undo feature phase (mutation-log design) together — this is a cross-cutting concern between the two.

---

### Pitfall 10: Reduced-motion / "remove animations" accessibility setting is silently ignored by hand-rolled celebration effects

**What goes wrong:**
Compose's built-in `animate*AsState`/`Animatable`/transition APIs automatically respect the system animator-duration-scale setting (from Compose 1.2 onward) — when the user has "Remove animations" on, those APIs collapse to duration 0 with no extra code. But a custom confetti/particle effect driven by a raw `withFrameNanos` loop, `rememberInfiniteTransition`, or a third-party particle/Lottie library does **not** automatically pick this up, because it isn't going through the `Animator`-scaled Compose primitives. The result: the app appears to fully ignore a system accessibility setting for exactly the one effect the user turned that setting on to avoid.
Compose does have an app-facing signal for this via `LocalAccessibilityManager.current?.isReduceMotionEnabled`.

**Why it happens:**
Confetti/particle effects are usually the one piece of animation custom-built with raw `Canvas`/frame-clock code (for performance and visual control), which is exactly the code path that sits outside Compose's automatic scaling.

**How to avoid:**
At the top of the celebration composable, branch explicitly on `LocalAccessibilityManager.current?.isReduceMotionEnabled == true` (or equivalently read `Settings.Global.ANIMATOR_DURATION_SCALE`) and substitute an instant/near-instant celebration (a static check/badge flash plus the haptic, no particle simulation) when true. Apply this check once, centrally, at the entry point every celebration goes through — not per-effect — so it can't be missed on a future tier/animation addition.

**Warning signs:**
Any `Canvas`/`withFrameNanos`/custom particle code with no accessibility branch near it. Manually test with Settings → Accessibility → Remove animations enabled: celebration must degrade to near-instant, not just "play at normal speed anyway."

**Phase to address:**
Celebration/animation phase, as an explicit acceptance criterion, not an afterthought.

---

### Pitfall 11: Editing an achievement's period or target mid-streak has no defined semantics

**What goes wrong:**
This is flagged as an open question in the project's own scope: what happens to an in-progress streak when the owner edits an achievement's period (daily → weekly) or its target mid-cycle? Without a resolved rule, the implementation will pick an arbitrary behavior by accident (e.g., silently reset to 0, or silently preserve a streak count that no longer means anything against the new period unit), and it will be discovered only when it happens to the owner's real data — a Room migration can't fix a semantic gap like this after the fact the way it fixes a schema gap.

**Why it happens:**
Edit flows are usually designed after the "happy path" create/complete flow, so their interaction with streak state is easy to leave implicit.

**How to avoid:**
Resolve this explicitly before building the edit flow, not during it. The simplest, most honest option consistent with the "no grace, no rewritten history" streak philosophy already chosen for this product: changing an achievement's period unit resets its current streak to 0 (its best-streak record is preserved) because a streak measured in "consecutive days" is not the same claim as one measured in "consecutive weeks" — carrying the number across would misrepresent it. Changing only the target (not the period) can preserve the current streak, since the period unit didn't change, but should not retroactively re-evaluate past periods against the new target (no backfilling, per existing scope). Whatever rule is chosen, write it down next to the other Key Decisions and add a test that edits an achievement mid-streak and asserts the resulting streak value.

**Warning signs:**
The edit-achievement feature ships without a test covering "edit while streak > 0." The open question is still marked open in `PROJECT.md` when the edit phase starts.

**Phase to address:**
Should be resolved as a decision during the data-model/period-engine phase (before edit UI exists), then implemented in the achievement-edit feature phase.

---

## Technical Debt Patterns

Shortcuts that seem reasonable but create long-term problems.

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|-----------------|------------------|
| `fallbackToDestructiveMigration()` as the Room strategy | No migration code to write, no crashes during rapid schema iteration | Any future schema change silently deletes all real streak history | Only before the app has ever been installed with real data on the owner's device (i.e., pre-Phase-1-ship); remove it the moment real usage starts |
| Deriving celebration from persisted `isDone` state instead of a one-shot event | Fewer moving parts, "just works" on first manual test | Animation re-fires on every rotation/recomposition/process restore; erodes the core delight loop within days | Never — the event/state distinction is cheap to get right from the start |
| Millis/`Instant`-based "add 24h" period math | Fast to write, passes tests run in one timezone on one day | Breaks silently on DST transitions and timezone travel; very hard to reproduce later | Never for period-boundary logic; fine only for pure audit timestamps that are never compared for "same day" |
| Skipping the reconciliation pass and only checking "yesterday vs today" | Simpler code path, works if the app is opened daily | Multi-week gaps (the expected real usage pattern for a personal app) are handled incorrectly | Never — write the general pass from the start, it's not meaningfully more code than the single-step version |
| No encryption at rest for the Room database | Saves setup time, avoids SQLCipher dependency/build complexity | None meaningful | **Always acceptable here** — single device, offline, no network, no accounts; physical device access is already full compromise, so encryption adds complexity without changing the threat model |
| Read-modify-write counter increments in Kotlin instead of atomic SQL | Reads naturally, easy to unit test in isolation | Race conditions under rapid taps corrupt the counter/streak | Never once undo exists — the race is trivial to hit via Snackbar-undo |

## Integration Gotchas

No external services are integrated (explicitly offline, no network permission), so this section covers library-level gotchas instead of service integrations.

| Library | Common Mistake | Correct Approach |
|---------|-----------------|-------------------|
| Room | Relying on `exportSchema` default without actually committing `schemas/*.json` to version control, so `MigrationTestHelper` has nothing to migrate from | Commit exported schema JSON on every version bump; add an instrumented migration test per bump |
| `java.time` on Android | Using it below API 26 without enabling core-library desugaring, causing runtime crashes on the owner's actual device if it's older than API 26 | Enable `coreLibraryDesugaring` in Gradle (or use kotlinx-datetime) regardless of current minSdk, since this is a personal app likely to run on whatever device the owner already owns |
| Compose `LazyColumn` + `animateItemPlacement`/`animateItem` | Using index-based keys (default) instead of stable `key = achievement.id`, causing reorder animations (e.g. completed items moving) to attach to the wrong visual item | Always supply an explicit stable `key` per item in `LazyColumn`/`LazyRow` |
| `WorkManager` / background scheduling | Reaching for it to "check for missed periods overnight" | Out of scope by product decision (no notifications/background work) — the on-foreground reconciliation pass (Pitfall 4) is the only mechanism; do not introduce background scheduling to compensate |

## Performance Traps

This app's real data volume is tiny (one user, a handful of achievements/todos, a few thousand history rows after years of daily use) — most classic "performance at scale" traps do not apply. The traps that do matter are jank-on-device traps, not query-scale traps.

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|-----------------|
| Confetti/particle effect allocating new `Paint`/`Path`/particle objects every frame | Visible frame drops/stutter during the full-tab celebration, worse on older/lower-end devices | Reuse objects across frames (`remember`, object pools), draw via `drawWithCache`/a single overlay `Canvas` rather than per-item layers | Noticeable on any device the moment particle count is high enough to matter — test on the owner's actual (likely mid-range) device, not just an emulator |
| Full-screen celebration overlay stacked as nested semi-transparent `Box` layers on top of an already-drawn tab | Overdraw-driven jank, especially with GPU profiling (Show GPU overdraw) showing red/dark red | Compose the overlay only while active (not permanently present at `alpha = 0`), keep it to as few draw layers as possible | Visible on mid/low-end devices as soon as the overlay is combined with a populated list underneath |
| Unstable lambdas passed into list item composables (e.g. `onClick = { viewModel.increment(item) }` recreated every recomposition) | Whole tab recomposes on every counter tick instead of just the changed card; more noticeable once celebration animations are layered on top | Hoist stable callbacks (method references, `remember`-wrapped lambdas, or stable class with `@Immutable`/`@Stable`) so unrelated items skip recomposition | Doesn't matter at this app's list sizes for correctness, but compounds with confetti overdraw to produce visible jank on lower-end devices |
| Leaving an infinite-transition-driven micro-animation (e.g. shimmer/glow on a "just completed" card) running after the item scrolls off-screen or the tab changes | Background CPU/battery drain, animation still consuming frames when not visible | Scope infinite transitions to composable lifecycle (they stop automatically when the composable leaves composition) — but verify they're not hoisted somewhere that outlives the visible item | Only surfaces after extended daily use; easy to miss in short manual testing sessions |

## Security Mistakes

Threat model here is narrow (offline, single device, no accounts, no network), so most "security" concerns from typical checklists don't apply. The two that do:

| Mistake | Risk | Prevention |
|---------|------|------------|
| Leaving `android:allowBackup="true"` (the AndroidManifest default) with no exclusion rules while treating the Room DB as sensitive | Full app data (achievement/todo names, history) is included in `adb backup` / auto-backup to the user's Google account by default | Low actual risk for this product (personal habit names, not credentials) — explicitly decide whether that's acceptable (likely yes) rather than leaving it unexamined; document the decision |
| Over-engineering encryption-at-rest (SQLCipher, encrypted Room) "just in case" | Wasted build phase time, added dependency/build complexity, migration friction for zero real risk reduction given the threat model | Skip it — see Technical Debt Patterns table; this is a case where the "shortcut" (no encryption) is simply correct |

## UX Pitfalls

Common user experience mistakes specific to streak/counter/tier products.

| Pitfall | User Impact | Better Approach |
|---------|-------------|-------------------|
| Shaming/broken-chain imagery on a missed period (red X, cracked chain, guilt copy) | Makes a zero-grace streak reset feel punitive rather than neutral, and this product has *chosen* zero-grace deliberately — pairing that with shame visuals compounds the harshness and drives exactly the abandonment the "no grace period" decision risks | Keep the reset visually neutral/matter-of-fact (a plain "streak: 0" with the preserved "best: N" still visible) — let the *celebration* carry all the emotional intensity, not the failure state |
| Showing fractional progress UI (e.g. "0/1") on a target-of-1 achievement | Reads as a counter/quota when it's really a boolean "did you or didn't you" — adds visual noise to the simplest, most common card type | Render target=1 achievements as a simple checkbox/tick state, not a fraction; reserve the "N/target" progress display for achievements with target > 1 |
| Tier badge (Bronze/Silver/Gold) visually competing with or outranking the "done" indicator | Users conflate "reached a tier" with "completed," or think they must hit Gold to protect their streak, when tiers only grade and the target alone gates done-ness | Make the done/streak-protecting state the most visually dominant signal on the card (e.g. checkmark + color fill), with tier as a secondary badge — and resolve the open question of whether target must be ≥ lowest tier threshold before shipping tier UI, since an independent target/tier relationship can otherwise show "done" with no tier reached, which will read as a bug to the owner even if it's correct per spec |
| Full-intensity celebration animation on every routine, low-tier completion | Delightful in week one, actively annoying and something to swipe past by week four ("celebration fatigue") — undermines the core loop the product is built around | Keep the modest/no-tier tier's animation genuinely modest (sub-second, skippable, non-blocking) so animation intensity actually scales down for the common case; reserve full-burst treatment for Gold and full-tab clears, which stay rare by construction |
| Blocking input while a celebration animation plays | A daily-driver app that makes the user wait through an animation before they can tap the next thing trains them to dread the very feature meant to delight them | Celebration animations should be non-blocking/interruptible — a subsequent tap should be accepted immediately, not queued behind the animation finishing |

## "Looks Done But Isn't" Checklist

Things that appear complete but are missing critical pieces in this specific app.

- [ ] **Streak logic:** Often verified only for the single-day-gap case — verify with a fabricated "app not opened for 3 weeks" scenario for each of daily, weekly, and monthly periods, including a run that crosses a year boundary (week/month keys, not just day keys)
- [ ] **Room migrations:** Often verified only on a fresh install — verify with a `MigrationTestHelper` test that migrates a database pre-populated with realistic data and asserts nothing is lost
- [ ] **Celebration animation:** Often verified only on first composition in one orientation — verify it does NOT replay after rotation and does NOT replay after simulated process death (Developer Options → "Don't keep activities")
- [ ] **Undo:** Often verified only for a single deliberate tap — verify rapid double-tap increment, undo-after-navigating-away-and-back, and undo issued twice in a row don't corrupt the counter
- [ ] **Midnight rollover:** Often never tested at all — verify by advancing the emulator/device clock past midnight while a tab is open in the foreground and confirming cards flip to the new period without leaving the screen
- [ ] **Reduced motion:** Often verified only for Compose's built-in `animate*AsState` calls — verify the custom celebration/confetti effect specifically degrades when Settings → Accessibility → Remove animations is enabled
- [ ] **Todo expiry:** Often verified only for "one period late" — verify a todo assigned to a week/month that closed several periods ago correctly lands in the archive on next open, not silently lingering in the tab

## Recovery Strategies

When pitfalls occur despite prevention, how to recover.

| Pitfall | Recovery Cost | Recovery Steps |
|---------|-----------------|------------------|
| Destructive migration wiped real streak history | HIGH | No server backup exists by design; if `android:allowBackup` was on and an auto-backup predates the wipe, restore from that as a last resort. Otherwise unrecoverable — this is exactly why Pitfall 7's prevention (migration tests before any schema change touches real data) must be treated as non-negotiable rather than "recoverable later" |
| Week/year-boundary period-key bug corrupted a streak once a year | MEDIUM | Because streak = a derived value (not authored data), it can be recomputed from the underlying completion history once the period-key bug is fixed — write a one-time recompute-all-streaks routine that re-derives every streak from raw completion records rather than trusting the stored (possibly corrupted) streak counter |
| Celebration re-fired repeatedly due to state-derived triggering | LOW | Pure UI bug with no data corruption — fix the event/state modeling (Pitfall 8) and ship; no data recovery needed |
| Undo race corrupted a counter/streak value | MEDIUM | If mutations are logged (per Pitfall 9's prevention), replay the mutation log to rederive the correct counter value; if not logged, the true value is unrecoverable and the owner must manually correct it — another argument for building the mutation log from the start rather than treating it as optional |

## Pitfall-to-Phase Mapping

How roadmap phases should address these pitfalls.

| Pitfall | Prevention Phase | Verification |
|---------|-------------------|----------------|
| Millis/Instant period math (P1) | Data-model / period-engine phase | Unit tests using fabricated `LocalDate`/`YearMonth` values across a DST-transition date and a timezone change, with no real-clock dependency |
| Locale-dependent week start (P2) | Data-model / period-engine phase | Unit test run under at least two different device locales (e.g. `en-US`, `en-GB`) asserting identical week boundaries |
| ISO week-of-year as period key (P3) | Data-model / period-engine phase | Unit test spanning a real year-boundary date range (e.g. Dec 28 2026 – Jan 4 2027) asserting streak adjacency is unaffected |
| Missing reconciliation pass (P4) | Streak/period-engine phase | Unit test that fast-forwards "now" by 1, 2, and 5+ periods and asserts correct streak reset and todo expiry, without waiting real time |
| Midnight rollover in foreground (P5) | State/lifecycle phase | Manual/instrumented test advancing device clock past midnight with the app foregrounded, confirming UI updates without navigation |
| Clock-tamper handling undecided (P6) | Data-model phase (decision only) | Decision recorded in `PROJECT.md` Key Decisions; no build task exists for anti-tamper |
| Destructive Room migration (P7) | Persistence/Room phase | `MigrationTestHelper` instrumented test per schema version bump, run in CI/pre-commit before merge |
| Celebration as derived state (P8) | Celebration/animation phase (built on state-management phase's event/state pattern) | Manual test: rotate device and simulate process death after a completion; animation must not replay |
| Undo race (P9) | Persistence phase (atomic SQL) + completion/undo feature phase (mutation log) | Instrumented test issuing rapid concurrent increment/undo calls, asserting final counter matches expected net delta |
| Reduced-motion ignored by custom animation (P10) | Celebration/animation phase | Manual test with system "Remove animations" enabled; celebration must degrade to near-instant |
| Undefined mid-streak edit semantics (P11) | Data-model phase (decision) then achievement-edit feature phase (implementation) | Test editing an achievement's period/target while streak > 0, asserting the documented rule is what actually happens |

## Sources

- [Understanding migrations with Room — Android Developers (Medium)](https://medium.com/androiddevelopers/understanding-migrations-with-room-f01e04b07929)
- [Migrate your Room database — Android Developers](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- [Room Database Migrations: Changing Your Schema Without Losing User Data — DEV Community](https://dev.to/myougatheaxo/room-database-migrations-changing-your-schema-without-losing-user-data-54ha)
- [Side-effects in Compose — Android Developers](https://developer.android.com/develop/ui/compose/side-effects)
- [How To Handle ViewModel One-Time Events In Jetpack Compose — droidcon](https://www.droidcon.com/2022/10/12/how-to-handle-viewmodel-one-time-events-in-jetpack-compose/)
- [StateFlow vs SharedFlow in Compose — Mobile Dev Notes](https://www.valueof.io/blog/stateflow-vs-sharedflow-jetpack-compose)
- [Android, Animations and Reduced Motion — Eevis Panula](https://eevis.codes/blog/2022-12-12/android-animations-and-reduced-motion/)
- [Reduced animations in Jetpack Compose — Appt](https://appt.org/en/docs/jetpack-compose/samples/reduced-animations)
- [WeekFields — Java SE 20 API docs](https://docs.oracle.com/en/java/javase/20/docs/api/java.base/java/time/temporal/WeekFields.html)
- [ISO Week Numbers Explained: Week 1, Week 53 & Year Boundaries — CalendarZ](https://www.calendarz.com/blog/iso-week-numbers-explained-week-1-week-53-and-year-boundaries)
- [Room + Time — Chris Banes (Android Developers)](https://medium.com/androiddevelopers/room-time-2b4cf9672b98)
- [Streak Timezone & DST Handling: The Complete Guide — Trophy](https://trophy.so/blog/streak-timezone-dst-handling)
- [Handling Time Zones in Gamification — Trophy](https://trophy.so/blog/handling-time-zones-gamification)
- [How to Build a Streaks Feature — Trophy](https://trophy.so/blog/how-to-build-a-streaks-feature)
- [Designing A Streak System: The UX And Psychology Of Streaks — Smashing Magazine](https://www.smashingmagazine.com/2026/02/designing-streak-system-ux-psychology/)
- [I Tested 10 Habit Trackers in 30 Days. 8 Broke Me the Same Way. — Tyler Ward (Medium)](https://medium.com/@wardtylerd/i-tested-10-habit-trackers-in-30-days-8-broke-me-the-same-way-9803ea20b228)

---
*Pitfalls research for: offline Android habit/streak tracker (Kotlin, Compose, Room)*
*Researched: 2026-07-27*
