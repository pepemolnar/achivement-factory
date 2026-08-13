# Requirements: Achievement Factory

**Defined:** 2026-07-27
**Core Value:** The satisfying loop — check off → streak ticks up → celebration — good enough that you actually open the app every day.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Achievements

- [x] **ACH-01**: User can create an achievement with a name and a period of daily, weekly, or monthly
- [x] **ACH-02**: User can assign an icon and an accent colour to an achievement
- [ ] **ACH-03**: User can edit an achievement's name, icon, and colour at any time without affecting its streak
- [ ] **ACH-04**: User can raise an achievement's done threshold or tier thresholds after creation and keep the existing streak
- [ ] **ACH-05**: App prevents lowering a threshold or changing the period after creation, so a streak can never be made easier retroactively
- [ ] **ACH-06**: User can archive an achievement, removing it from its tab while preserving its history
- [ ] **ACH-07**: User can restore an archived achievement back into its tab
- [ ] **ACH-08**: User can permanently delete an archived achievement and its history, with a confirmation step
- [ ] **ACH-09**: User can reorder achievements within a tab by dragging, and the order persists across app restarts

### Tiers

- [x] **TIER-01**: User can create an achievement with a plain counter target and no tiers, defaulting to a target of 1
- [ ] **TIER-02**: User can attach a tier ladder to an achievement and set the threshold count for each tier
- [ ] **TIER-03**: Tier names are fixed as Bronze, Silver, and Gold, and cannot be renamed
- [ ] **TIER-04**: App accepts only tier ladders that fill downward from Gold — `{Gold}`, `{Silver, Gold}`, or `{Bronze, Silver, Gold}` — and rejects any set omitting Gold or skipping a level
- [ ] **TIER-05**: App requires tier thresholds to strictly increase from Bronze through Gold
- [ ] **TIER-06**: When tiers are configured, the lowest tier's threshold is the achievement's done threshold, and higher tiers are upgrades rather than gates
- [ ] **TIER-07**: User can see the tier reached in the current period on the achievement's card

### Tracking

- [x] **TRACK-01**: User can increment an achievement's counter to log progress
- [x] **TRACK-02**: User can see partial progress on the card, visually distinct from being done
- [x] **TRACK-03**: An achievement becomes done for a period when its count reaches its done threshold
- [x] **TRACK-04**: User can undo an increment, and the done state, tier, and streak all update correctly
- [x] **TRACK-05**: User can only log progress for the current period — earlier periods are read-only
- [x] **TRACK-06**: App tracks a streak of consecutive completed periods for each achievement, counted in that achievement's own period unit
- [x] **TRACK-07**: A missed period resets an achievement's streak to zero, with no grace period
- [x] **TRACK-08**: User can see the best streak ever reached for each achievement
- [ ] **TRACK-09**: App records the highest tier reached in each completed period
- [x] **TRACK-10**: A closed period's recorded result is immutable and unaffected by later edits to the achievement

### Periods

- [x] **PERIOD-01**: Daily periods run from local midnight to local midnight
- [ ] **PERIOD-02**: Weekly periods run Monday through Sunday regardless of device locale
- [ ] **PERIOD-03**: Monthly periods follow the calendar month
- [x] **PERIOD-04**: App detects a period rollover while running in the foreground and refreshes the tabs without needing a restart
- [x] **PERIOD-05**: App correctly reconciles all missed periods when reopened after an extended absence
- [x] **PERIOD-06**: Period boundaries stay correct across daylight-saving transitions, timezone changes, and device clock changes

### Todos

- [ ] **TODO-01**: User can create a one-off todo assigned to today, this week, or this month
- [ ] **TODO-02**: Todos appear in the tab matching their assigned period, visually distinct from achievements
- [ ] **TODO-03**: User can complete a todo with a single tick and undo it
- [ ] **TODO-04**: An unfinished todo moves to the archive when its period ends
- [ ] **TODO-05**: User can restore an archived todo by assigning it to a current day, week, or month
- [ ] **TODO-06**: User can permanently delete a todo

### Navigation

- [ ] **NAV-01**: App presents three tabs — Daily, Weekly, Monthly
- [ ] **NAV-02**: Each tab shows that period's achievements and todos together in one list
- [ ] **NAV-03**: User can reach an archive view listing archived achievements and expired todos, with restore and permanent-delete actions
- [x] **NAV-04**: Empty tabs show a helpful empty state that explains how to add the first item

### Celebration

- [ ] **CELEB-01**: Completing an achievement fires an animation whose intensity scales with the tier reached
- [ ] **CELEB-02**: Crossing into a higher tier within the same period fires the corresponding upgraded animation
- [ ] **CELEB-03**: Clearing an entire tab — every achievement done and every todo ticked — fires a distinct, larger full-tab celebration
- [ ] **CELEB-04**: Each celebration fires exactly once per triggering event and never replays on rotation, recomposition, process death, or app restart
- [ ] **CELEB-05**: Completion is accompanied by haptic feedback that matches the tier reached
- [ ] **CELEB-06**: Animations respect the system reduced-motion setting

### Data

- [x] **DATA-01**: All state persists in a local Room database and the app functions fully offline
- [x] **DATA-02**: App declares no `INTERNET` permission, making the offline guarantee OS-enforced
- [x] **DATA-03**: Room schemas are exported and migrations are tested; destructive migration is never used, so streak history cannot be silently lost
- [x] **DATA-04**: App state survives process death and device restart
- [ ] **DATA-05**: App builds as a release APK and installs directly on the owner's device

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Backfill

