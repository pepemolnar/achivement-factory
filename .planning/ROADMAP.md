# Roadmap: Achievement Factory

## Overview

Achievement Factory ships as five vertical slices, each one installable and usable end-to-end on the owner's own phone before the next slice begins. Phase 1 deliberately stays narrow — daily achievements only — but settles the highest-risk foundation correctly and once: civil-date period math, safe Room persistence (schema export, no destructive migration), and a one-shot celebration-event architecture, so nothing downstream has to be retrofitted. Phase 2 widens the achievement model to its full shape: weekly and monthly periods across three tabs, plus the Bronze/Silver/Gold tier ladder and celebration that scales with it. Phase 3 completes the achievement lifecycle — editing, archiving, restoring, deleting, reordering. Phase 4 adds one-off todos alongside achievements and a unified archive. Phase 5 closes the loop with the app's actual differentiator: the full-tab clear celebration, tier-matched haptics, and reduced-motion accessibility — pure polish on a foundation already proven correct.

## Phases

**Phase Numbering:**

- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Daily Achievements — Core Loop & Foundation** - Create, track, undo, and streak a daily achievement on a correctly modelled period and persistence foundation
- [ ] **Phase 2: Full Achievement Model — Periods & Tiers** - Extend to weekly/monthly periods across three tabs, plus the Bronze/Silver/Gold tier ladder and its scaled celebration
- [ ] **Phase 3: Achievement Management — Edit, Archive & Reorder** - Full lifecycle control over existing achievements, with history that can never be made easier retroactively
- [ ] **Phase 4: Todos & Unified Archive** - One-off todos alongside achievements in every tab, with a shared archive for anything archived or expired
- [ ] **Phase 5: Full-Tab Celebration & Motion Polish** - The distinct full-tab clear celebration, tier-matched haptics, and reduced-motion support

## Phase Details

### Phase 1: Daily Achievements — Core Loop & Foundation

**Goal**: User can create a daily achievement via a simple creation form, track it to completion on a Daily tab screen, and trust that its streak, undo, and stored data are all correct — on a period-math and persistence foundation solid enough that no later phase has to retrofit it.
**Mode:** mvp
**Depends on**: Nothing (first phase)
**Requirements**: ACH-01, ACH-02, TIER-01, TRACK-01, TRACK-02, TRACK-03, TRACK-04, TRACK-05, TRACK-06, TRACK-07, TRACK-08, TRACK-10, PERIOD-01, PERIOD-04, PERIOD-05, PERIOD-06, NAV-04, CELEB-04, DATA-01, DATA-02, DATA-03, DATA-04, DATA-05
**Success Criteria** (what must be TRUE):

  1. User can create a daily achievement with a name, icon, colour, and counter target (defaulting to 1) from the creation form; it appears immediately on the Daily tab screen, and an empty Daily tab shows a helpful empty state explaining how to add the first item.
  2. User can increment the counter, see partial progress visually distinct from "done," have it become done when the count reaches the threshold, and undo a mistaken increment — only today is editable, and once a day closes, its recorded result never changes afterward.
  3. Current streak and best streak are tracked correctly per achievement, resetting to zero on any missed day (including multi-day gaps found after a long absence), refreshing automatically if the app is left open across midnight, and staying correct across daylight-saving transitions, timezone changes, and manual clock edits.
  4. Completing a daily achievement fires a celebration exactly once, never replaying on device rotation, recomposition, or app restart.
  5. All achievement data survives process death and device restart, the app works fully offline with no INTERNET permission declared, and Room schema export plus migration-safety discipline (no destructive fallback) are in place before this build is installed on the real device.

**Plans**: 8 plans
Plans:
**Wave 1**

- [x] 01-01-PLAN.md — Toolchain, Gradle scaffold, civil-date period engine, Room schema v1 with export

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 01-02-PLAN.md — Daily tab: repository Flow to StateFlow, resting card, Done section, empty state

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 01-03-PLAN.md — Create a daily achievement from a full-screen form behind the FAB

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 01-04-PLAN.md — Tick off: whole-card tap, filling ring, done state, atomic undo

**Wave 5** *(blocked on Wave 4 completion)*

- [x] 01-05-PLAN.md — Streaks derived from the completion log, reconciled after absence, flame badge

**Wave 6** *(blocked on Wave 5 completion)*

- [ ] 01-06-PLAN.md — Read-only detail screen via long-press, and live period rollover

**Wave 7** *(blocked on Wave 6 completion)*

- [ ] 01-07-PLAN.md — The completion celebration: one-shot events, sequenced animation, reduced motion

**Wave 8** *(blocked on Wave 7 completion)*

- [ ] 01-08-PLAN.md — Ship: signed release APK, migration harness, recorded conventions

**UI hint**: yes

