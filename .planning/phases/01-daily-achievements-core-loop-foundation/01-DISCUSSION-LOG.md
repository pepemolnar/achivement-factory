# Phase 1: Daily Achievements — Core Loop & Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-07-27
**Phase:** 1-Daily Achievements — Core Loop & Foundation
**Areas discussed:** Tick-off interaction, Card anatomy, Creation flow, Phase-1 celebration, Absence & clock changes

---

## Tick-off interaction

### What do you tap to log progress?

| Option | Description | Selected |
|--------|-------------|----------|
| Whole card is the target | Tapping anywhere increments. Biggest hit area, fastest loop; opening detail needs another gesture | ✓ |
| Dedicated button on card | Circular check/+ button; rest of card opens it. Clearer, smaller target, less playful | |
| Swipe to complete | Deliberate and satisfying, less discoverable, conflicts with swipe-to-archive later | |

### For a target of 3, how does the counter go up?

| Option | Description | Selected |
|--------|-------------|----------|
| One tap = +1 | Interaction never changes shape with target size. Simplest | ✓ |
| Tap +1, long-press to jump | Stepper for large targets like "read 20 pages". More UI | |
| Stepper on the card | Explicit, trivial undo, but heavy and bureaucratic for target-1 | |

### How do you undo a mistaken tap?

| Option | Description | Selected |
|--------|-------------|----------|
| Snackbar after each tap | Zero permanent UI cost, fits fast tapping; window closes | ✓ |
| Long-press to decrement | Always available, no time limit, less discoverable | |
| Both | Covers every case, more to build and keep consistent | |

### What happens to a card once it's done?

| Option | Description | Selected |
|--------|-------------|----------|
| Stays put, styled as done | List order never shifts under your thumb | |
| Sinks to a Done section | Satisfying "list empties out"; items move while tapping; fights Phase 3 reordering | ✓ |
| Collapses to a compact row | Keeps remaining work dominant without reordering | |

### Follow-ups

| Question | Options | Selected |
|----------|---------|----------|
| Gesture to open an item | Long-press / chevron button / swipe left | Long-press |
| When the card sinks | After celebration ends / immediately / only on next visit | After celebration ends |

**Notes:** The whole-card tap combined with a sinking Done section was flagged as creating movement under the user's thumb; the "after the celebration ends" timing was chosen specifically to defuse it. Long-press was reserved for opening detail now so Phase 3's edit screen needs no gesture redesign.

---

## Card anatomy

### How prominent is the current streak?

| Option | Description | Selected |
|--------|-------------|----------|
| Hero element | Large numeral, makes missing a day feel costly | |
| Small badge | Compact pill; name and progress lead | ✓ (as a flame badge) |
| Only once earned | Hidden until streak 2-3, feels unlocked | |

### Where does best streak live?

| Option | Description | Selected |
|--------|-------------|----------|
| Detail screen only | Uncluttered list; best streak is a record you look up | ✓ |
| Ghosted next to current | Constant motivation, two numbers per row | |
| Only when you're near it | Silent until it matters | |

### How does partial progress read?

| Option | Description | Selected |
|--------|-------------|----------|
| Filling ring around the icon | Compact, uses chosen colour, fill animation is itself satisfying | ✓ |
| Segmented pips | Readable for small targets, useless for large | |
| Bar plus a 2/3 label | Works at any size, most generic-looking | |

### How much weight does the accent colour carry?

| Option | Description | Selected |
|--------|-------------|----------|
| Accents only | Calm, modern, survives a long list | ✓ |
| Full coloured card | Scannable and playful, loud, harder in dark mode | |
| Coloured edge stripe | Middle ground | |

### Follow-ups

| Question | Options | Selected |
|----------|---------|----------|
| Detail screen shape in Phase 1 | Minimal read-only view / bottom sheet / put best streak on the card instead | Minimal read-only view |
| Flame badge at streak 0 | Hidden / shown greyed | Hidden |
| Done-state look | Ring closes + icon→check / ring closes + card dims / ring closes only | Ring closes, icon swaps to check |

**Notes:** The user's answer was "a small fire like badge in the corner" — the small-badge option, specified as a flame. Choosing detail-screen-only for best streak was surfaced as adding a read-only detail screen to Phase 1 scope; the user accepted that trade explicitly.

---

## Creation flow

### Where does creation happen?

| Option | Description | Selected |
|--------|-------------|----------|
| Full screen from a FAB | Room for all fields; same layout reusable as Phase 3 edit | ✓ |
| Bottom sheet | Quicker and lighter, tight with pickers, reworked in Phase 2 | |
| Inline row at list end | Fastest capture, pushes other fields to a step easily skipped | |

### How do you pick an icon?

| Option | Description | Selected |
|--------|-------------|----------|
| System emoji keyboard | Everything available, nothing to curate, stored as a string | ✓ |
| Curated icon grid | Consistent and tintable, limited and more work | |
| Emoji from a shortlist | Faster for common habits | |

