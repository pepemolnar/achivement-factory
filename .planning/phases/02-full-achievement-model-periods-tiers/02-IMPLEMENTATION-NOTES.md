# Phase 2 — Implementation Notes

**Status:** Implemented 2026-10-26 outside the GSD workflow (at the owner's request), in a session
that could not run Gradle. **Not yet compiled or tested** — run `./gradlew :app:testDebugUnitTest`
and `assembleDebugAndroidTest` before treating anything here as done.

Requirements covered: PERIOD-02, PERIOD-03, NAV-01, TIER-02 – TIER-07, TRACK-09, CELEB-01, CELEB-02.

## Decisions made in place of a discuss step

- **No schema change, no migration.** Schema v1 already carried `bronze/silver/goldThreshold` and
  `progress.tierReached`. `APP_DATABASE_VERSION` stays 1.
- **Done = Gold; streak = lowest tier** (TIER-06, revised by the owner on 2026-10-26 after using the
  build). The lowest tier is stored in `counterTarget`, so the progress row's `done` column keeps
  meaning "counts toward the streak" and the Phase 1 SQL and streak walk are unchanged — no
  migration, existing data stays valid. Card/detail `isDone` for a laddered achievement is read
  from the snapshotted `tierReached == GOLD`, and the card's ring fills toward Gold.
- **Three edges per tap:** `justCompleted` (reached Gold / plain target → `Completed`, card sinks),
  `streakSecured` (reached the lowest tier → flame ticks), `tierUpgraded` (Bronze/Silver below Gold
  → `TierUpgraded`, ring stays open, no hold-back). Gold after a lower medal does not re-tick the
  flame.
- **`tierReached` is snapshotted in the same atomic UPDATE** as the count (increment and undo), via a
  `CASE` over nullable thresholds — never re-derived on read (TRACK-09/TRACK-10). Undo within the
  open period lowers it with the count.
- **Ladder validation lives in `domain/tier/TierLadder`** (pure Kotlin); the constructor refuses
  invalid ladders, `validate` returns a typed `TierLadderError`, and stored rows are read
  tolerantly (`fromStoredColumns` → null rather than throwing out of a read flow).
- **One `CurrentPeriodProvider` with a `StateFlow` per `PeriodType`**; `currentDailyKey` kept as a
  shorthand. `PeriodWatcher` reconciles every period type with its own key on each signal.
- **Tabs are lateral state inside the `daily` route** (as SKELETON.md required): a saveable
  `selectedTab`, a `NavigationBar` slotted into the tab screen's own Scaffold, and one
  `PeriodViewModel` per tab (`viewModel(key = "period-<TYPE>")`). The screen is wrapped in
  `key(selectedTab)` so snackbar/celebration state never crosses tabs.
- **Renames:** `ui/daily` → `ui/period`; `DailyViewModel/DailyUiState/DailyScreen` →
  `PeriodViewModel/PeriodUiState/PeriodScreen`; `DailyAchievement` → `TrackedAchievement`;
  `AchievementDetail.todayCount` → `periodCount`; `observeDaily()` → `observePeriod(type)`.
- **Create form:** a "Repeats" segmented picker (Daily/Weekly/Monthly, defaulting to the tab the
  FAB was pressed on, via `create?periodType=`), and a "Medal tiers" switch in Advanced that
  replaces the counter-target field with Bronze (optional) / Silver (optional) / Gold fields.
  Errors are shown on save, under the fields; an invalid ladder opens Advanced.
- **Celebration scaling is spec-only** (`MotionSpecs` tier table: check-pulse peak, burst particle
  count/radius/duration, badge pop). No-ladder achievements play exactly the Phase 1 sequence with
  no burst. The burst is a stock-Compose `Canvas` (`CelebrationBurst`); Konfetti stays in Phase 5.
- **Card:** a `TierBadge` (medal + fixed name, fixed metal colours in `ui/theme/TierColors.kt`)
  for the tier reached this period (TIER-07); progress text counts toward the *next* tier. Done
  cards stay tappable — that is how Silver/Gold are reached — and can play an upgrade celebration.
- **`AchievementCard.onCelebrationSettled` now receives the event**, so a settle report for an
  event that was replaced by a newer one (an upgrade landing mid-completion) doesn't drop the newer.

## New tests

`TierLadderTest`, `PeriodsAndTiersRepositoryTest`, `TierCelebrationEventTest`, `TierCardTest`, plus
new cases in `CurrentPeriodProviderTest` and `CreateAchievementViewModelTest`. Existing tests were
migrated to the renamed APIs.

## Still to do for this phase

- Compile + run the unit-test gate; fix whatever the compiler finds.
- On-device pass: tab switching, weekly/monthly rollover, tier celebrations at each tier,
  reduced-motion collapse of the burst, `CelebrationInstrumentedTest`.
- Code review, then mark requirements complete in REQUIREMENTS.md / ROADMAP.md.