- **BACK-01**: User can mark an achievement done for yesterday — *research flagged this as the most likely v1 regret; first candidate for promotion if "did it, forgot to open the app" turns out to bite*
- **BACK-02**: User can fill in earlier days within the current period

### Notifications

- **NOTF-01**: User receives a daily reminder about unfinished items
- **NOTF-02**: User can set a per-achievement reminder time

### Insights

- **STAT-01**: User can see a per-achievement history calendar or heatmap
- **STAT-02**: User can see completion rates and trends across all achievements
- **STAT-03**: User can see a count of Gold periods earned per achievement

### Flexibility

- **FLEX-01**: User can lower a threshold or change an achievement's period, with defined streak consequences
- **FLEX-02**: User can group achievements into categories

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Grace period / streak freeze | A forgiving streak is not a streak; best-streak tracking is the chosen mitigation |
| Fully editable completion history | A streak you can rewrite is not a streak |
| Cloud sync, accounts, sharing, social features | Local-only, single-user by design |
| Play Store release | Target is the owner's own phone; avoids signing ceremony, listing, screenshots, privacy policy, data-safety declaration |
| iOS or cross-platform | Native Kotlin chosen deliberately for animation quality and zero runtime overhead |
| Custom tier names or more than three tiers | Fixed Bronze/Silver/Gold keeps grading legible and the edit UI simple |
| Sub-task checklists inside an achievement | Todos are standalone items, not children of achievements |
| Todos carrying over silently | Expiry to archive with explicit restore prevents stale items accumulating invisibly |
| Hilt or Koin dependency injection | Object graph is too small to justify the build-time cost; manual DI is the researched recommendation |
| Background workers for period rollover | Reconciliation on app open is sufficient and matches the open-the-app-only product model |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| ACH-01 | Phase 1 | Complete |
| ACH-02 | Phase 1 | Complete |
| ACH-03 | Phase 3 | Pending |
| ACH-04 | Phase 3 | Pending |
| ACH-05 | Phase 3 | Pending |
| ACH-06 | Phase 3 | Pending |
| ACH-07 | Phase 3 | Pending |
| ACH-08 | Phase 3 | Pending |
| ACH-09 | Phase 3 | Pending |
| TIER-01 | Phase 1 | Complete |
| TIER-02 | Phase 2 | Pending |
| TIER-03 | Phase 2 | Pending |
| TIER-04 | Phase 2 | Pending |
| TIER-05 | Phase 2 | Pending |
| TIER-06 | Phase 2 | Pending |
| TIER-07 | Phase 2 | Pending |
| TRACK-01 | Phase 1 | Complete |
| TRACK-02 | Phase 1 | Complete |
| TRACK-03 | Phase 1 | Complete |
| TRACK-04 | Phase 1 | Complete |
| TRACK-05 | Phase 1 | Complete |
| TRACK-06 | Phase 1 | Complete |
| TRACK-07 | Phase 1 | Complete |
| TRACK-08 | Phase 1 | Complete |
| TRACK-09 | Phase 2 | Pending |
| TRACK-10 | Phase 1 | Complete |
| PERIOD-01 | Phase 1 | Complete |
| PERIOD-02 | Phase 2 | Pending |
| PERIOD-03 | Phase 2 | Pending |
| PERIOD-04 | Phase 1 | Complete |
| PERIOD-05 | Phase 1 | Complete |
| PERIOD-06 | Phase 1 | Complete |
| TODO-01 | Phase 4 | Pending |
| TODO-02 | Phase 4 | Pending |
| TODO-03 | Phase 4 | Pending |
| TODO-04 | Phase 4 | Pending |
| TODO-05 | Phase 4 | Pending |
| TODO-06 | Phase 4 | Pending |
| NAV-01 | Phase 2 | Pending |
| NAV-02 | Phase 4 | Pending |
| NAV-03 | Phase 4 | Pending |
| NAV-04 | Phase 1 | Complete |
| CELEB-01 | Phase 2 | Pending |
| CELEB-02 | Phase 2 | Pending |
| CELEB-03 | Phase 5 | Pending |
| CELEB-04 | Phase 1 | Pending |
| CELEB-05 | Phase 5 | Pending |
| CELEB-06 | Phase 5 | Pending |
| DATA-01 | Phase 1 | Complete |
| DATA-02 | Phase 1 | Complete |
| DATA-03 | Phase 1 | Complete |
| DATA-04 | Phase 1 | Complete |
| DATA-05 | Phase 1 | Pending |

**Coverage:**

- v1 requirements: 53 total *(corrected from 51 — original count undercounted the ACH/TIER/TRACK/PERIOD/TODO/NAV/CELEB/DATA sections above; verified by counting distinct REQ-IDs)*
- Mapped to phases: 53
- Unmapped: 0 ✓

**By phase:**

| Phase | Requirement count |
|-------|--------------------|
| Phase 1 — Daily Achievements — Core Loop & Foundation | 23 |
| Phase 2 — Full Achievement Model — Periods & Tiers | 12 |
| Phase 3 — Achievement Management — Edit, Archive & Reorder | 7 |
| Phase 4 — Todos & Unified Archive | 8 |
| Phase 5 — Full-Tab Celebration & Motion Polish | 3 |

---
*Requirements defined: 2026-07-27*
*Last updated: 2026-07-27 after roadmap creation*
