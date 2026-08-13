package com.achievementfactory

import android.content.Context
import androidx.room.Room
import com.achievementfactory.data.AppDatabase
import com.achievementfactory.data.achievement.AchievementRepository
import com.achievementfactory.period.AppClock
import com.achievementfactory.period.PeriodEngine
import com.achievementfactory.period.SystemAppClock

private const val DATABASE_NAME = "achievement-factory.db"

/**
 * This project's entire dependency-injection mechanism: a plain, hand-written class built
 * once in [AchievementFactoryApp.onCreate]. No DI framework, no `di/` package — see CLAUDE.md
 * "What NOT to Use" and SKELETON.md for the full rationale.
 */
class AppContainer(context: Context) {

    val appClock: AppClock = SystemAppClock()

    val periodEngine: PeriodEngine = PeriodEngine(appClock)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            DATABASE_NAME,
        ).build()
    }

    val achievementRepository: AchievementRepository by lazy {
        AchievementRepository(
            achievementDao = database.achievementDao(),
            progressDao = database.progressDao(),
            periodEngine = periodEngine,
            clock = appClock,
        )
    }
}
