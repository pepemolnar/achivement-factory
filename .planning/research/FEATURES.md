# Feature Research

**Domain:** Personal habit / achievement tracker (Android, local-only, single-user, counter + tier + streak model)
**Researched:** 2026-07-27
**Confidence:** MEDIUM (web-search-based competitor survey, cross-checked across 6+ independent apps and multiple review/analysis sources; no official API docs involved since this is a UX/feature-landscape question, not a library question)

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist in any habit/streak tracker. Missing these makes the app feel broken or incomplete, even for a single-user personal build.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| One-tap / low-friction completion | Every successful tracker (Streaks, Loop, HabitKit, Done) reduces the "log it" action to one tap. Friction is the #1 reason trackers get abandoned — "the best habit tracker is the one that takes less time to use than the habit itself." | LOW | Already in scope: increment counter, tick todo. |
| Counter/quantified habits with visible partial progress | Standard pattern across Done ("Count" habit type), TickTick ("8 glasses a day" tracker with manual/automatic recording), HabitKit ("3x/week, 20x/month"). Users expect to see "1/2" not just done/not-done. | MEDIUM | Already in scope (counter + target, partial progress visible on card). |
| Undo a mistaken log | Universal safety-valve once a counter or checkmark exists; without it, a mis-tap is a permanent, un-fixable event, which is worse for trust than having no history editing at all. | LOW | Already in scope. |
| Current streak count | Present in literally every tracker surveyed (Streaks, Loop, HabitKit, Done, Habitica Dailies, TickTick). It is the core feedback signal of the category. | LOW | Already in scope. |
| Best/longest streak ever | Nearly universal alongside current streak (Streaks' stats screen, Done's "big circle," HabitKit). It's the thing that survives a reset and gives the user something to chase again immediately. | LOW | Already in scope — and this is the single most important mitigation for the "no grace period" decision (see Exclusions Assessment below). |
| Edit / archive / delete a habit | Universal CRUD. Every app surveyed supports renaming, deactivating, and removing habits without losing history. | LOW | Already in scope (archive, restore, permanent delete). |
| Icons and/or colors per item | Near-universal for quick visual scanning once you have more than ~5 items on screen. Streaks (600+ icons, 78 themes), HabitKit (custom colors/icons), Habit Pixel, mhabit, Habitsy all treat this as baseline, not a premium feature. | LOW | **Gap check:** not explicitly listed in PROJECT.md's Active scope. With only 3 tabs and an unbounded number of achievements per tab, icon/color coding is what keeps a Daily tab of 6–10 cards scannable at a glance. Recommend confirming this is covered by "Create/Edit achievement" (e.g., pick a color, optionally an icon) even if not called out as its own bullet. |
| Manual reordering of items within a list | Table stakes even in minimalist trackers — Habit Pixel, mhabit, Habit Tracker Grid, Habitsy, Table Habit all support drag-to-reorder, and it's cheap to build (a `sortOrder` int column + drag handle). Without it, the order becomes creation order forever, which degrades as the list grows or priorities shift. | LOW–MEDIUM | **Gap check:** not explicitly in PROJECT.md Active scope. Flag as a likely-needed addition; low cost, meaningfully improves daily usability once more than a handful of achievements exist in a tab. |
| Fast, low-ceremony first-run / empty state | UX research on habit trackers converges on one rule: the first empty screen is onboarding, and the single-session goal is "get to one created habit and one completed check-in as fast as possible" — no tutorials, no "here's what our app can do" screens. | LOW | Naturally satisfied by a local single-user app with no accounts — just needs an empty-tab state with a clear "add achievement" affordance, no walkthrough. |
| Visible tab/period grouping (daily / weekly / monthly) | TickTick and Habitica both separate habits by cadence; grouping by period is the norm once an app supports more than one frequency. | LOW | Already in scope (three tabs). |

### Differentiators (Competitive Advantage)

