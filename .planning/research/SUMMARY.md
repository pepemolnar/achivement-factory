# Project Research Summary

**Project:** Achievement Factory
**Domain:** Native Android habit/achievement tracker (offline-only, single-user, animation-heavy)
**Researched:** 2026-07-27
**Confidence:** HIGH

## Executive Summary

Achievement Factory is a single-device, single-user Android habit/achievement tracker built on Kotlin + Jetpack Compose + Room, with no network, no accounts, and no server backup. Experts build this class of app as a small, layer-then-feature single-module Android app: a pure-Kotlin period/date engine at the core, a thin Room persistence layer treating the completion log as the source of truth, calculators (streak, tier) derived from that log rather than incrementally mutated, and a Compose UI that strictly separates persisted state from one-shot events. The differentiators that make this app worth building at all -- the Bronze/Silver/Gold grading ladder over a plain counter, escalating celebration animations, and a merged achievement+todo view with a full-tab "cleared the board" celebration -- are all genuinely uncommon in the surveyed competitor set (Streaks, Loop, HabitKit, Habitica, Done, TickTick), which validates the product's core bet.

Three cross-cutting risks surfaced independently across every research thread and should dictate phase ordering more than feature listing does. First, date/period representation is the highest-risk area in the whole app: it must be built on civil calendar types (java.time.LocalDate/YearMonth), never epoch-millis or Instant arithmetic; the Monday week-start rule must be hardcoded (TemporalAdjusters, never a locale-dependent WeekFields/Calendar API); and week identity must be the LocalDate of the Monday that starts the week -- never an ISO week-of-year number, which breaks at year boundaries (2026 itself has 53 ISO weeks). Every other feature -- streaks, tiers, celebrations, todo expiry -- depends on this being correct, so it must be settled and unit-tested before anything else is built on top of it. Second, celebration animations must be architected as one-shot events on a non-replaying Channel/SharedFlow, never derived from persisted StateFlow state -- this decision must be made in the state-management layer before any animation code exists, because retrofitting it later means rewriting the trigger plumbing under an already-built UI. Third, Room schema export and migration testing must exist before the app is installed on the owner's real device -- there is no cloud backup of streak history, so fallbackToDestructiveMigration() (fine during pre-install prototyping) must be replaced by real Migration objects and MigrationTestHelper instrumented tests the moment real data exists, or a future schema change will silently wipe years of streak history with no recovery path.

The recommended approach is a seven-step build order that mirrors dependency reality: period engine, Room schema, repositories (raw CRUD), domain calculators (streak/tier, layered on stable repositories), ViewModels with correct state/event plumbing (still no animation), a fully usable but visually flat Compose UI, then celebration/animation layer last, built on a foundation already proven correct. This produces a natural two-phase-cluster shape for the roadmap: "data + core loop" (functionally complete, visually flat) followed by "celebration polish" (the actual differentiator, safe to build last because by then there is nothing left to get wrong about when to fire, only what to render).

## Key Findings

### Recommended Stack

Native Kotlin 2.3.20 + Jetpack Compose (BOM 2026.04.01) + Room 2.8.4, targeting minSdk 26 (native java.time, no desugaring needed) and compileSdk 36, built with AGP 9.2.0/Gradle 9.1.x and KSP (never KAPT) for Room's annotation processing. Dependency injection is a hand-written AppContainer, not Hilt -- the object graph (one database, 2-3 repositories, ~5 ViewModels) doesn't justify a DI framework's build-time and cognitive cost. Navigation Compose hosts a single NavHost with tabs implemented as a HorizontalPager (lateral state), not three separate nav routes. java.time is used directly (not kotlinx-datetime, which is pre-1.0 and only pays off for multiplatform). Celebration animation uses stock Compose (animateFloatAsState, updateTransition) for the small/medium tiers and Konfetti (konfetti-compose 2.0.5) for the Gold/full-tab burst, since hand-rolling a particle system is real engineering effort not worth taking on. Room 3.0.0 is deliberately avoided (four weeks old at research time, breaking package/API changes) in favor of mature, KSP-first Room 2.8.4.

**Core technologies:**
- Kotlin 2.3.20 + Jetpack Compose BOM 2026.04.01 -- current stable, K2 compiler, strongest animation APIs of any Android UI toolkit option
- Room 2.8.4 + KSP -- mature, Flow-native local persistence; KSP is ~2x faster than KAPT with no Java stub generation
- java.time (LocalDate, YearMonth, TemporalAdjusters) -- civil calendar types available natively at minSdk 26, no desugaring needed
- Navigation Compose 2.9.8 -- gives predictive-back-gesture support for the edit/archive screens for free
- Konfetti-compose 2.0.5 -- purpose-built, Compose-native particle library for the one animation tier worth a third-party dependency
- Hand-written AppContainer (no DI framework) -- object graph is small enough that Hilt's ceremony costs more than it saves

