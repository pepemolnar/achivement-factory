# Achievement Factory

## What This Is

A personal Android app for tracking recurring achievements and one-off todos across daily, weekly, and monthly cycles. Every achievement is counter-based with an optional Bronze/Silver/Gold grading ladder, keeps a streak of consecutive completed periods, and celebrates completion with animations that escalate from a small tick to a full-tab moment. Everything lives in local storage on one device — no account, no network, no sync. It is built for its owner to use daily on their own phone.

## Core Value

**The satisfying loop.** Check off → streak ticks up → celebration — and it feels good enough that you actually open the app every day. If tracking is perfectly accurate but the loop is flat, the app has failed.

## Requirements

### Validated

<!-- Shipped and confirmed valuable. -->

(None yet — ship to validate)

### Active

<!-- Current scope. Building toward these. All hypotheses until shipped. -->

**Achievements — lifecycle**

- [ ] Create an achievement with a name and a period (daily / weekly / monthly)
- [ ] Edit an existing achievement
- [ ] Archive an achievement (hidden from tabs, history intact, restorable)
- [ ] Permanently delete an archived achievement

**Achievements — counters and tiers**

- [ ] Every achievement has a counter target, defaulting to 1
- [ ] An achievement is **done** for a period when its count reaches its target
- [ ] Partial progress is visible on the card (e.g. 1/2) without counting as done
- [ ] Optionally attach Bronze/Silver/Gold tiers with user-set thresholds per achievement
- [ ] Tier names are fixed (Bronze, Silver, Gold); only the thresholds are configurable
- [ ] Tier sets must fill downward from Gold — valid: `{Gold}`, `{Silver, Gold}`, `{Bronze, Silver, Gold}`. A lone Bronze, a lone Silver, or any set without Gold is rejected
- [ ] Record the highest tier reached in each period

**Completion and streaks**

- [ ] Increment an achievement's counter to log progress
- [ ] Undo a mistaken increment
- [ ] Completion is only possible for the current period — no backfilling past days
- [ ] Track a streak of consecutive completed periods, per achievement, in that achievement's own period unit
- [ ] A missed period resets the streak to zero — no grace period
- [ ] Track and display the best streak ever reached
- [ ] Periods roll over at local midnight; weeks run Monday–Sunday; months are calendar months

**Todos**

- [ ] Create a one-off todo assigned to today, this week, or this month
- [ ] Todos appear in the tab matching their assigned period, alongside achievements
- [ ] Todos have no counter, no tiers, and no streak — a single tick completes them
- [ ] An unfinished todo expires into the archive when its period ends
- [ ] Restore an archived todo by assigning it to a current day / week / month

**Tabs and navigation**

- [ ] Three tabs: Daily, Weekly, Monthly
- [ ] Each tab shows that period's achievements and todos together
- [ ] An archive view listing archived achievements and expired todos, with restore and permanent-delete actions

**Celebration**

- [ ] Completing an achievement fires an animation whose intensity scales with the tier reached (no tiers or Bronze → modest tick; Gold → full burst)
- [ ] Crossing into a higher tier mid-period fires the corresponding upgraded animation
- [ ] Clearing an entire tab — every achievement done and every todo ticked — fires a distinct, larger full-tab celebration
- [ ] Haptic feedback accompanies completion

**Platform**

- [ ] Native Android app in Kotlin with Jetpack Compose
- [ ] All state persisted in local device storage (Room); the app works fully offline with no network permission
- [ ] Runs as a daily driver on the owner's own device via direct install

### Out of Scope

<!-- Explicit boundaries. Reasoning included to prevent re-adding. -->

- **Notifications and reminders** — v1 is open-the-app-only; scheduling and permissions add real complexity without serving the core loop
- **Backfilling past periods** — only the current period can be completed; keeps streaks honest and the completion logic simple
- **Fully editable completion history** — same reasoning; a streak you can rewrite is not a streak
- **Grace periods on streaks** — one miss resets to zero, deliberately
- **Stats dashboard, charts, completion-rate trends** — current streak and best streak only for v1
- **Play Store release** — target is the owner's own phone; avoids release signing, store listing, screenshots, privacy policy, and data-safety declarations
- **iOS or cross-platform** — native Kotlin chosen deliberately for animation quality and zero runtime overhead
- **Cloud sync, accounts, sharing, social features** — local-only, single-user by design
- **Custom tier names or more than three tiers** — fixed Bronze/Silver/Gold keeps the grading legible and the UI simple
- **Sub-task checklists inside an achievement** — todos are standalone items, not children of achievements
- **Todos carrying over silently** — expiry into the archive with an explicit restore is the chosen behaviour, so stale items never accumulate invisibly

## Context

**Origin.** The owner wants a personal habit and achievement tracker that makes consistency feel rewarding rather than merely recorded. The animation and the streak are not decoration — they are the product.

**Domain model, as settled during questioning:**

- An *achievement* recurs on a period (daily, weekly, monthly) and carries a counter with a target. It is done when the count reaches the target.
- Tiers are a grading ladder layered on top of the same counter. They do not gate done-ness; the target does. An achievement can be graded Gold-only, Silver+Gold, or the full Bronze+Silver+Gold ladder.
- A *todo* is a one-off task filed into a period bucket. It shares the tab with achievements but has no counter, tier, or streak.
- The archive is a single destination for both archived achievements and expired todos, with restore and permanent-delete available from there.

**Open questions for planning:**

- Should the app enforce a relationship between the counter target and the lowest configured tier threshold (e.g. require target ≥ lowest tier, or allow them to be fully independent)? Left independent by default; worth validating during the data-model phase.
- Exact behaviour when an achievement's period or target is edited mid-streak — whether the existing streak is preserved, recomputed, or reset.

## Constraints

- **Tech stack**: Native Android — Kotlin, Jetpack Compose, Room — Android-only and local-only, and Compose has the strongest animation story of the options considered
- **Storage**: Device-local only, no network calls, no network permission — a hard product boundary, not just a v1 shortcut
- **Audience**: Single user (the owner) — no multi-user, permissions, or onboarding concerns
- **Distribution**: Direct install on the owner's device — no store submission work in scope
- **Period semantics**: Local-timezone midnight rollover, Monday-start weeks, calendar months

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Native Kotlin + Compose + Room | Android-only and local-only make cross-platform runtimes pure overhead; Compose gives the best animation APIs for the celebration-driven core value | — Pending |
| Done = counter reaches target (default 1) | Preserves the original "gym twice a week" framing; partial progress is visible but does not complete or protect the streak | — Pending |
| Tiers grade, they do not gate | Keeps done-ness on one clear rule while letting tiers reward going further | — Pending |
| Tier sets fill downward from Gold | A lone Bronze is meaningless; a single-tier achievement should be all-or-nothing at a high bar | — Pending |
| Streak = consecutive completed periods, no grace | A forgiving streak is not a streak; a miss resets to zero | — Pending |
| Current-period completion only, undo allowed | Undo fixes mistakes without letting history be rewritten | — Pending |
| Todos expire to archive, restore is explicit | Nothing is silently lost, and stale todos cannot pile up in the tabs or block the full-tab celebration | — Pending |
| Full-tab celebration requires todos too | Reinforces the inbox-zero feeling; safe because expired todos leave the tab | — Pending |
| Escalating animation scaled by tier | Keeps the big moment special and gives a reason to push past the minimum | — Pending |
| No notifications in v1 | The core loop is opening the app; scheduling work adds scope without serving it | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-07-27 after initialization*