These are not expected by default in the category — they're where this app can stand out, and they map directly to the stated Core Value ("the satisfying loop").

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Escalating celebration animation tied to tier reached | Most trackers give a flat checkmark/confetti regardless of how well you did. Scaling the payoff (small tick → full burst) rewards effort beyond the minimum, which none of the surveyed mainstream apps (Streaks, Loop, HabitKit, Done) do — they're binary or simple-streak in their feedback. | HIGH | This *is* the app's stated core value — worth the investment. |
| Bronze/Silver/Gold grading ladder over a single counter | A known but genuinely niche pattern, not table stakes. Found in "Improve: Habit Tracker" (awards a bronze/silver/gold marble per day based on how much was accomplished, not all-or-nothing) and in study-tracker apps like Athenify (bronze/silver/gold thresholds by hours studied). Duolingo's Bronze→Diamond leagues popularized bronze/silver/gold as a legible tier metaphor at massive scale, though that system is social/competitive (weekly cohorts), not a personal grading ladder — it validates the *vocabulary*, not the mechanic. No mainstream single-user tracker (Streaks, Loop, HabitKit, Done, TickTick) layers a 3-tier grading system on top of a counter the way this project does. This is genuine, differentiated territory — done well by small/niche apps, not the market leaders. | MEDIUM | Grading (doesn't gate done-ness) rather than gating is the right call — it avoids Habitica's most-criticized flaw (see Anti-Features) where difficulty/reward tuning makes simple completion feel punishing. |
| Full-tab "clear everything" celebration | No surveyed app has a per-period "you cleared the whole board" moment distinct from per-item completion. This reinforces an inbox-zero feeling that's closer to task-manager satisfaction (Things 3, TickTick's "all done" states) than habit-tracker satisfaction — combining the two is a genuine differentiator. | MEDIUM | Depends on todos expiring cleanly out of the tab (already the chosen design) so stale items can't permanently block it. |
| Achievements and todos merged in one period-scoped view | Most apps that support both recurring habits and one-off tasks keep them in **separate** lists or sections: Habitica has three distinct categories (Habits / Dailies / To-Dos) presented as separate columns; TickTick pairs a task manager and a habit tracker as separate modules within one app. Interleaving both types in a single Daily/Weekly/Monthly feed, sharing the same completion/celebration language, is an opinionated choice not seen in the surveyed apps. | MEDIUM | Genuine differentiator, but carries a real execution risk: achievement cards (counter, tier, streak) and todo cards (single tick, no streak) must be visually distinct enough at a glance that users don't expect streak/tier behavior from a todo. Worth a deliberate visual language decision (e.g., a persistent small "no-streak" affordance on todo cards). |
| Haptic feedback on completion | Increasingly common in polished apps generally, but not universal in the habit-tracker category specifically (most rely on visual/audio only). Cheap to add, reinforces the "feels good" loop. | LOW | Already in scope. |
| A lightweight streak *indicator icon* on the card itself (e.g., a flame/glyph that visibly "lights up" once a streak is active) | Not the same as a stats dashboard — a single glanceable glyph, not a chart. Nearly every surveyed app uses some visual shorthand for "streak alive" (flame icons are near-ubiquitous) beyond just the number, because a bare number requires reading while a glyph requires only glancing. | LOW | Recommend as a cheap addition that reinforces Core Value without violating the "no stats dashboard" exclusion — it's iconography, not analytics. |

### Anti-Features (Commonly Requested, Often Problematic)

