# Walking Skeleton — Achievement Factory

**Phase:** 1
**Generated:** 2026-08-12

## Capability Proven End-to-End

A daily achievement created on the owner's own phone is written to a local Room database, rendered on the Daily tab, incremented by tapping its card, and still there — with the correct streak — after the process is killed and the app relaunched.

The skeleton itself (plans `01-01` and `01-02`) proves the thinnest version of that: the app builds, installs, opens the Room database, reads a real row through a repository `Flow`, and renders it in Compose. Plan `01-03` supplies the first real write from the UI.

## Architectural Decisions

These are binding for Phases 2–5. Later phases build on them and do not renegotiate them.

| Decision | Choice | Rationale |
|---|---|---|
| Language / UI toolkit | Kotlin 2.3.20 + Jetpack Compose (BOM 2026.04.01), Material3 baseline color scheme | Pinned by CLAUDE.md / STACK.md. Material3 *baseline* (not dynamic color) so the fixed per-achievement accent palette stays predictable (UI-SPEC Design System). |
| Build | AGP 9.2.0, Gradle 9.1.x wrapper, JDK 17+ (Temurin 21), `compileSdk`/`targetSdk` 36, `minSdk` 26 | `minSdk 26` is the floor where `java.time` works with **no** core-library desugaring, so no desugaring config exists anywhere in this project. |
| Dependency declaration | Gradle version catalog at `gradle/libs.versions.toml`, single `app/` module | Every version pinned in one file. Single module: one developer, no build-fan-out or dynamic-delivery need (ARCHITECTURE.md "Pattern 1"). |
| Annotation processing | KSP (`com.google.devtools.ksp`) | KAPT is forbidden project-wide (CLAUDE.md "What NOT to Use"). |
| Data layer | Room **2.8.4** (`androidx.room`, *not* `androidx.room3`), schema export via the `androidx.room` Gradle plugin → `room { schemaDirectory("$projectDir/schemas") }` | Room 3.0.0 is deliberately avoided. Schema JSON is a committed, Gradle-tracked build input. |
| Migration policy | `fallbackToDestructiveMigration()` is **banned**. Every schema version bump ships an explicit `Migration(from, to)` plus a `MigrationTestHelper` test. | There is no cloud backup of streak history; a destructive fallback is the highest-cost possible bug (PITFALLS 7). |
| Date / period math | `java.time` civil types only — `LocalDate`, `YearMonth`, `TemporalAdjusters`. Week identity = the `LocalDate` of that week's Monday. `DayOfWeek.MONDAY` hardcoded. | Epoch-millis math, `WeekFields`, `Locale.getDefault()` week APIs, and ISO week-of-year numbers are all banned (PITFALLS 1–3). All of it lives in `com.achievementfactory.period`, which depends on nothing. |
| Period key format | Zero-padded, lexicographically chronological strings: daily `2026-08-12`, weekly `2026-08-10` (that week's Monday), monthly `2026-08` | Sorts chronologically in SQL with no date parsing in SQLite; a `UNIQUE(achievementId, periodKey)` index gives "one progress row per period" for free. **Supersedes ARCHITECTURE.md's `2026-W31` ISO-week proposal**, which PITFALLS 3 forbids. |
| Streak source of truth | The immutable `progress` log. `currentStreak` / `bestStreak` / `streakCachedForPeriodKey` on `achievements` are a **read cache**, recomputed by a bounded backward walk whenever the cache is behind the current period key. | Self-healing after any length of absence with no catch-up bookkeeping (ARCHITECTURE.md "Streak Computation", PITFALLS 4). |
| Counter writes | Atomic SQL inside `@Transaction` — `UPDATE ... SET count = count + 1`; undo is a compare-and-set `UPDATE ... WHERE count = :expectedCount`. Never Kotlin-side read-modify-write. | Snackbar undo races increments otherwise (PITFALLS 9). |
| Dependency injection | Hand-written `AppContainer`, built once in `AchievementFactoryApp.onCreate()`, reached from ViewModel factories via `APPLICATION_KEY`. | Hilt, Dagger and Koin are all out of scope (CLAUDE.md). The object graph is one database, one repository, four ViewModels. |
| UI state / events | One `StateFlow<XxxUiState>` per screen ViewModel (`stateIn` + `SharingStarted.WhileSubscribed(5_000)`) **plus** a separate non-replaying `Channel<CelebrationEvent>` exposed as `receiveAsFlow()`, collected under `repeatOnLifecycle(STARTED)`. | State and one-shot events are never mixed. This is what makes CELEB-04 true (PITFALLS 8). |
| Navigation | Single `MainActivity`, single `NavHost` (Navigation Compose 2.9.8). Routes: `daily`, `create`, `detail/{achievementId}`. | Phase 2's three tabs become lateral state *inside* the `daily` destination, not three nav routes. |
| Reduced motion | One central helper, `rememberReducedMotion()` in `ui/theme/MotionSpecs.kt`, reading `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`. Every animation resolves its spec through `MotionSpecs`. | Honoured from Phase 1 (D-25) so Phase 2 and 5 animations inherit it instead of retrofitting (PITFALLS 10). |
| Backup | `android:allowBackup="false"` | The product boundary is "everything lives on one device." No app data leaves the device via Android auto-backup. Accepted tradeoff: no cloud restore path, which is precisely why the migration policy above is non-negotiable. |
| Network | No `<uses-permission>` element of any kind in `AndroidManifest.xml` | OS-enforced offline guarantee (DATA-02), not a policy note. |
| Distribution | `./gradlew :app:assembleRelease` → one signed `.apk`, local keystore referenced from a gitignored `keystore.properties`. No `.aab`, no Play App Signing, `isMinifyEnabled = false`. | No store submission is in scope. Minification off removes the R8-strips-Room class of risk for zero cost (no APK size budget on a single-device install). |
| Directory layout | Layer-then-feature under `app/src/main/java/com/achievementfactory/`: `period/`, `data/`, `domain/`, `ui/` | ARCHITECTURE.md "Recommended Project Structure", minus its `di/` package (no Hilt). |
| Test strategy | Pure JUnit4 for `period/` and `domain/`; **Robolectric** for Room DAO/repository tests so they run on the JVM; Turbine for `Flow`/event assertions; MockK where a fake is not simpler. Compose UI tests and `MigrationTestHelper` tests live in `androidTest` and are compile-gated in CI-less runs. | `./gradlew :app:testDebugUnitTest` must be a complete, device-free correctness gate. CONTEXT.md left testing approach to planner discretion. |

## Stack Touched in Phase 1

- [x] Project scaffold — Gradle wrapper, version catalog, AGP/Kotlin/Compose/KSP/Room plugins, JDK + Android SDK provisioning (`01-01` T1)
- [x] Routing — `AppNavHost` with a real `daily` destination, plus `create` and `detail/{achievementId}` (`01-01` T1, `01-03`, `01-06`)
- [x] Database — real write (`AchievementDao.insert`, atomic progress `UPDATE`) and real read (`Flow<List<AchievementWithProgress>>`) (`01-01` T3, `01-02` T1)
- [x] UI — the whole achievement card is an interactive tap target wired through ViewModel → repository → Room (`01-02`, `01-04`)
- [x] Deployment — `./gradlew :app:installDebug` on the owner's device (`01-02`), `./gradlew :app:assembleRelease` signed APK (`01-08`)

## Out of Scope (Deferred to Later Slices)

Phase 1 is daily achievements only. None of the following exist in this phase, and no plan may add them:

- Weekly and monthly periods in the UI — the `period/` engine supports all three period types and is unit-tested for all three, but only `PeriodType.DAILY` is reachable from any screen (Phase 2)
- Three-tab navigation (Phase 2)
- Bronze/Silver/Gold tier configuration and tier-scaled celebration intensity — the tier columns exist in schema v1 and the no-tier path works, which is all TIER-01 requires (Phase 2)
- Editing, archiving, restoring, deleting, drag-to-reorder (Phase 3)
- Todos and the unified archive screen (Phase 4)
- The full-tab celebration, Konfetti, and haptics (Phase 5)
- Streak-reset history UI, welcome-back summary, suggested starter achievements (rejected for v1 — D-26, D-20, D-29)

## Subsequent Slice Plan

Each later phase adds one vertical slice on top of this skeleton without altering the architectural decisions above:

- **Phase 2** — weekly/monthly periods across three tabs (uses `PeriodType` and `PeriodEngine` unchanged) plus the Bronze/Silver/Gold ladder (fills the tier columns already in schema v1) and tier-scaled celebration intensity (adds variants to the existing `CelebrationEvent` / `MotionSpecs` path)
- **Phase 3** — edit, archive, restore, delete, reorder (adds edit controls to the existing read-only `AchievementDetailScreen` and reuses `CreateAchievementScreen`'s layout in edit mode; requires the first real Room migration and the first `MigrationTestHelper` test)
- **Phase 4** — todos and the unified archive (adds `TodoEntity` + `TodoDao` alongside the existing achievement tables; a second migration)
- **Phase 5** — full-tab celebration, tier-matched haptics attached at the `onCelebrationEvent` hook, reduced-motion already inherited from Phase 1

## Known Deviations From Research Documents

Recorded here so later phases do not "fix" them back:

1. **Week period key** — ARCHITECTURE.md proposes `"2026-W31"` (ISO week-of-year). PITFALLS 3 forbids exactly that. The Monday-`LocalDate` form wins; PITFALLS is authoritative on date math.
2. **`di/` package with Hilt** — ARCHITECTURE.md's tree shows a `di/` package and its ViewModel example uses `@HiltViewModel`. CLAUDE.md and STACK.md forbid Hilt. There is no `di/` package; `AppContainer.kt` sits at the package root.
3. **Core-library desugaring** — PITFALLS 1 and its integration-gotchas table advise enabling it "regardless of current minSdk." At `minSdk 26` it is unnecessary, and STACK.md explicitly says not to add it. No desugaring config exists.
