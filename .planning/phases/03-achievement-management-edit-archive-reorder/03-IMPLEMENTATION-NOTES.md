# Phase 3 — Implementation Notes

**Status:** Implemented 2026-10-26 outside the GSD workflow (owner's request). Requirements:
ACH-03 – ACH-09.

## Owner decisions (asked 2026-10-26)

- Edit and Archive start from the **detail screen** (long-press a card → Edit / Archive in the top
  bar). Tapping a card stays +1; nothing new on the cards.
- A simple **Archived screen** now (top-bar ⋮ → Archived) with Restore and Delete forever. Phase 4
  extends this same screen with expired todos.
- **Reorder mode** (top-bar ⋮ → Reorder): drag handles on simplified rows, taps don't count, Done or
  Back to leave.

## Decisions made in implementation

- **Schema v2, first real migration.** `achievements.sortOrder INTEGER NOT NULL DEFAULT 0`
  (`@ColumnInfo(defaultValue = "0")` to match). `MIGRATION_1_2` seeds each row's position from
  creation order within its period type, so nothing on the phone moves. Wired into `AppContainer`.
  Tested on the JVM (`MigrationOneToTwoTest`: a hand-built v1 file from the exact `1.json` SQL,
  populated, opened through Room — which validates the result) and in `MigrationTest` via
  `runMigrationsAndValidate`. `app/schemas/.../2.json` must be committed (SchemaExportTest).
- **"Never easier" is one pure rule**, `domain/edit/ThresholdChangeRule`, used by the form (to explain)
  and by `AchievementRepository.update` inside its transaction (to enforce). Targets and medals may
  only go up; medals and ladders can't be removed; a medal can be added only if the streak threshold
  doesn't drop (i.e. not below the current lowest); a plain achievement may gain a ladder whose lowest
  medal is ≥ the old target. Period type is not editable at all (not even a parameter).
- **New thresholds apply to the open period.** Raising them re-snapshots the current period's
  `done`/`tierReached` from its existing count and reconciles the streak in the same transaction —
  raising today's target past today's count makes today "not done yet". Closed periods never change.
- **Archive doesn't pause a streak.** Archive/restore clear the streak cache; restore reconciles in
  the same transaction, so a restored achievement shows the truth (missed periods broke it). Best
  streak is kept. Restore appends to the end of the tab.
- **Delete is archived-only at the database level** (`DELETE … WHERE archived = 1`), deletes the
  progress log explicitly as well as via CASCADE, and the UI always confirms.
- **Reorder** is hand-rolled over `LazyListState.layoutInfo` (no new dependency); the order is
  committed once on drop. New achievements append to the end of their tab.
- Archiving from the detail screen closes it only after the write lands (one-shot `closeRequests`
  SharedFlow, same convention as other events), so leaving can't cancel the archive.

## Still to do

- Gate run (expect `SchemaExportTest` to fail once until `2.json` is staged), then commit.
- On-device: edit (incl. refused lowering), archive/restore/delete, reorder persistence across
  restarts, and **the v1→v2 migration on the real phone's data** — `connectedDebugAndroidTest` per
  docs/RELEASE.md, or at least install over the existing build and check nothing moved.