### How do you pick the colour?

| Option | Description | Selected |
|--------|-------------|----------|
| Fixed palette of 8-12 | Guaranteed legible in both themes, fastest | ✓ |
| Full colour picker | Freedom, risk of unreadable choices | |
| Auto-assigned, changeable | Fewest decisions, still controllable | |

### How much is required to save?

| Option | Description | Selected |
|--------|-------------|----------|
| Name only, rest defaulted | Save in three seconds, refine later | ✓ |
| Name and icon required | Every card scannable from day one | |
| Everything explicit | Most intentional, most friction | |

### Follow-ups

| Question | Options | Selected |
|----------|---------|----------|
| Card with no emoji | First letter of name / generic placeholder / just the ring | First letter of the name |
| Target field visibility | Always visible defaulting to 1 / behind an advanced toggle | Behind an advanced toggle |
| Empty state | Illustration + prompt / minimal line of text / suggested starters | Minimal line of text |

**Notes:** The advanced-toggle choice for the target field also gives Phase 2's tier configuration an obvious home.

---

## Phase-1 celebration

### How far should the single-item celebration go?

| Option | Description | Selected |
|--------|-------------|----------|
| The real thing, minus tiers | Polished moment now; Phase 2 adds only intensity variants | ✓ |
| Deliberate placeholder | Proves plumbing, less rework, flat to live with | |
| Real thing plus confetti | Maximum payoff, spends the full-tab moment early | |

### Celebrate the streak too?

| Option | Description | Selected |
|--------|-------------|----------|
| Streak animates too | The streak is what's being protected; making it react drives the loop | ✓ |
| Completion only | Cleaner, more focused | |

### Haptics in Phase 1?

| Option | Description | Selected |
|--------|-------------|----------|
| Basic haptic now | Nearly free; completion feels dead without it | |
| Wait for Phase 5 | Keeps CELEB-05 in one place, flatter Phase 1 | ✓ |

### Reduced motion from Phase 1?

| Option | Description | Selected |
|--------|-------------|----------|
| Honour it from the start | Later animations inherit it rather than being retrofitted | ✓ |
| Leave it to Phase 5 | One phase owns it, everything earlier needs revisiting | |

**Notes:** Building the real celebration while deferring haptics was noted as a mild tension — mitigated in CONTEXT.md (D-24) by requiring an explicit haptic hook point in the celebration component so Phase 5 is an addition rather than a rework.

---

## Absence & clock changes

*Additional area surfaced by Claude after the four selected areas completed; user chose to explore rather than defer.*

### Returning after four missed days

| Option | Description | Selected |
|--------|-------------|----------|
| Silent — just the new state | Honest and undramatic, nothing extra to build | ✓ |
| Quiet inline marker | Acknowledges the loss without interrupting | |
| Welcome-back summary | Most informative, seen at your lowest moments | |

### Clock jumps forward

| Option | Description | Selected |
|--------|-------------|----------|
| Treat it as a real new day | Simple, predictable, correct for genuine travel | ✓ |
| Detect and flag suspicious jumps | Tamper-resistant, complex, misfires on legitimate travel | |

### Clock moves backward

| Option | Description | Selected |
|--------|-------------|----------|
| Closed periods stay closed | Falls out of TRACK-10, closes the replay exploit | ✓ |
| Follow the clock wherever it goes | Simplest rule, free way to redo a day | |

### Visible streak-reset history?

| Option | Description | Selected |
|--------|-------------|----------|
| No — not in v1 | Best streak is the only memory; keeps scope honest | ✓ |
| Store it now, show it later | Schema ready for STAT-01 without a migration | |

**Notes:** Recorded in CONTEXT.md (D-29) with a clarification — the per-period completion log exists regardless because streak computation requires it, so "no history in v1" is a UI decision, not a storage one.

---

## Claude's Discretion

The user deferred nothing explicitly, but made no call on the following, which CONTEXT.md hands to the planner: overall visual system and theming, exact palette swatch values, snackbar duration, Done-section header/collapsibility, package structure and DI wiring shape, ViewModel and UiState decomposition, animation durations and spring parameters, and testing depth.

## Deferred Ideas

- Welcome-back summary after an absence — rejected for v1; the quiet inline marker is the cheaper fallback if silence feels harsh
- Suggested starter achievements in the empty state — rejected as one-use scope
- Long-press stepper for large targets — rejected; long-press is taken by open-detail
- Per-achievement history / heatmap — remains v2 (STAT-01)
- Clock-tampering detection via monotonic time — rejected as complexity that misfires on legitimate travel
- **Risk flagged for Phase 3:** the Done section (D-05) and drag-to-reorder (ACH-09) are competing sort authorities and must be reconciled when Phase 3 is planned
