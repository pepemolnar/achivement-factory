package com.achievementfactory

import android.app.Application

/**
 * Application entry point. Constructs the single [AppContainer] instance in [onCreate] — this
 * project's entire dependency-injection mechanism (see AppContainer.kt: no DI framework) — and
 * starts [AppContainer.periodWatcher] so date/time/timezone changes and process resume are
 * noticed for as long as the process lives (PERIOD-04, PERIOD-06).
 *
 * The app-open streak reconciliation pass (PERIOD-05) is *not* launched from here. Starting the
 * watcher registers a `ProcessLifecycleOwner` observer whose `onStart` already runs
 * `refresh()` + `runAll` the moment the first `Activity` starts, which is precisely the
 * "app opened" signal PERIOD-05 asks for. Launching a second pass here as well meant every cold
 * start ran two full reconciliations concurrently on the multi-threaded `Dispatchers.Default`
 * over the same rows: correct, because `runFor` is idempotent, but every stale achievement was
 * read and rewritten twice and the two passes could interleave their `staleAchievementIds`
 * queries against each other's writes. If a pre-first-Activity pass is ever genuinely wanted,
 * serialize the two with a `Mutex` in `PeriodWatcher.onSignal` rather than reintroducing an
 * unguarded second launch here.
 */
class AchievementFactoryApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.periodWatcher.start()
    }
}
