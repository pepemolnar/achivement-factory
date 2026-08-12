# Phase 1: Daily Achievements — Core Loop & Foundation - Context

**Gathered:** 2026-07-27
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase delivers a working, installable app containing exactly one tab's worth of product: **daily achievements only**. The user can create a daily achievement, tap it to log progress, watch it complete with a real celebration, build and lose a streak, and undo a mistaken tap — all persisted locally and correct across midnight, timezone changes, DST, and long absences.

The narrow surface is deliberate. Underneath it, this phase settles the three foundations everything later depends on: the civil-date period engine, Room migration-safety discipline, and the one-shot celebration-event channel. Nothing in Phases 2–5 should have to retrofit any of them.

**In scope:** ACH-01, ACH-02, TIER-01, TRACK-01 through TRACK-08, TRACK-10, PERIOD-01, PERIOD-04, PERIOD-05, PERIOD-06, NAV-04, CELEB-04, DATA-01 through DATA-05.

**Explicitly NOT in this phase:** weekly/monthly periods, the three-tab navigation, Bronze/Silver/Gold tiers, editing, archiving, deleting, drag-to-reorder, todos, the archive screen, the full-tab celebration, and tier-matched haptics.

</domain>

<decisions>
## Implementation Decisions

### Tick-off interaction

- **D-01:** The **whole card is the tap target** for logging progress. Maximum hit area, fastest loop. No dedicated check button, no swipe-to-complete.
- **D-02:** **One tap = +1.** No stepper, no long-press quick-set. A target-1 achievement completes in one tap; a target-3 takes three. The interaction never changes shape with the target.
- **D-03:** **Long-press opens the detail screen.** This gesture is reserved now specifically so Phase 3's edit screen has a home and no redesign is needed then.
- **D-04:** Undo is a **snackbar shown after each increment**. Long-press is *not* an undo gesture (it opens detail per D-03). Note: this means undo is only available within the snackbar window — accepted deliberately.
- **D-05:** Completed cards **sink into a "Done" section at the bottom** of the list, leaving outstanding items at the top.
- **D-06:** The sink happens **after the completion celebration finishes**, never mid-animation and never while the user is mid-tap. The movement should read as a consequence of the celebration.

### Card anatomy

