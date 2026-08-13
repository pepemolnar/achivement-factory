package com.achievementfactory

import android.content.Context
import androidx.room.Room
import com.achievementfactory.data.AppDatabase
import com.achievementfactory.data.achievement.AchievementRepository
import com.achievementfactory.domain.maintenance.ReconcileStreaksUseCase
import com.achievementfactory.domain.streak.StreakCalculator
import com.achievementfactory.period.AppClock
import com.achievementfactory.period.PeriodEngine
import com.achievementfactory.period.SystemAppClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private const val DATABASE_NAME = "achievement-factory.db"

/**
 * This project's entire dependency-injection mechanism: a plain, hand-written class built
 * once in [AchievementFactoryApp.onCreate]. No DI framework, no `di/` package — see CLAUDE.md
 * "What NOT to Use" and SKELETON.md for the full rationale.
 */
class AppContainer(context: Context) {

    val appClock: AppClock = SystemAppClock()

    val periodEngine: PeriodEngine = PeriodEngine(appClock)

    /**
     * Lives for the process's whole lifetime — backs the streak reconciliation pass launched
     * from [AchievementFactoryApp.onCreate], which must outlive any single screen's
     * `viewModelScope`. No background-scheduled job of any kind exists in this project; app-open
     * reconciliation on this scope is the entire mechanism (ARCHITECTURE.md "Todo Expiry
     * Without a Background Worker", carried over verbatim to streak reconciliation).
     */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            DATABASE_NAME,
        ).build()
    }

    val streakCalculator: StreakCalculator = StreakCalculator(periodEngine)

    val reconcileStreaks: ReconcileStreaksUseCase by lazy {
        ReconcileStreaksUseCase(
            achievementDao = database.achievementDao(),
            progressDao = database.progressDao(),
            periodEngine = periodEngine,
            streakCalculator = streakCalculator,
        )
    }

    val achievementRepository: AchievementRepository by lazy {
        AchievementRepository(
            achievementDao = database.achievementDao(),
            progressDao = database.progressDao(),
            periodEngine = periodEngine,
            clock = appClock,
            reconcileStreaks = reconcileStreaks,
        )
    }
}
