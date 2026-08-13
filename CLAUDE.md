<!-- GSD:project-start source:PROJECT.md -->

## Project

**Achievement Factory**

A personal Android app for tracking recurring achievements and one-off todos across daily, weekly, and monthly cycles. Every achievement is counter-based with an optional Bronze/Silver/Gold grading ladder, keeps a streak of consecutive completed periods, and celebrates completion with animations that escalate from a small tick to a full-tab moment. Everything lives in local storage on one device — no account, no network, no sync. It is built for its owner to use daily on their own phone.

**Core Value:** **The satisfying loop.** Check off → streak ticks up → celebration — and it feels good enough that you actually open the app every day. If tracking is perfectly accurate but the loop is flat, the app has failed.

### Constraints

- **Tech stack**: Native Android — Kotlin, Jetpack Compose, Room — Android-only and local-only, and Compose has the strongest animation story of the options considered
- **Storage**: Device-local only, no network calls, no network permission — a hard product boundary, not just a v1 shortcut
- **Audience**: Single user (the owner) — no multi-user, permissions, or onboarding concerns
- **Distribution**: Direct install on the owner's device — no store submission work in scope
- **Period semantics**: Local-timezone midnight rollover, Monday-start weeks, calendar months

<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->

## Technology Stack

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Kotlin | 2.3.20 | Language | Current stable as of March 2026; next stable (2.4.20) isn't due until Sept 2026, so this is the correct pin today. Ships with the K2 compiler, which every tool below (KSP, Compose compiler, Room) now assumes. |
| Android Gradle Plugin (AGP) | 9.2.0 | Build system | Landed April 2026, has ~3 months of real-world mileage as of this research. AGP 9.3.0 exists (released 2026-07-20) but is one week old at time of writing — too fresh to trust for a project just starting; re-check it after a month of bugfix churn. Requires JDK 17 and Gradle ≥ 9.1.0. |
| Gradle | 9.1.x (wrapper) | Build tool | Minimum required by AGP 9.2.0. Use the latest 9.1.x patch via `gradle wrapper --gradle-version`. |
| compileSdk / targetSdk | 36 (Android 16) | Platform target | API 36 has been stable and broadly exercised since early 2026 (Android 17 / API 37 only shipped 2026-06-16, six weeks before this research — too new to be the default target for a project that has no store deadline forcing an earlier upgrade). Since there's no Play Store gate, bump to `compileSdk = 37` later once you've confirmed your own device is on Android 17 and you want its APIs; nothing about this app needs it on day one. |
| minSdk | 26 (Android 8.0) | Platform floor | 26 is the floor where `java.time` (LocalDate, DayOfWeek, TemporalAdjusters) works natively with **no core-library desugaring**. Because this app installs on exactly one device you control, you can raise `minSdk` further to match that phone's actual OS version and delete even more compatibility surface — there's no install-base tradeoff to weigh. Don't go *below* 26 without adding `coreLibraryDesugaring`; it isn't worth the extra Gradle config for a single-device app. |
| Jetpack Compose BOM | 2026.04.01 | UI toolkit version alignment | Latest **stable** BOM (April '26 release, Compose core 1.11.0). A 2026.07.00 alpha BOM exists (Material3 1.5.0-alpha24) but alpha Material3 is not worth the instability for a project with no reason to chase bleeding-edge Compose features. |
| Room | 2.8.4 | Local persistence | Latest stable Room 2.x (Nov 2025). **Deliberately not recommending Room 3.0.0** despite it being newer — see "What NOT to Use." |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `androidx.room:room-runtime` + `androidx.room:room-ktx` | 2.8.4 | Room runtime + coroutines/Flow extensions | Always — `room-ktx` gives you `suspend` DAO functions and `Flow<T>` query returns out of the box. |
| `androidx.room:room-compiler` via **KSP** | 2.8.4 | Annotation processing for `@Entity`/`@Dao`/`@Database` | Always. Do not use KAPT (see below). |
| `com.google.devtools.ksp` (KSP2) plugin | Aligned to Kotlin 2.3.20 — check `github.com/google/ksp/releases` for the exact patch tag at implementation time (recent tags look like `2.3.10`, `2.3.0`; the two-part `<kotlin>-<ksp>` naming scheme used through 2.2.x appears to be phasing toward bare-version tags aligned 1:1 with Kotlin releases) | Symbol processing for Room | Always — required by Room, and roughly 2x faster than KAPT with no Java stub generation. |
| `androidx.room` Gradle plugin | 2.8.4 (ships alongside room-runtime) | Schema export config | Apply `id("androidx.room") version "2.8.4"` and set `room { schemaDirectory("$projectDir/schemas") }` instead of hand-writing `ksp { arg("room.schemaLocation", ...) }`. Available since Room 2.6, still current, removes a common footgun (schema dir not being a tracked Gradle input, breaking cache correctness). |
| `androidx.navigation:navigation-compose` | 2.9.8 | Screen-to-screen navigation with back stack | Use for the tab container + archive + edit-screen graph (see Navigation section below). |
| `androidx.lifecycle:lifecycle-viewmodel-compose` + `lifecycle-runtime-compose` | 2.10.0 | ViewModel + `collectAsStateWithLifecycle()` | Always — this is how Room `Flow`s become Compose state safely (lifecycle-aware collection, no leaks on rotation/backgrounding). 2.11.0 is in RC as of June 2026; stay on 2.10.0 stable for now. |
| `nl.dionsegijn:konfetti-compose` | 2.0.5 | Confetti/particle burst for the full-tab celebration | See Animation section — this is the one third-party animation dependency worth taking. |
| `kotlinx-coroutines-core` / `kotlinx-coroutines-android` | matches Kotlin 2.3.x line (1.10.x) | Structured concurrency | Always — Room, Navigation, and ViewModel all assume coroutines/Flow. |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| Gradle Version Catalog (`libs.versions.toml`) | Central version pinning | Use from day one even for a solo project — keeps the Compose BOM, Room, KSP, and Kotlin versions consistent and makes future bumps a one-line diff. |
| Android Studio (current stable channel) | IDE | Track whichever stable Android Studio release bundles AGP 9.2.x; don't run a canary/nightly channel for a project you intend to actually finish. |
| R8 (full mode, default in AGP 9.x) | Shrinking/obfuscation for release builds | Optional for a single-device install (no APK size budget to hit), but harmless to leave on; if you do, test the release build on-device before treating a feature as done — R8 can strip Room-generated classes if `@Keep`/consumer rules aren't wired (Room's own consumer ProGuard rules handle this automatically, so this is a non-issue in practice, just don't hand-roll custom rules that fight it). |

## Installation

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|--------------------------|
| Manual DI (a hand-written `AppContainer`) | Koin | If the object graph grows past ~15-20 injectable classes and manual wiring starts feeling like busywork, Koin's DSL is a lightweight step up with almost no build-time cost. |
| Manual DI | Hilt | Never for this project's size — see "What NOT to Use." Would only reconsider if the app grew multi-module with many feature teams, which a single-device personal app will not. |
| Manual DI | Metro (Zac Sweers' new K2 compiler-plugin DI, hit 1.0 stable April 2026) | Genuinely interesting for 2026 — compile-time-safe like Hilt/Dagger but built for K2 with far less boilerplate, and adopted in production at Vinted. Worth a look on your *next* project or if this one balloons; too new/unfamiliar to be the default choice on a personal app where you want zero DI-framework debugging distracting from the actual product. |
| `java.time` | `kotlinx-datetime` (0.8.0, pre-1.0) | Only if this ever becomes a Kotlin Multiplatform project (e.g. you later add a desktop/wear companion). For an Android-only app, `java.time` is already on-device, has full Android Studio/IDE tooling support, and doesn't add a dependency that's still pre-1.0 and reserves the right to break. |
| Navigation Compose 2.9.8 | Manual sealed-class + `when` screen switcher (no navigation library) | Legitimate for the *tab switching* alone (3 tabs really is just "which composable is showing"), but the edit screen and archive both need push/pop-with-back-button semantics and Navigation Compose gives you that plus predictive-back-gesture support for free. Only drop the library entirely if you're willing to hand-roll a back stack. |
| Konfetti for confetti bursts | ParticleEmitter (Canvas-based, physics particle library for Compose) | If you want more granular control over emitter shape/gravity/blend-modes than Konfetti exposes, or want particles that aren't confetti-shaped (e.g. bursts of star/tier icons). It's a younger, less battle-tested library than Konfetti — evaluate it hands-on before committing. |
| Konfetti for confetti bursts | ConfettiKit (Compose *Multiplatform* fork of Konfetti) | Only relevant if you go multiplatform later; for Android-only, it adds a KMP abstraction layer you don't need over the original Konfetti it's forked from. |
| Room 2.8.4 | Room 3.0.0 | Once Room 3.0 has a few months of adoption and bugfix releases behind it (it shipped 2026-07-01, one month before this research). It's the clear future direction (mandatory KSP, suspend-only DAOs, connection-based APIs replacing SupportSQLite) but the ecosystem — tutorials, Stack Overflow answers, third-party integration code — hasn't caught up yet, and the whole package namespace changed (`androidx.room` → `androidx.room3`), which is a real migration, not a patch bump. |
| compileSdk 36 | compileSdk 37 | Once you've verified your own device has taken the Android 17 update and you specifically want an API 37-only capability. Nothing in this app's feature set needs it at day one. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|--------------|
| KAPT for Room's annotation processor | Slower (generates Java stubs for every Kotlin class before processing), officially in maintenance mode, and Room's own docs steer new projects to KSP. There's no reason to pay the KAPT build-time tax on a brand-new project. | KSP (`com.google.devtools.ksp` + `ksp(libs.room.compiler)`) |
| Hilt/Dagger for this app | Hilt earns its keep on multi-module apps with many features and multiple contributors, where compile-time-verified scoped graphs prevent real bugs across teams. For an app with ~5 screens (3 tabs, archive, edit) and one repository-backed data layer, Hilt's annotation processing overhead, generated-code indirection, and the ceremony of `@HiltAndroidApp`/`@AndroidEntryPoint`/`@Module`/`@InstallIn` cost more in build time and cognitive load than they save. You will spend more time debugging Hilt's component graph than you would writing five constructor calls by hand. | A single `AppContainer` class instantiated in your `Application`, holding the `RoomDatabase` and repositories, injected into ViewModels via a simple `ViewModelProvider.Factory` (or `viewModel { }` composition-local pattern). |
| Room 3.0.0 for a project starting today | Released 4 weeks before this research; mandatory suspend-only DAOs, new `androidx.room3` package, and a fundamentally different connection API (`useReaderConnection`/`useWriterConnection` replacing `SupportSQLiteDatabase`). Being this close to the metal on a brand-new major version means you'll be the one finding the edge-case bugs instead of finding answers to them. | Room 2.8.4 — mature, KSP-first already, Flow-native, huge base of working examples. |
| kotlinx-datetime for an Android-only app | Still pre-1.0 (0.8.0) with an explicit "may introduce breaking changes between minor versions" caveat, and its only real advantage — Kotlin Multiplatform portability — is moot when the target is Android-only, per this project's own constraints. | `java.time` (`LocalDate`, `LocalDateTime`, `DayOfWeek`, `TemporalAdjusters`, `YearMonth`) |
| Reflection-based mocking libraries that fight Kotlin `final` classes (plain Mockito without `mockito-kotlin`/inline-mock-maker) | Kotlin classes are `final` by default; vanilla Mockito needs extra config to mock them, and the resulting tests read less idiomatically than Kotlin-first alternatives. | MockK — built for Kotlin, mocks final classes/objects without extra Gradle config. |
| Publishing an `.aab` (Android App Bundle) | App Bundles only exist to let Play Store generate device-specific split APKs at install time; there is no Play Store step in this project, and `.aab` files can't be sideloaded directly. | `./gradlew assembleRelease` → a single signed `.apk`, installed via `adb install` or direct file transfer. |
| Play App Signing / Google-managed signing key | That workflow only exists for Play Store submissions. | A local keystore (`keytool -genkeypair`) referenced from `signingConfigs` in `app/build.gradle.kts`, kept outside version control (`.gitignore` it, or keep the whole repo private since you're not publishing anyway). |
| Requesting `INTERNET` (or any network) permission "just in case" | The product constraint is offline-only with no network permission — omitting `android.permission.INTERNET` from the manifest is not just policy, it's an OS-enforced guarantee: the app *cannot* make network calls even if a bug tried to. Free correctness, take it. | No network permission in `AndroidManifest.xml` at all. |

## Stack Patterns by Variant

- Use a hand-written `AppContainer` (a plain class, not a framework) constructed once in your `Application.onCreate()`, exposing lazily-built repositories backed by the single `RoomDatabase` instance.
- Because there's exactly one build target (your own phone, not multiple product flavors or test doubles across teams), you don't need Hilt's `@TestInstallIn` module-swapping story either — for tests, just construct an in-memory Room database and pass it to the repository/ViewModel constructor directly.
- One `NavHost` at the root with routes for the tab container (itself holding local state for which of the 3 tabs is selected — no need for nested `NavHost`s per tab, a `Scaffold` + `NavigationBar`/bottom tabs with a `when`-switched tab body is simpler and avoids the back-stack-per-tab complexity most apps don't actually need), plus routes for `archive` and `edit/{achievementId}`.
- This gets you predictive back gesture support (stable since Android 13, default-on from Android 14+) for free when navigating into archive or edit, which a hand-rolled screen switcher would have to reimplement.
- **Small tick / no-tier or Bronze completion:** stock Compose — `animateFloatAsState`/`animateColorAsState` for a scale-and-color pulse on the counter, `AnimatedVisibility` for a checkmark fade/scale-in. No library needed.
- **Silver:** layer in `updateTransition` with multiple child animations (scale + rotation + color) driven off a single `MutableTransitionState`, so the escalation between tiers is a matter of transition spec (duration, easing, spring stiffness) rather than new code paths.
- **Gold / full-tab celebration:** this is where a stock `Canvas` + `Animatable`-driven approach becomes real engineering effort (spawning/animating dozens of particles, physics, staggered timing) — bring in **Konfetti** (`konfetti-compose`) rather than hand-rolling a particle system. It's purpose-built for exactly this, Compose-native since v2.0.0, and actively maintained (v2.0.5 is the current release).
- Drive the actual trigger from a single `Animatable`/state value representing "celebration tier reached this event" so the UI layer picks which of the above three treatments to play — keeps the escalation logic in one place instead of scattered per-composable.
- For all discrete completion/tier-up events, use Compose's `LocalHapticFeedback.current.performHapticFeedback(...)`. Compose 1.8+ (included in BOM 2026.04.01) added a much richer `HapticFeedbackType` vocabulary beyond the old `LongPress`/`TextHandleMove` pair — `Confirm` and `ToggleOn` are good fits for a normal tier completion, `GestureThresholdActivate` reads well for a partial-progress increment, and `Reject` is available if you ever add a "can't do that" moment (e.g. blocking backfill attempts).
- For the **full-tab celebration**, the semantic `HapticFeedbackType` constants are single fixed effects, not custom waveforms. If you want a bespoke escalating buzz pattern to match the confetti burst, drop one level down to the platform `Vibrator`/`VibratorManager` service (`context.getSystemService(VibratorManager::class.java)`) and build a custom `VibrationEffect.createWaveform(...)` or `.compose(...)` pattern (Android 12+ has amplitude/frequency composition; Android 8+ has waveform). This is the one spot where Compose's higher-level API isn't enough and you intentionally step down to the platform API.

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|------------------|-------|
| Kotlin 2.3.20 | AGP 9.2.0, KSP (tag aligned to 2.3.20) | AGP 9.0+ fully stabilized Kotlin 2.1.x+ compatibility per Google's own migration guidance; 2.3.20 is well inside that supported range. |
| Compose BOM 2026.04.01 | Kotlin 2.3.20 via the `org.jetbrains.kotlin.plugin.compose` Gradle plugin | Since Compose compiler moved to a Kotlin-version-matched Gradle plugin (post Compose 1.5.4/Kotlin 1.9.20-era split from the old hardcoded compiler-extension-version mapping), you no longer hand-pin a Compose-compiler-to-Kotlin compatibility table — the plugin handles it. Just keep both on current stable. |
| Room 2.8.4 | KSP, `kotlinx-coroutines` | `room-ktx` requires `kotlinx-coroutines-android` on the classpath for `Flow`-returning DAO queries; pull it in transitively via `lifecycle-viewmodel-compose` or add it explicitly. |
| AGP 9.2.0 | Gradle ≥ 9.1.0, JDK 17, SDK Build Tools 36.0.0 | Confirmed from official AGP 9.2.0 release notes region of the docs; don't run an older Gradle wrapper against this AGP version, the build will refuse to configure. |
| Konfetti 2.0.5 | Compose BOM 2026.04.01 | Confirmed Compose-native as of Konfetti 2.0.0+; no known incompatibility with current Compose BOM as of research date. |
| MockK | Kotlin 2.3.x | MockK tracks Kotlin releases closely; verify the latest MockK patch at implementation time rather than trusting a version pinned months earlier. |

## Sources

- `developer.android.com/build/releases/agp-9-2-0-release-notes`, `agp-9-3-0-release-notes`, `gradle-plugin-roadmap` — AGP version, dates, Gradle/JDK requirements (HIGH)
- `kotlinlang.org/docs/whatsnew2320.html`, `blog.jetbrains.com/kotlin/2026/03/kotlin-2-3-20-released` — Kotlin 2.3.20 release date and next-release timeline (HIGH)
- `developer.android.com/blog/posts/whats-new-in-the-jetpack-compose-april-26-release`, `developer.android.com/develop/ui/compose/bom` — Compose BOM 2026.04.01 stable / 2026.07.00 alpha (HIGH)
- `developer.android.com/jetpack/androidx/releases/room`, `.../room3`, `android-developers.googleblog.com/2026/03/room-30-modernizing-room.html` — Room 2.8.4 stable vs Room 3.0.0 (released 2026-07-01) breaking changes (HIGH)
- `github.com/DanielMartinus/Konfetti`, `.../releases` — Konfetti version 2.0.5, Compose support since 2.0.0, active maintenance (HIGH — official repo)
- `github.com/vinceglb/ConfettiKit` — ConfettiKit as a KMP-oriented fork (MEDIUM — README only, no independent verification of release cadence)
- `github.com/Kotlin/kotlinx-datetime/releases` — kotlinx-datetime 0.8.0, pre-1.0 status (HIGH — official repo)
- `developer.android.com/reference/kotlin/androidx/compose/ui/hapticfeedback/*` and multiple ProAndroidDev/Medium writeups on Compose 1.8 `HapticFeedbackType` additions — cross-checked against the official reference package for the type names (HIGH for API existence, MEDIUM for exhaustive list)
- `github.com/cashapp/turbine`, mvnrepository — Turbine 1.2.1 (HIGH)
- `zacsweers.github.io/metro`, `zacsweers.dev/metro-is-stable`, `vinted.engineering/2026/02/12/from-dagger-to-metro` — Metro 1.0 stable April 2026, production adoption (MEDIUM — vendor + one adopter case study, not independently cross-checked further)
- Multiple Medium/ProAndroidDev pieces on Hilt vs Koin vs manual DI for small apps — used only to confirm this is mainstream community consensus, not as the basis for the recommendation itself, which is this document's own reasoning about the project's actual object-graph size (MEDIUM)
- `developer.android.com/training/data-storage/room/migrating-db-versions`, community writeups on `androidx.room` Gradle plugin and `room.schemaLocation` — schema export / migration testing configuration (MEDIUM-HIGH, mixed official + community)
- `developer.android.com/jetpack/androidx/releases/navigation` — Navigation Compose 2.9.8 (HIGH)
- `developer.android.com/jetpack/androidx/releases/lifecycle` and mvnrepository — Lifecycle 2.10.0 stable, 2.11.0-rc01 in progress (MEDIUM — exact version numbers not confirmed on the primary docs page itself, cross-checked against Maven Central listings)

<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->

## Conventions

Established during Phase 1 (daily-achievements-core-loop-foundation). Binding for Phases 2–5
per SKELETON.md — build on these, don't renegotiate them.

**Package layout.** Layer-then-feature under `app/src/main/java/com/achievementfactory/`:
`period/` (civil-date math, depends on nothing else in the project), `data/` (Room entities,
DAOs, repositories, one subpackage per aggregate — `data/achievement/`, `data/progress/`),
`domain/` (`domain/model/` for plain data classes crossing layers, `domain/streak/`,
`domain/maintenance/` for use cases), `ui/` (one subpackage per screen — `ui/daily/`,
`ui/create/`, `ui/detail/`, `ui/celebration/`, `ui/theme/`, `ui/nav/`, plus shared composables
directly under `ui/achievement/`). No `di/` package — see DI below.

**ViewModel and UiState shape.** One `StateFlow<XxxUiState>` per screen, built with
`.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), XxxUiState.Loading)` off a
reactive repository `Flow` — never polled, never manually refreshed. `XxxUiState` is a sealed
interface (`Loading` / `Content` / `Error` / `NotFound`, as the screen needs). Screens expose
plain intent methods (`onIncrement(id)`, `onUndo(token)`), not a dispatched-action pattern. Each
ViewModel's companion object exposes a `Factory: ViewModelProvider.Factory` built with
`viewModelFactory { initializer { ... } }`, reading `AchievementFactoryApp` off
`this[APPLICATION_KEY]` to reach `app.container`. A ViewModel that needs a route argument reads
it via `createSavedStateHandle()` inside that same initializer block.

**State versus events.** One-shot signals — undo snackbars, completion celebrations — travel on
a dedicated `Channel(Channel.BUFFERED)` exposed as `.receiveAsFlow()`, collected by the screen
under `repeatOnLifecycle(STARTED)`. A `Channel` holds no buffered history, so a collector that
subscribes after rotation or process restoration receives nothing — never the last-delivered
event. These events are never folded into a `UiState` and never derived from one; they are
emitted only at the exact transition edge that produced them (e.g. `IncrementOutcome
.justCompleted`). This is what makes CELEB-04 (a celebration plays exactly once) hold, and the
rule every later phase's new one-shot signal must follow.

**Dependency injection.** A single hand-written `AppContainer`, constructed once in
`AchievementFactoryApp.onCreate()`, exposing lazily-built repositories and use cases backed by
one `AppDatabase` instance. No DI framework of any kind. ViewModels reach it through their
`Factory` via `AchievementFactoryApp`, never by constructor injection from a framework.

**Repository and layering rules.** Repositories own transaction boundaries — DAOs expose atomic,
single-purpose queries; a repository composes them inside `@Transaction`-annotated DAO methods
or its own suspend functions, never leaving a multi-step write half-applied. Room entities
(`AchievementEntity`, `ProgressEntity`) never cross into `ui/`; screens and ViewModels see only
`domain/model/` types (`DailyAchievement`, `AchievementDetail`, etc.), mapped at the repository
boundary.

**Period rules.** `java.time` civil types only (`LocalDate`, `YearMonth`) — never epoch-millis
arithmetic for period boundaries. `DayOfWeek.MONDAY` is hardcoded everywhere a week boundary is
needed; week identity is the `LocalDate` of that week's Monday, never an ISO week-of-year
number. `CurrentPeriodProvider` is the single `StateFlow<PeriodKey>` every *read* path
subscribes to — no other read call site resolves "what day is it" independently (grep-gated).
Write paths (`increment`/`undo`) resolve today's key internally at the moment of the write,
rather than trusting a value read earlier that could be stale by the time the write lands.

**Counter mutation.** Every counter change is a single atomic SQL statement inside the DAO —
`UPDATE ... SET count = count + 1 ...` — never a Kotlin-side read followed by a write. Undo is a
compare-and-set `UPDATE ... WHERE count = :expectedCount`, so a stale undo token that no longer
matches the live row is silently a no-op rather than corrupting a newer increment.

**Migration rule.** The destructive-migration-fallback builder options must never appear in this
codebase. Every schema version bump ships a real `Migration(n, n + 1)`, adds it to the
`Room.databaseBuilder(...)` call in `AppContainer`, commits the newly exported schema JSON under
`app/schemas/`, and extends `MigrationTest` to migrate a *populated* prior-version database
rather than a fresh empty one. `SchemaExportTest` fails the build if a version bump's schema
export exists on disk but was never committed to git. See `docs/RELEASE.md` for the full
runbook — this is a standing rule from the first release install onward, not a Phase 1 detail.

**Animation convention.** Every animation duration, spring spec, and stage delay this project
uses lives in `ui/theme/MotionSpecs.kt` — no literal-digit `delay(250)` or inline
`tween(300)` anywhere else. `rememberReducedMotion()` in that same file is the only sanctioned
reader of the system's reduced-motion setting (grep-gated); every animation resolves its spec
through `MotionSpecs.resolve`/`resolveDelay`/`resolvePlacement`, so a new tier or celebration
variant inherits reduced-motion handling automatically instead of needing its own check.

**Testing convention.** Pure JUnit4 for `period/` and `domain/` (no Android dependency). Room
DAO/repository tests run on the JVM under Robolectric rather than requiring a device. Turbine
for asserting `Flow`/`Channel` sequences. MockK for mocking concrete classes (this project has
no repository interface — `AchievementRepository` is mocked directly). Compose UI tests and
`MigrationTestHelper` tests live in `androidTest` and are compile-gated (`assembleDebugAndroidTest`)
in any environment without an attached device/emulator. `./gradlew :app:testDebugUnitTest` is
the complete, device-free correctness gate every plan in this project runs before committing.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->

## Architecture

**Layers, top to bottom.** `ui/` (Compose screens + ViewModels) depends on `domain/` (plain
model types and use cases) and `data/` (repositories) depends on Room's generated DAOs, which
depend on `AppDatabase`. `period/` sits underneath everything — it depends on nothing else in
the project (not even `data/`) and is the one package every other layer is free to import
directly. A ViewModel never touches a DAO or `AppDatabase` directly; it always goes through a
repository. A Compose screen never touches a repository directly; it always goes through its
ViewModel's `StateFlow<UiState>` plus its `Channel`-backed event flow(s).

**Entities.** `AchievementEntity` (the achievement's identity, period type, counter target,
optional Bronze/Silver/Gold thresholds, archived flag, and the streak-cache columns below) and
`ProgressEntity` (one row per achievement per period instance — the append-only log of what
happened in each period, unique on `(achievementId, periodKey)`, `CASCADE`-deleted with its
parent achievement).

**Streaks are a cache, not a source of truth.** `AchievementEntity.currentStreak`/`bestStreak`/
`streakCachedForPeriodKey` exist purely to make rendering fast — the real source of truth is the
immutable `progress` log. Whenever the cache is behind the current period key,
`ReconcileStreaksUseCase` recomputes it with a bounded backward walk through that log. This is
lazy — there is no background worker — reconciliation runs at app open and on every
date/time/timezone signal `PeriodWatcher` observes, so a streak is always self-healing after any
length of absence rather than requiring catch-up bookkeeping.

**Navigation.** A single `MainActivity` with one `NavHost` (Navigation Compose). Routes:
`daily`, `create`, `detail/{achievementId}`. Phase 2's three tabs become lateral state inside the
`daily` destination rather than three additional nav routes — the route graph does not grow with
period-type coverage.
<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->

## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->

## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:

- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->

## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