- **D-07:** Current streak is a **small flame badge in the corner** of the card — present and readable, but the name and progress lead.
- **D-08:** The flame badge is **hidden entirely at streak 0**. A fresh or freshly-broken achievement looks clean, and the flame's appearance at streak 1 is itself a small reward.
- **D-09:** Progress is an **accent-coloured ring filling around the achievement's icon**, one segment/sweep per tap. Not pips, not a progress bar.
- **D-10:** Accent colour is used for **accents only** — the icon, the progress ring, the flame badge. The card body stays neutral. No full-colour cards, no edge stripes.
- **D-11:** On completion the **ring closes and the icon morphs into a checkmark** in the accent colour. This morph is the natural anchor for the completion animation.
- **D-12:** **Best streak lives on the detail screen only**, not on the card.
- **D-13:** Because of D-12, this phase includes a **minimal read-only detail screen** (name, icon, colour, current streak, best streak, today's progress). No edit controls — Phase 3 adds them to this same screen. This is how TRACK-08 is satisfied.

### Creation flow

- **D-14:** Creation is a **full screen reached from a FAB**. Not a bottom sheet. The same screen layout is intended to be reused in edit mode in Phase 3 and to absorb tier configuration in Phase 2 — build it with that in mind.
- **D-15:** Icon selection uses the **system emoji keyboard**. Stored as a short string. No curated icon set to assemble or maintain.
- **D-16:** Accent colour comes from a **fixed palette of roughly 8–12 swatches**, each chosen to remain legible in both light and dark themes. No free colour picker.
- **D-17:** **Name is the only required field.** Icon, colour, and target all have defaults, so an achievement can be created in seconds and refined later.
- **D-18:** When no emoji is set, the ring encircles the **first letter of the achievement's name** in its accent colour. Never an empty ring, never a shared generic glyph.
- **D-19:** The counter target field sits **behind an "advanced" toggle**, defaulting to 1. The common daily habit is once-a-day and shouldn't have to look at it. Phase 2's tier configuration belongs in this same advanced area.
- **D-20:** The empty state is a **minimal line of text** pointing at the FAB. No illustration, no suggested starter achievements — it's a screen the single user will see roughly twice.

### Celebration

- **D-21:** Phase 1 builds **the real single-item celebration, minus tier variation** — ring closing, icon morphing to a check, spring scale, colour sweep. Not a throwaway placeholder. Phase 2 adds only intensity variants per tier; the treatment itself is settled here.
- **D-22:** The **streak flame animates too** as the number ticks up, with something extra on its first appearance at streak 1. The streak is what the user is protecting, so it must visibly react.
- **D-23:** **No confetti in Phase 1.** Konfetti is reserved for the Phase 5 full-tab moment so the escalation stays meaningful.
- **D-24:** **No haptics in Phase 1** — CELEB-05 stays wholly in Phase 5. However, the celebration component must be built with an obvious hook point where the tier-matched haptic pattern will later attach, so Phase 5 is an addition rather than a rework.
- **D-25:** **Reduced-motion is honoured from Phase 1 onward**, not deferred to Phase 5 with CELEB-06. Every animation built in this phase collapses to a near-instant effect when the system setting is on, so later animations inherit the behaviour instead of needing retrofit.

### Absence, rollover, and clock changes

- **D-26:** Returning after missed days is **silent** — the streak simply reads 0 and the flame is gone. No welcome-back summary, no inline "streak lost" marker, no dialog. The user will infer what happened.
- **D-27:** A **forward clock jump is treated as a genuine new day.** The previous day closes with whatever it had; today starts fresh. No monotonic-clock cross-checking, no suspicious-jump detection. Travelling east genuinely does start a new local day, which is the desired behaviour.
- **D-28:** A **backward clock move never reopens a closed period.** Once a day is recorded it is immutable, so setting the clock back cannot earn a second streak credit. This falls directly out of TRACK-10 and requires no tamper detection.
- **D-29:** **No UI for streak-reset history in v1.** Best streak is the only visible memory of what was lost. Note: the per-period completion log exists regardless because streak computation requires it — that is a functional necessity, not an early down-payment on the v2 stats work (STAT-01).

### Claude's Discretion

The user made no call on these; the planner and researcher should decide:

- Overall visual system — Material 3 vs a custom theme, typography, spacing, card elevation, dark/light handling. (Consider running `/gsd-ui-phase 1` for a proper design contract before planning.)
- Exact swatch values for the fixed accent palette (D-16), subject to the both-themes-legible constraint.
- Snackbar duration for undo (D-04).
- Whether the Done section has a header, a count, or is collapsible.
- Package structure, DI wiring shape, ViewModel/UiState decomposition, DAO and repository interfaces.
- Concrete animation durations, easings, and spring parameters.
- Testing approach and coverage depth, subject to the mandatory items in canonical refs below.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project scope and requirements
- `.planning/PROJECT.md` — product definition, core value, locked decisions, out-of-scope boundaries
- `.planning/REQUIREMENTS.md` — the 23 REQ-IDs in this phase, plus what is deliberately deferred
- `.planning/ROADMAP.md` §Phase 1 — goal, requirement mapping, and the five success criteria this phase is verified against

### Architecture and schema — read before designing the data layer
- `.planning/research/ARCHITECTURE.md` — concrete proposed Room schema, period-key representation, the streak-computation approach (log as source of truth, cache for render speed), lazy reconciliation at app open/resume, and the one-shot celebration event pattern
- `.planning/research/SUMMARY.md` — cross-cutting synthesis and phase-ordering rationale

### Risk register — read before writing any date or migration code
- `.planning/research/PITFALLS.md` — the date/period section is the highest-value read in the whole project. Non-negotiable rules it establishes:
  - Civil date types (`LocalDate`, `YearMonth`) exclusively — **never** epoch-millis arithmetic for period boundaries
  - `DayOfWeek.MONDAY` hardcoded — **never** locale-derived `WeekFields`
  - A week is identified by its Monday's `LocalDate` — **never** ISO week-of-year (2026 is a 53-week year)
  - `fallbackToDestructiveMigration()` must never appear in this codebase
  - Undo must use atomic SQL, not read-modify-write, or it races with increments
  - Celebration events on a non-replaying `Channel`/`SharedFlow(replay=0)` collected via `repeatOnLifecycle`, never derived from persisted state

### Stack — read before touching the build files
- `.planning/research/STACK.md` — pinned versions and the "what NOT to use" list. Load-bearing choices for this phase: Room 2.8.4 (**not** 3.0.0), KSP (**not** KAPT), the `androidx.room` Gradle plugin for schema export, manual `AppContainer` DI (**no** Hilt, **no** Koin), `java.time` (**not** kotlinx-datetime), and no `INTERNET` permission in the manifest at all

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

None — this is a greenfield repository. The working tree contains only `.planning/` and `CLAUDE.md`. There is no Gradle project, no source tree, and no codebase map.

### Established Patterns

None yet. **Phase 1 sets every pattern this project will use.** Whatever the planner chooses for package structure, ViewModel/UiState shape, repository interfaces, DI wiring, and testing style becomes the convention Phases 2–5 follow. Choose accordingly, and record the conventions so later phases inherit rather than reinvent them.

### Integration Points

The build itself is an integration point: this phase must scaffold the Gradle project, wire KSP and the Room Gradle plugin, configure schema export to `$projectDir/schemas`, and produce a release APK installable on the owner's device (DATA-05).

</code_context>

<specifics>
## Specific Ideas

- The completion moment should read as a single connected gesture: **ring closes → icon morphs to check → streak flame animates → card sinks**. These are sequenced, not simultaneous, and the sink waits for the rest to finish (D-06).
- The flame appearing for the very first time, at streak 1, should feel like a small unlock rather than just another state change (D-08, D-22).
- The user's instinct throughout is toward a **calm list with concentrated moments of delight** — neutral cards, accents-only colour, hidden zero-state badges, a bare empty state — with the energy spent entirely on the completion animation.

</specifics>

<deferred>
## Deferred Ideas

- **Welcome-back summary after an absence** — considered and rejected for v1 (D-26). If the silent reset ever feels harsh in daily use, the quiet inline "streak lost — best 20" marker is the cheaper of the two rejected options.
- **Suggested starter achievements in the empty state** — rejected as one-use scope (D-20).
- **Long-press stepper for large targets** — rejected (D-02, and long-press is taken by D-03). Worth revisiting if a "read 20 pages" style achievement ever makes tapping absurd; a quick-set control in the detail screen would be the natural home.
- **Per-achievement history / heatmap** — v2 (STAT-01), reaffirmed at D-29.
- **Clock-tampering detection via monotonic time** — explicitly rejected (D-27) as complexity that misfires on legitimate travel.

### Risk carried into Phase 3

**D-05's Done section will collide with ACH-09's drag-to-reorder.** Manual ordering and automatic sinking of completed items are competing sort authorities. Phase 3 must decide how they coexist — most likely by making manual order apply only within the outstanding group, with the Done section always sorted separately. Flagging it now so it is a design decision then rather than a bug.

</deferred>

---

*Phase: 1-Daily Achievements — Core Loop & Foundation*
*Context gathered: 2026-07-27*