### Phase 2: Full Achievement Model — Periods & Tiers

**Goal**: Achievements grow to their full shape: any of the three periods, reachable via three-tab navigation, and an optional Bronze/Silver/Gold tier ladder whose celebration scales to match how far the achievement was pushed.
**Mode:** mvp
**Depends on**: Phase 1
**Requirements**: PERIOD-02, PERIOD-03, NAV-01, TIER-02, TIER-03, TIER-04, TIER-05, TIER-06, TIER-07, TRACK-09, CELEB-01, CELEB-02
**Success Criteria** (what must be TRUE):

  1. User can create achievements on a weekly (Monday–Sunday) or monthly (calendar month) period, in addition to daily, and switch between three tabs — Daily, Weekly, Monthly — via the app's tab navigation to see each period's items.
  2. User can attach a Bronze/Silver/Gold tier ladder to an achievement, setting increasing thresholds per tier; the app accepts only valid downward-filling sets (Gold-only, Silver+Gold, or Bronze+Silver+Gold) and rejects any set that omits Gold or skips a level.
  3. The lowest configured tier's threshold is the achievement's done threshold, the achievement's card shows the tier reached in the current period, and the app records the highest tier reached each completed period.
  4. Completing an achievement fires a celebration whose intensity scales with the tier reached, and crossing into a higher tier within the same period fires the corresponding upgraded animation.

**Plans**: TBD
**UI hint**: yes

### Phase 3: Achievement Management — Edit, Archive & Reorder

**Goal**: User can fully manage an achievement's lifecycle from an edit screen — cosmetic changes, threshold raises, archiving, restoring, permanent deletion, and drag-to-reorder — without ever being able to make a streak's history easier retroactively.
**Mode:** mvp
**Depends on**: Phase 2
**Requirements**: ACH-03, ACH-04, ACH-05, ACH-06, ACH-07, ACH-08, ACH-09
**Success Criteria** (what must be TRUE):

  1. User can edit an achievement's name, icon, and colour at any time without affecting its streak.
  2. User can raise an achievement's counter threshold or tier thresholds after creation and keep the existing streak, but the app prevents lowering any threshold or changing the period once the achievement is created.
  3. User can archive an achievement — removing it from its tab while its history stays intact — and restore it back into the tab later.
  4. User can permanently delete an archived achievement and its history, after an explicit confirmation step.
  5. User can reorder achievements within a tab by dragging, and the new order persists across app restarts.

**Plans**: TBD
**UI hint**: yes

### Phase 4: Todos & Unified Archive

**Goal**: One-off todos join achievements on every tab screen, sharing a single creation flow and a unified archive screen for anything archived or expired, with restore and permanent-delete available in one place.
**Mode:** mvp
**Depends on**: Phase 3
**Requirements**: TODO-01, TODO-02, TODO-03, TODO-04, TODO-05, TODO-06, NAV-02, NAV-03
**Success Criteria** (what must be TRUE):

  1. User can create a one-off todo assigned to today, this week, or this month, and it appears in the tab matching its assigned period, alongside achievements, visually distinct from them (no counter, tier, or streak).
  2. User can complete a todo with a single tick and undo it.
  3. An unfinished todo automatically moves to the archive when its assigned period ends, however many periods ago that was.
  4. User can restore an archived todo by assigning it to a current day, week, or month, or permanently delete it.
  5. User can reach an archive view listing both archived achievements and expired todos together, with restore and permanent-delete actions available for each.

**Plans**: TBD
**UI hint**: yes

### Phase 5: Full-Tab Celebration & Motion Polish

**Goal**: The full-tab celebration, tier-matched haptics, and reduced-motion support ship as the last layer on the celebration overlay component, built on a foundation already proven correct in every earlier phase.
**Mode:** mvp
**Depends on**: Phase 4
**Requirements**: CELEB-03, CELEB-05, CELEB-06
**Success Criteria** (what must be TRUE):

  1. Clearing an entire tab — every achievement done and every todo ticked — fires a distinct, larger full-tab celebration, visibly different from any single-item celebration.
  2. Haptic feedback intensity matches the tier reached, escalating from a light tick up through the full Gold/full-tab pattern.
  3. Every celebration animation, including the full-tab burst, collapses to a near-instant effect when the system's reduced-motion ("remove animations") accessibility setting is enabled.

**Plans**: TBD
**UI hint**: yes

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Daily Achievements — Core Loop & Foundation | 5/8 | In Progress|  |
| 2. Full Achievement Model — Periods & Tiers | 0/TBD | Not started | - |
| 3. Achievement Management — Edit, Archive & Reorder | 0/TBD | Not started | - |
| 4. Todos & Unified Archive | 0/TBD | Not started | - |
| 5. Full-Tab Celebration & Motion Polish | 0/TBD | Not started | - |
