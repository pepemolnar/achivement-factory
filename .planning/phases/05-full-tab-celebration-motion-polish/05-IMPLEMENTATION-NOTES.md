# Phase 5 — Implementation Notes

**Status:** Implemented 2026-10-26 outside the GSD workflow (owner's request), on top of the
not-yet-built Phase 3 and 4 commits. Requirements: CELEB-03, CELEB-05, CELEB-06.

## Decisions

- **Full-tab event (CELEB-03)** is a third one-shot `SharedFlow` on `PeriodViewModel`
  (`fullTabCelebrations`, same zero-replay construction). It is emitted only right after a write
  that just completed something — a `justCompleted` tap or a todo ticked done — when a fresh read
  shows the tab non-empty with every achievement done (Gold for ladders) and every todo ticked. Never
  derived from UI state, so never replayed on rotation, tab switch or restart.
- **Overlay:** `ui/celebration/FullTabCelebration` over the whole tab — two Konfetti bursts from the
  lower corners in the medal colours plus the accent, and an "All clear!" banner that pops in, then
  the overlay fades and removes itself. It starts `FULL_TAB_DELAY_MS` after the event so the last
  card's own celebration is seen first. Konfetti 2.0.5 added to the version catalog (the one
  third-party animation dependency CLAUDE.md sanctions). The overlay takes no touches.
- **Haptics (CELEB-05) use system haptic feedback only**, through Compose's `HapticFeedback`.
  The platform `Vibrator` and custom waveforms were rejected: they require the `VIBRATE`
  permission, and this app declares no permission at all (enforced by `LocalOnlyBoundaryTest`).
  Escalation is by multi-pulse patterns instead: tap = 1 light tick; no ladder/Bronze = confirm;
  Silver = confirm + threshold; Gold = confirm + threshold + heavy; full tab = 4 strong pulses.
  Todos get toggle on/off. Patterns are plain data in `ui/haptics/CelebrationHaptics` (JVM-tested
  for strict escalation) and follow the user's system "touch feedback" setting automatically.
- **Reduced motion (CELEB-06):** the full-tab overlay drops the confetti and the pop under reduced
  motion and shows the banner briefly; its delay and fade resolve through `MotionSpecs` like every
  other spec. Card celebrations already collapsed since Phase 1/2. Haptics are not motion and still
  play.

## Still to do

- First build of Phases 3–5 together (gate run), commit `app/schemas/.../2.json`.
- Konfetti's API was written from memory of 2.0.x without a compiler — the first build may need
  small signature fixes in `FullTabCelebration.kt`.
- On-device pass for everything (owner plans one pass at the end), migration first.
