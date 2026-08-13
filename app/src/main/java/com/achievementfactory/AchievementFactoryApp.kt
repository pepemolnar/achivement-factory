package com.achievementfactory

import android.app.Application

/**
 * Application entry point. Constructs the single [AppContainer] instance in [onCreate] — this
 * project's entire dependency-injection mechanism (see AppContainer.kt: no DI framework).
 */
class AchievementFactoryApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