### Expected Features

**Must have (table stakes):** one-tap/low-friction completion; counter with visible partial progress; undo; current streak; best streak; edit/archive/delete CRUD; icon or color per achievement (flagged as a gap -- not explicit in PROJECT.md but expected by every competitor surveyed); manual reordering within a tab (same gap status); low-ceremony empty/first-run state; period-grouped tabs.

**Should have (competitive differentiators -- already in scope and worth the investment):** escalating celebration animation scaled by tier reached (no competitor surveyed does this -- most give flat, binary feedback); Bronze/Silver/Gold grading ladder over a single counter (niche but validated pattern, distinct from Habitica's difficulty-scaling and Duolingo's social leagues); full-tab "cleared everything" celebration (no surveyed app has this); achievements and todos merged into one period-scoped feed (every competitor keeps these as separate lists/modules -- this is a genuine, deliberate risk worth taking, but needs a clear visual language so todo cards don't look like they carry a streak); haptic feedback on completion; a cheap streak "alive" glyph as a P2 addition.

**Defer (v2+):** stats dashboards/charts/trend lines (actively named in category research as a driver of app abandonment -- confirmed correct to exclude); notifications/reminders (correctly deferred for a self-motivated single user, cheap to add later if "forgot to open the app" becomes the actual failure mode instead of "forgot to do the habit"); backfilling past periods (flagged as the exclusion most likely to cause real friction -- recommend monitoring closely as the first v1.x candidate); streak freeze/grace period (deliberately, philosophically excluded -- mitigated by retaining best-streak-ever); widgets and categories/grouping beyond the three tabs.

### Architecture Approach

Single Gradle module, layer-then-feature packaging (period/, data/, domain/, ui/), following official Android architecture guidance scaled down for an app with one real domain concept rather than many cross-cutting flows. The period/ package is isolated and dependency-free by design -- it's the correctness foundation everything else assumes. Streaks and tiers are computed from an immutable progress log (one row per achievement per period, UNIQUE(achievementId, periodKey)), never trusted as live-mutated counters; cached columns on the achievement entity are a read-optimization only, reconciled via a bounded backward walk whenever the cache is behind the current period key. Todo expiry runs the same way: a lazy, pull-based sweep at app-open/resume, deliberately not WorkManager, since the product has no background-guarantee requirement (open-the-app-only by design).

**Major components:**
1. PeriodKeyFactory (pure Kotlin, java.time-based) -- turns "now" into a comparable, storable period identity; the single place all date math happens
2. AchievementRepository / TodoRepository -- own DAOs, orchestrate increment/undo transactions and tier/streak snapshotting
3. StreakCalculator / TierEvaluator -- pure, unit-testable domain functions, no Android dependency
4. TabViewModel -- combines repository Flows into StateFlow<UiState> while emitting CelebrationEvents on a separate, non-replaying Channel
5. CelebrationOverlay -- collects the event stream via LaunchedEffect/repeatOnLifecycle, renders tier-scaled animation + haptics, built last

### Critical Pitfalls

1. **Period math on epoch-millis/Instant instead of civil dates** -- breaks silently on DST/timezone/leap-day edge cases that don't show up in casual same-timezone testing. Use LocalDate/YearMonth as the canonical period key, converted from Instant exactly once per read.
2. **Locale-dependent week-start APIs** (Calendar.getInstance().firstDayOfWeek, WeekFields.of(Locale.getDefault())) silently override the Monday-start rule if the device region ever changes. Hardcode DayOfWeek.MONDAY via TemporalAdjusters everywhere; never touch a locale-based week API.
3. **ISO week-of-year as a period key breaks at year boundaries** (2026 has 53 ISO weeks; Jan 1 can belong to the previous ISO year). Use the Monday LocalDate itself as week identity -- adjacency becomes a simple plusDays(7) comparison, no week-number logic anywhere.
4. **Celebration modeled as derived UI state instead of a one-shot event** re-fires on rotation, process restoration, or unrelated recomposition -- the single most delight-critical moment in the app becomes the most annoying within days. Model it as a Channel/SharedFlow(replay = 0), collected with repeatOnLifecycle, decided in the state-management phase before celebration UI exists.
5. **fallbackToDestructiveMigration() left in place past first real-data install** silently wipes all streak history on the next schema change, with no server backup to recover from. exportSchema = true committed to VCS, real Migration objects, and MigrationTestHelper instrumented tests must exist before the app is installed on the owner's device with real data -- this is the highest-cost possible bug in the app.

## Implications for Roadmap

Based on research, suggested phase structure:

### Phase 1: Period Engine & Domain Foundations
**Rationale:** Every other feature -- streaks, tiers, todo expiry, celebrations -- depends on period identity being correct. This is a foundational type choice, not something to retrofit; get it wrong and every later phase inherits the bug.
**Delivers:** PeriodType, PeriodKeyFactory (current/previous/adjacency), pure StreakCalculator and TierEvaluator functions, all unit-tested against fabricated multi-period gaps, year-boundary weeks, DST-adjacent dates, and locale variation -- no UI, no persistence yet.
**Addresses:** Underpins all "Achievements -- counters and tiers," "Completion and streaks," and "Todos" requirements from PROJECT.md.
**Avoids:** Pitfalls 1, 2, 3, 6 (millis-based math, locale week APIs, ISO week-of-year, undecided clock-tamper stance -- the last is a documented decision, not build work).

### Phase 2: Persistence Layer
**Rationale:** Schema and migration discipline must exist before real data accumulates on the owner's device; this is cheapest to get right before the app is ever installed for daily use.
**Delivers:** AchievementEntity, ProgressEntity, TodoEntity, DAOs, AppDatabase with exportSchema = true and committed schema JSON from the first commit, atomic SQL increment/undo via @Transaction (never Kotlin read-modify-write), a mutation-log design for undo.
**Uses:** Room 2.8.4 + KSP, androidx.room Gradle plugin for schema export config.
**Implements:** The data layer from Architecture research, including the UNIQUE(achievementId, periodKey) constraint and snapshotted (not live-derived) done/tierReached columns.
**Avoids:** Pitfall 7 (destructive migration risk) and Pitfall 9 (undo race) -- both flagged as needing to be correct before Phase 3 ships to the owner's real device.

### Phase 3: Repositories, Reconciliation & Core Loop
**Rationale:** With period math and persistence both proven, wire up CRUD, the increment/undo transaction, and the reconciliation pass (streak recompute + todo expiry) that must run on every app foreground given there's no background worker.
**Delivers:** AchievementRepository, TodoRepository, AppMaintenanceUseCase (reconciliation pass tested against fabricated multi-period gaps of 1, 2, 5+ periods), midnight-rollover handling via ACTION_DATE_CHANGED/ACTION_TIME_CHANGED/ACTION_TIMEZONE_CHANGED broadcasts.
**Addresses:** "Achievements -- lifecycle," full streak/best-streak requirements, todo expiry-to-archive.
**Avoids:** Pitfalls 4 and 5 (missing reconciliation pass, unhandled foreground midnight rollover).

### Phase 4: ViewModels, State/Event Plumbing & Flat UI Shell
**Rationale:** Build the full app end-to-end with correct data and correct state/event separation before any animation exists -- this is the point research says the app becomes fully usable, just visually flat, and is deliberately the phase where the event-channel pattern for celebrations must be settled architecturally even though no animation plays yet.
**Delivers:** TabViewModel (+ edit/archive ViewModels) combining repository Flows into StateFlow<UiState>, with an already-wired Channel<CelebrationEvent> emitting placeholder events; single Activity + NavHost + TabPagerScaffold; three tabs, archive, edit screens rendering plain checkmarks/counters/tier badges -- no confetti.
**Implements:** Unidirectional StateFlow-per-screen pattern, tabs-as-Pager-not-nav-routes pattern.
**Avoids:** Pitfall 8 set up correctly at the plumbing level (event stream never conflated with state) even before celebration visuals exist, so celebration work in Phase 5 has nothing left to get wrong about when to fire.

### Phase 5: Celebration & Animation Polish
**Rationale:** Deliberately last -- it is purely reactive to state transitions that Phases 1-4 already compute correctly, so this phase is pure visual/animation work on a proven-correct foundation. This is also the app's actual differentiator and core value, so it deserves focused, unhurried attention rather than being interleaved with data-correctness work.
**Delivers:** Tier-scaled CelebrationEvent handling (tick, Bronze, Silver, Gold, full-tab), Konfetti-based Gold/full-tab burst, haptics via LocalHapticFeedback/custom VibrationEffect waveforms, reduced-motion accessibility branch, non-blocking/interruptible animation.
**Uses:** Konfetti-compose, Compose animateFloatAsState/updateTransition, LocalAccessibilityManager.isReduceMotionEnabled.
**Avoids:** Pitfalls 8 and 10 (event-vs-state re-fire bug, reduced-motion silently ignored by custom particle code) -- both have explicit manual test protocols (rotate device, kill process, enable "Remove animations") that should be acceptance criteria for this phase.

### Phase Ordering Rationale

- Dependency order is nearly linear: period math, persistence, business logic, UI plumbing, animation -- matching the "Suggested Build Order" from Architecture research almost exactly.
- Grouping into 5 phases (rather than 7 granular steps) balances phase-transition overhead against the natural "data + core loop" vs. "celebration polish" split the architecture research explicitly calls out.
- Placing Room migration discipline in Phase 2 -- before Phase 3's real usage or Phase 4's UI exists -- directly avoids the single highest-cost data-loss bug identified (destructive migration wiping unrecoverable streak history).
- Placing the celebration event/state architecture decision in Phase 4 (plumbing) rather than Phase 5 (visuals) avoids retrofitting a state/event split under an already-built animation layer.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 1 (Period Engine):** Worth a --research-phase pass specifically on java.time/TemporalAdjusters edge-case test design (year-boundary weeks, DST transitions) -- the mechanics are well-documented but the exhaustive test matrix is easy to under-specify.
- **Phase 5 (Celebration):** Worth research on Konfetti API specifics (emitter configuration, performance tuning for the full-tab burst) and on the platform VibratorManager/VibrationEffect.compose() API for a bespoke escalating haptic pattern, since this steps below Compose's high-level haptics API.

Phases with standard patterns (skip research-phase):
- **Phase 2 (Persistence):** Room schema/migration patterns are extensively documented in official Android guidance; the STACK.md and ARCHITECTURE.md research already provide concrete entity/DAO code.
- **Phase 3 (Repositories/Reconciliation)** and **Phase 4 (ViewModels/UI shell):** Standard StateFlow/repository/ViewModel patterns per official Android architecture guidance, already concretely specified in ARCHITECTURE.md.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Verified against official Android/Kotlin/Room/Compose release notes; only DI opinion and exact KSP patch pinning are MEDIUM |
| Features | MEDIUM | Web-search-based competitor survey across 6+ apps, cross-checked, but no official API docs involved since this is a UX/landscape question |
| Architecture | HIGH for layering/UI/Room patterns (official guidance); MEDIUM for the specific streak/period-key design (synthesized reasoning, no single authoritative source for this exact app shape) |
| Pitfalls | HIGH for Room/Compose/java.time mechanics (official docs + corroborated community sources); MEDIUM for streak psychology/UX claims (blog sources, not peer-reviewed) |

**Overall confidence:** HIGH

### Gaps to Address

- **Mid-streak edit semantics** (editing an achievement's period or target while a streak is active) is explicitly unresolved in PROJECT.md and flagged by both Architecture and Pitfalls research as needing an owner decision before the edit-achievement feature is built, not during it. Architecture research's recommended default: changing the period unit resets current streak to 0 (best-streak preserved); changing only the target preserves the current streak without retroactively re-evaluating past periods. Needs explicit owner ratification.
- **Relationship between counter target and lowest tier threshold** -- currently left independent by default (an achievement can be "done" with no tier reached, or vice versa in principle). Pitfalls research flags this as likely to read as a bug to the owner even if correct per spec. Needs explicit owner ratification before tier UI is built.
- **Icon/colour per achievement and drag-to-reorder** -- both are table-stakes in every competitor surveyed and flagged as gaps not explicit in PROJECT.md's Active scope. Recommend the owner explicitly decide whether these belong in v1 (low cost, meaningfully improve daily usability) or are deliberately deferred.
- **android:allowBackup stance** -- low actual risk (personal habit names, not credentials) but Pitfalls research recommends an explicit documented decision rather than leaving the manifest default unexamined.

## Sources

### Primary (HIGH confidence)
- developer.android.com/topic/architecture, /recommendations, /ui-layer, /data-layer/offline-first, /modularization/patterns -- official Android architecture guidance
- developer.android.com/build/releases/agp-9-2-0-release-notes, kotlinlang.org/docs/whatsnew2320.html, developer.android.com/jetpack/androidx/releases/room, /navigation, /lifecycle -- stack version verification
- developer.android.com/training/data-storage/room/migrating-db-versions, developer.android.com/kotlin/flow/stateflow-and-sharedflow -- Room migration and state/event pattern guidance
- docs.oracle.com WeekFields API docs, developer.android.com/studio/write/java8-support-table -- java.time/desugaring mechanics
- github.com/DanielMartinus/Konfetti -- Konfetti version and Compose-native support confirmation

### Secondary (MEDIUM confidence)
- Competitor survey: Streaks, Loop Habit Tracker, HabitKit, Habitica, Done, TickTick (app store listings, official sites, reviews) -- feature landscape and anti-pattern validation
- manuelvivo.dev/viewmodel-events-antipatterns, costafotiadis.com -- Channel/SharedFlow one-shot event pattern corroboration
- trophy.so streak/timezone blog series, smashingmagazine.com streak UX/psychology piece -- streak design and grace-period debate context
- zacsweers.dev/metro-is-stable, vinted.engineering Dagger-to-Metro case study -- DI alternative landscape (not the chosen recommendation)

### Tertiary (LOW confidence)
- Individual Medium/ProAndroidDev pieces on Hilt vs Koin vs manual DI -- used only to confirm mainstream consensus, not as a standalone basis for the manual-DI recommendation

---
*Research completed: 2026-07-27*
*Ready for roadmap: yes*
