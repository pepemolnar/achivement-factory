package com.achievementfactory

import android.app.Application
import kotlinx.coroutines.launch

/**
 * Application entry point. Constructs the single [AppContainer] instance in [onCreate] — this
 * project's entire dependency-injection mechanism (see AppContainer.kt: no DI framework) — and
 * launches the streak reconciliation pass on [AppContainer.applicationScope] so every
 * achievement's cached streak is already correct by the time the Daily tab first renders,
 * however long the app was closed (PERIOD-05).
 */
class AchievementFactoryApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.applicationScope.launch {
            container.reconcileStreaks.runAll()
        }
    }
}