Features that look appealing in this category but are known to cause real problems, or that directly conflict with this project's stated boundaries.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|------------------|-------------|
| RPG-style gamification economy (XP, gold, HP loss, leveling, equipment) — Habitica's model | Sounds like it would make tracking more fun/motivating; taps into game-reward psychology. | Habitica's own reviews (3.93/5 average) report the mechanic "slowing down simple task completion," streaks resetting incorrectly, and the app "feeling unusable" for people who just want to check a box. The economy becomes a second thing to manage on top of the habit itself. | Keep the reward purely presentational (the escalating celebration animation) with no persistent currency, inventory, or penalty system to balance. Already the project's direction — confirmed correct by competitor pain points. |
| Social features: friends, leaderboards, sharing, competitive leagues (Duolingo-style) | Proven powerful for engagement at scale (Duolingo's weekly leagues measurably lift completion). | Requires accounts, a backend, and moderation — directly violates this project's local-only, single-user, no-network constraint, and the "competitive" pressure model doesn't fit a personal, private tracker. | None needed — already correctly excluded in PROJECT.md. |
| Full stats dashboard: charts, completion-rate trend lines, heatmap calendars, monthly reports | Feels like it "closes the loop" on data you're already collecting; several competitors (Streaks, Loop, HabitKit, TickTick) all ship this. | Directly named in a 2026 UX critique of the category as a leading cause of "feature creep" — "achievement badges, social feeds, analytics dashboards... exhausting to use, overwhelming to configure, easier to abandon than the habits they're supposed to help you build." Apps that skip this (e.g., "Everyday Habits" — list + calendar + one-tap check-in, nothing else) are cited as the *good* minimalist counter-example. | Current streak + best streak (already in scope) covers the large majority of the motivational value a dashboard provides, at a fraction of the build cost. Confirmed as the right cut (see Exclusions Assessment). |
| Elaborate reminder/notification systems with per-habit schedules, snooze, multiple daily reminders | Nearly universal in commercial trackers, and reminder research shows they meaningfully help follow-through for casual/lapsed users. | The same feature-creep critique explicitly calls out "17 notification settings" as a symptom of bloat; building scheduling, exact-alarm permissions, and Android 13+ POST_NOTIFICATIONS handling is real engineering weight for a single, self-motivated user who already intends to open the app daily. | Rely on the app being a deliberate daily-open habit for its own creator; revisit only if usage data (once "current streak" starts breaking often) shows forgetting-to-open, not forgetting-to-do, is the actual failure mode. |
| Streak freeze / vacation mode / auto-grace period | Increasingly common as a direct response to criticism that all-or-nothing streaks feel punishing and cause people to quit the *app* (not just the habit) after one bad day — HabitBrix, STRIK, Habi, Streak-Free, and others now ship some version of this. | Directly conflicts with the project's stated core rule that "a streak you can rewrite is not a streak," and adds real state-machine complexity (freeze inventory, earn conditions, expiry) for a single motivated user who explicitly wants strictness. | Best-streak-ever (already in scope) is the correct compromise: a miss doesn't erase the *record* of what was achieved, only the live counter — softening the psychological blow without softening the rule. Treat "no grace period" as correct for v1 but watch for the specific quit-inducing pattern research warns about (see Exclusions Assessment). |
| Sub-task checklists inside a habit/achievement (Habitica's To-Do checklists) | Feels useful for breaking down a complex recurring goal. | Blurs the counter/tier/streak model this project has deliberately kept simple; a checklist item and a counter increment are different mental models and mixing them muddies "what does 'done' mean." | Already correctly excluded in PROJECT.md ("todos are standalone items, not children of achievements"). |

## Feature Dependencies

```
Counter + Target (default 1)
    └──requires──> "Done" state (count reaches target)
                       └──requires──> Streak increment on period rollover
                                          └──requires──> Best-streak-ever tracking (enhances, not required)

Tier thresholds (Bronze/Silver/Gold)
    └──requires──> Counter + Target (tiers grade the same counter, don't gate it)

Escalating celebration animation
    └──requires──> Tier thresholds (to pick animation intensity)
    └──requires──> "Done" state (to fire at all)

Full-tab celebration
    └──requires──> Every achievement "done" in the period
    └──requires──> Every todo ticked (or expired out) in the period
    └──requires──> Todo expiry-to-archive (so stale todos can't permanently block it)

Streak reset on miss
    └──conflicts──> Streak freeze / grace period (mutually exclusive designs)

Icon/color per achievement ──enhances──> Fast visual scanning of a tab
Drag-to-reorder ──enhances──> Fast visual scanning of a tab (both are cheap, both compound)

Backfilling past periods ──conflicts──> "Current-period-only" completion rule
Editable completion history ──conflicts──> Streak integrity (a rewritable streak is not a streak)
```

### Dependency Notes

- **Tiers require Counter + Target:** tiers are purely a presentation/grading layer over the same underlying count; they cannot exist without a counter to grade. This means counter/target logic must ship before tier logic in any phased build.
- **Escalating celebration requires Tier thresholds:** the animation-intensity decision needs to know which tier (if any) was reached, so tier evaluation must run before the celebration is triggered, not after.
- **Full-tab celebration requires Todo expiry:** if todos didn't expire out of the tab, a single forgotten todo from days ago would permanently block the full-tab celebration — expiry-to-archive is a hard prerequisite, not a nice-to-have, for this specific differentiator to feel achievable.
- **Streak reset conflicts with grace period / freeze:** these are mutually exclusive product decisions, not features that can coexist. The research shows this is a real, active tension in the category (apps are visibly splitting into "strict streak" vs. "forgiving streak" camps) — worth knowing this is a considered trade-off, not an oversight.
- **Backfilling conflicts with current-period-only completion:** same tension as above — deliberately chosen against for streak-integrity reasons; see Exclusions Assessment for the honest risk analysis.

## Assessment of v1 Exclusions

Per PROJECT.md's Out of Scope, each exclusion assessed against category norms and known failure patterns.

| Exclusion | Verdict | Reasoning |
|-----------|---------|-----------|
| **No notifications/reminders** | **Reasonable cut** | Reminders measurably help *casual or lapsed* users, but this app has exactly one user who is also its builder — someone already motivated enough to write the app is not the target audience reminders exist for. The real risk (forgetting to open the app on a given day) is a genuine failure mode, but it's cheap to observe after shipping (watch how often streaks break from simple non-opening vs. genuine non-doing) and cheap to add later as a single local scheduled notification if it turns out to matter. Correctly deferred, not a design flaw. |
| **No backfilling past periods** | **Likely regret, but the least-bad exclusion for it** — recommend monitoring closely | This is the exclusion most likely to cause real friction. The specific failure case research turned up repeatedly: the user *does* the habit but simply doesn't open the app that day, only to find the streak dead the next morning with no honest way to correct a true record. Unlike backfilling-to-cheat (a legitimate concern in social/competitive apps), a single-owner private app has no one to cheat — the only person who'd "fake" a backfilled entry is the user lying to themselves, which they have no incentive to do. That said, the project's own reasoning ("keeps streaks honest and completion logic simple") is sound from an engineering-simplicity standpoint, and undo + current-period-only already covers the *most common* mistake (fat-fingered taps), just not the *forgot-to-open-the-app-at-all* case. Recommend treating this as the first candidate for a v1.x add if the owner notices streaks breaking from forgetting to open the app rather than forgetting to do the habit. |
| **No stats dashboard / charts / completion-rate trends** | **Reasonable cut — confirmed correct by category research** | Directly validated by the 2026 feature-creep critique of the category: dashboards are named as a primary driver of app abandonment, not retention, in trackers that over-invest in them. Current streak + best streak (already in scope) delivers the bulk of the motivational payoff a dashboard provides. Low regret risk. |
| **No grace period on streaks** | **Reasonable cut, with one required mitigation already in place** | This is the most philosophically debated exclusion in the category right now — UCL research and multiple 2026-era apps (HabitBrix, STRIK, Habi, Streak-Free) exist specifically because all-or-nothing streaks are shown to cause people to quit the *app* after one bad day, not just reset the habit. However: (1) this project already retains best-streak-ever, which is the single most effective mitigation research surfaces — it means a miss doesn't erase the *evidence* of what was achieved, only the live counter; (2) the target user explicitly wants strictness ("a streak you can rewrite is not a streak" is a stated value, not an oversight); (3) for a single self-aware user (vs. a mass-market app trying to retain strangers), the psychological stakes of a "harsh" streak are lower — there's no external audience to feel judged by. Correct for v1, but worth an explicit, deliberate revisit if the owner notices themselves avoiding opening the app after a miss (the exact quit pattern the research describes) rather than shrugging and starting a new streak. |

## MVP Definition

### Launch With (v1)

This mirrors PROJECT.md's Active scope, cross-checked against the table-stakes list above. Two additions flagged as likely-needed even within a tight v1.

- [x] One-tap/low-friction counter increment with visible partial progress — table stakes, already in scope
- [x] Undo a mistaken increment — table stakes, already in scope
- [x] Current streak + best streak, no grace period — table stakes + deliberate core-value choice, already in scope
- [x] Bronze/Silver/Gold tier ladder over the counter (grades, doesn't gate) — differentiator, already in scope
- [x] Escalating celebration animation by tier, plus full-tab celebration — differentiator, already in scope, this is the product
- [x] Todos coexisting with achievements in shared period tabs — differentiator, already in scope
- [x] Archive with restore for both achievements and expired todos — table stakes, already in scope
- [ ] **Icon or color per achievement, chosen at create/edit time** — table stakes; not explicitly called out in PROJECT.md's Active bullets. Recommend confirming it's implicit in "create/edit an achievement," even if minimal (a color swatch is enough; full icon library is not needed for a single user).
- [ ] **Manual reordering of achievements/todos within a tab** — table stakes at LOW cost (sortOrder field + drag handle); recommend adding to v1 scope rather than deferring, since it's cheap and every comparable app treats it as baseline.

### Add After Validation (v1.x)

- [ ] Backfilling a period the owner genuinely completed but forgot to log — trigger: streaks visibly breaking from "forgot to open the app" rather than "didn't do the habit" (see Exclusions Assessment)
- [ ] Small streak "alive" indicator glyph on the card (flame or similar) beyond the plain number — trigger: once the celebration/animation system exists, this is a natural cheap extension of the same visual language
- [ ] Local (non-networked) daily reminder notification, single per app not per habit — trigger: same forgetting-to-open pattern as backfilling, if it recurs

### Future Consideration (v2+)

- [ ] Categories/grouping beyond the three period tabs — defer until the number of achievements per tab is large enough that a flat list plus reordering isn't enough (unlikely for a single owner's personal list, but possible with monthly achievements accumulating)
- [ ] Widgets — defer; the app is explicitly open-the-app-only by design, and a widget mainly exists to reduce app-opens, which cuts against the stated core loop
- [ ] Any stats/history visualization — explicitly deferred per PROJECT.md, revisit only if current+best streak genuinely feels insufficient after real usage

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Counter increment + undo | HIGH | LOW | P1 |
| Current + best streak | HIGH | LOW | P1 |
| Bronze/Silver/Gold tiers | HIGH | MEDIUM | P1 |
| Escalating tier celebration | HIGH | HIGH | P1 |
| Full-tab celebration | HIGH | MEDIUM | P1 |
| Todos in shared period tabs | HIGH | MEDIUM | P1 |
| Archive/restore | MEDIUM | LOW | P1 |
| Icon/color per achievement | MEDIUM | LOW | P1 (recommend adding) |
| Drag-to-reorder | MEDIUM | LOW–MEDIUM | P1 (recommend adding) |
| Streak "alive" indicator glyph | LOW–MEDIUM | LOW | P2 |
| Backfill for genuine forgotten logs | MEDIUM | MEDIUM | P2 |
| Local single daily reminder | LOW–MEDIUM | MEDIUM | P2 |
| Categories/grouping | LOW | MEDIUM | P3 |
| Widgets | LOW | HIGH | P3 |
| Stats dashboard / charts | LOW (per research) | HIGH | Not planned |

**Priority key:**
- P1: Must have for launch
- P2: Should have, add when possible
- P3: Nice to have, future consideration

## Competitor Feature Analysis

| Feature | Streaks | HabitKit | Habitica | Loop Habit Tracker | Done | TickTick | Our Approach |
|---------|---------|----------|----------|---------------------|------|----------|--------------|
| Check-in method | Tap, "5 of 7 days" targets | Tap, grid heatmap | Tap (Habits/Dailies/To-Dos as 3 separate lists) | Tap, flexible schedules (3x/week etc.) | Tap, multi-times-per-day counter | Tap, auto or manual count recording | Tap-to-increment counter, partial progress visible |
| Counter/quantified habits | Limited (day-count targets, not arbitrary counters) | Yes ("3x/week, 20x/month") | Via difficulty levels, not true counters | Yes (complex schedules) | Yes ("Count" habit type) | Yes (explicit counter, e.g. glasses of water) | Yes — every achievement is counter-based, target defaults to 1 |
| Tiered/graded goals | No | No | Difficulty levels (trivial/easy/medium/hard) scale rewards, don't grade a single counter | No (has a "strength" score instead) | No | No | **Bronze/Silver/Gold ladder over the same counter — differentiator** |
| Streak model | Current + best, no freeze | Current streak, custom cadence | Streak bonuses/penalties, HP loss on miss | "Strength" algorithm — soft decay, not hard reset | Current streak, visual circles | Frequency-based check-in rate, not strict streak | Current + best, hard reset on miss, no grace |
| Grace period/freeze | No | No (some competitors do) | Effectively via HP buffer | Yes — soft decay instead of reset | No | Rate-based, inherently forgiving | Explicitly no (deliberate) |
| Backfilling | Yes | Yes (calendar integration) | Yes (To-Dos have no time pressure) | Yes (scroll back through history) | Yes | Yes | Explicitly no (deliberate) |
| Todos alongside habits | No (habits only) | No (habits only) | Yes, but as a separate list/category | No (habits only) | No (habits only) | Yes, but as a separate module | Yes — merged into the same period tab as achievements |
| Stats/charts | Yes (dedicated stats screen) | Yes (grid heatmap as primary UI) | Yes (character sheet, stats) | Yes (detailed graphs) | Yes | Yes (check-in tables) | Explicitly no — current + best streak only |
| Notifications | Yes | Yes (smart reminders) | Yes | Yes (per-habit reminders) | Yes | Yes | Explicitly no for v1 |
| Icons/colors | Yes (600+ icons, 78 themes) | Yes | Avatar-based | Limited | Yes | Yes | Should be included (recommend adding if not implicit) |
| Reordering | Yes | Yes | Limited | Limited | Yes | Yes | Should be included (recommend adding if not implicit) |
| Celebration intensity scaling | No (flat feedback) | No | Flat XP/gold regardless of margin | No | No | No | **Escalating animation by tier — differentiator** |

## Sources

- [Streaks — App Store](https://apps.apple.com/us/app/streaks/id963034692); [Streaks Limits You to 12 Habits — AppPicked](https://www.apppicked.com/en/blog/streaks-habit-tracker-ios-review)
- [Loop Habit Tracker — GitHub (iSoron/uhabits)](https://github.com/iSoron/uhabits); [Loop Habit Tracker — F-Droid](https://f-droid.org/en/packages/org.isoron.uhabits/)
- [HabitKit — official site](https://www.habitkit.app/); [HabitKit — Product Hunt](https://www.producthunt.com/products/habitkit-habit-tracker)
- [Habitica Review 2026 — Calmevo](https://calmevo.com/habitica-review/); [Habitica — GitHub org](https://github.com/Habitica-Review)
- [The Done App Review 2026 — crm.org](https://crm.org/news/done-app-review); [Done — Product Hunt reviews](https://www.producthunt.com/products/done-3/reviews)
- [TickTick Habit help docs](https://help.ticktick.com/articles/7055781785312952320); [TickTick Review 2026 — HabitBox](https://habitbox.app/blog/ticktick-review)
- [Simple Habit Tracker Apps (No Features Overwhelm) — Cohorty](https://www.cohorty.app/blog/simple-habit-tracker-apps-no-features-overwhelm-2025)
- [I Tested 10 Habit Trackers in 30 Days — Medium](https://medium.com/@wardtylerd/i-tested-10-habit-trackers-in-30-days-8-broke-me-the-same-way-9803ea20b228)
- [Allow user to skip days without breaking streak — uhabits GitHub Discussion #88](https://github.com/iSoron/uhabits/discussions/88)
- [Improve: Habit Tracker — App Store](https://apps.apple.com/us/app/improve-habit-tracker/id6757102859)
- [Duolingo Leagues — Deconstructor of Fun](https://duolingo.deconstructoroffun.com/mechanics/leagues); [Duolingo Divisions 2026 — Spliiit](https://www.spliiit.com/en/blog/divisions-duolingo-explication)
- [Habit Pixel — App Store](https://apps.apple.com/us/app/habit-tracker-habit-pixel/id6742632433); [mhabit — GitHub](https://github.com/FriesI23/mhabit)
- [Empty state UI design — Setproduct](https://www.setproduct.com/blog/empty-state-ui-design); [Onboarding UX Patterns: Empty States — UserOnboard](https://www.useronboard.com/onboarding-ux-patterns/empty-states/)
- [Best Habit Tracker App That Survives Bad Days — Goals and Progress](https://goalsandprogress.com/habit-tracker-trigger-action-reward-two-day-rule/)

---
*Feature research for: Personal habit/achievement tracker (Android)*
*Researched: 2026-07-27*
