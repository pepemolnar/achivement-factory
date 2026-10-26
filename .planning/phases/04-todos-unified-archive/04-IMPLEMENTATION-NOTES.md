# Phase 4 — Implementation Notes

**Status:** Implemented 2026-10-26 outside the GSD workflow (owner's request), on top of the not-yet-
built Phase 3 checkpoint. Requirements: TODO-01 – TODO-06, NAV-02, NAV-03.

## Decisions (made in implementation; the owner asked to move on without a discuss step)

- **One schema version for Phases 3 and 4.** No build with Phase 3's intermediate schema was ever
  installed, so `MIGRATION_1_2` now also creates `todos` (+ `(periodType, periodKey)` index). The
  phone goes v1 → v2 once. `MigrationOneToTwoTest` checks the table is usable after migrating.
- **Expiry is derived, not written (TODO-04).** A todo stores the period key it is assigned to; it
  is in its tab while that key is current for its type, and an unfinished one whose key is not current
  *is* expired — the Archived query (`done = 0 AND periodKey != current key for its type`) finds it.
  No `archived` column, no maintenance pass, correct after any absence. Finished todos from closed
  periods are kept but shown nowhere.
- **Tick/undo = tap to toggle (TODO-03)**, only while the todo's period is current (a closed period
  can't be rewritten, same as achievements). No celebration for a todo (Phase 5's full-tab moment
  will count them). Long-press → confirm → delete (TODO-06).
- **Visually distinct (TODO-02):** outlined, transparent card with a plain round checkbox — no
  accent ring, icon, counter, medal or flame. Done todos strike through and join the Done section.
- **One list per tab (NAV-02):** outstanding achievements (in their order), then unfinished todos
  (by creation), then the Done section with done achievements and ticked todos. Reorder mode still
  covers achievements only.
- **One creation flow:** an Achievement / Todo switch at the top of the + form (create mode only).
  A todo is a title plus "Due: Today / This week / This month", defaulting to the tab it was opened
  from; icon, colour and Advanced are hidden.
- **Unified archive (NAV-03):** the Phase 3 Archived screen gains an "Unfinished todos" section
  ("Due 25 Oct", "Due week of 19 Oct", "Due October 2026"). Restore asks Today / This week / This
  month (TODO-05) and brings it back unfinished; Delete confirms first.

## Still to do

- First build of Phases 3 + 4 together (gate run), commit `app/schemas/.../2.json`.
- On-device: the v1→v2 migration on real data first (docs/RELEASE.md), then todos across a day
  boundary, archive restore/delete, and everything in Phase 3's list.
