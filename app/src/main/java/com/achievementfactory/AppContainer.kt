package com.achievementfactory

import android.content.Context
import androidx.room.Room
import com.achievementfactory.data.AppDatabase
import com.achievementfactory.data.MIGRATION_1_2
import com.achievementfactory.data.achievement.AchievementRepository
import com.achievementfactory.domain.maintenance.ReconcileStreaksUseCase
import com.achievementfactory.domain.streak.StreakCalculator
import com.achievementfactory.period.AppClock
import com.achievementfactory.period.CurrentPeriodProvider
import com.achievementfactory.period.PeriodEngine
import com.achievementfactory.period.PeriodWatcher
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
     * Lives for the process's whole lifetime — backs the streak reconciliation pass [PeriodWatcher]
     * launches on every signal (including the process-lifecycle `onStart` that stands in for "app
     * opened"), which must outlive any single screen's `viewModelScope`. No background-scheduled
     * job of any kind exists in this project; reconciliation on this scope is the entire mechanism
     * (ARCHITECTURE.md "Todo Expiry Without a Background Worker", carried over verbatim to streak
     * reconciliation).
     */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            DATABASE_NAME,
        )
            // Every schema version bump adds its real Migration here — never a destructive
            // fallback (docs/RELEASE.md, "The migration rule").
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    val streakCalculator: StreakCalculator = StreakCalculator(periodEngine)

    val reconcileStreaks: ReconcileStreaksUseCase by lazy {
        ReconcileStreaksUseCase(
            achievementDao = database.achievementDao(),
            progressDao = database.progressDao(),
            streakCalculator = streakCalculator,
        )
    }

    /**
     * The single source of "what day is it" for every read flow (PERIOD-04) — see
     * [CurrentPeriodProvider]'s own doc for why no other read path may call
     * `periodEngine.current` directly.
     */
    val currentPeriodProvider: CurrentPeriodProvider = CurrentPeriodProvider(periodEngine)

    val achievementRepository: AchievementRepository by lazy {
        AchievementRepository(
            database = database,
            achievementDao = database.achievementDao(),
            progressDao = database.progressDao(),
            clock = appClock,
            reconcileStreaks = reconcileStreaks,
            currentPeriodProvider = currentPeriodProvider,
        )
    }

    /**
     * Started from [AchievementFactoryApp.onCreate] — refreshes [currentPeriodProvider] and
     * reruns [reconcileStreaks] on every date/time/timezone broadcast and process resume
     * (PERIOD-04, PERIOD-06). Its `onStart` signal is also the app-open pass (PERIOD-05); nothing
     * else launches `runAll`.
     */
    val periodWatcher: PeriodWatcher by lazy {
        PeriodWatcher(
            context = context.applicationContext,
            currentPeriodProvider = currentPeriodProvider,
            reconcileStreaks = reconcileStreaks,
            applicationScope = applicationScope,
        )
    }
}
