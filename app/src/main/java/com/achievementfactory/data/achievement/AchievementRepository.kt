package com.achievementfactory.data.achievement

import androidx.room.withTransaction
import com.achievementfactory.data.AppDatabase
import com.achievementfactory.data.progress.ProgressDao
import com.achievementfactory.domain.maintenance.ReconcileStreaksUseCase
import com.achievementfactory.domain.model.AchievementDetail
import com.achievementfactory.domain.model.DailyAchievement
import com.achievementfactory.domain.model.IncrementOutcome
import com.achievementfactory.domain.model.UndoToken
import com.achievementfactory.period.AppClock
import com.achievementfactory.period.CurrentPeriodProvider
import com.achievementfactory.period.PeriodKey
import com.achievementfactory.period.PeriodType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

/**
 * The single read/write entry point between ViewModels and the DAOs (ARCHITECTURE.md
 * "Pattern 1"). [DailyAchievement] is the only type this repository exposes across the `ui/`
 * boundary — [AchievementEntity]/[com.achievementfactory.data.progress.ProgressEntity] never
 * cross it (T-01-09).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AchievementRepository(
    // The repository owns transaction boundaries (CONVENTIONS.md "Repository and layering rules"),
    // so it needs the database itself and not only its DAOs: a multi-step write that spans two
    // DAOs cannot be made atomic from inside either one.
    private val database: AppDatabase,
    private val achievementDao: AchievementDao,
    private val progressDao: ProgressDao,
    private val clock: AppClock,
    private val reconcileStreaks: ReconcileStreaksUseCase,
    private val currentPeriodProvider: CurrentPeriodProvider,
) {

    /**
     * Reactively joins the active daily achievements with the current day's progress rows.
     * Re-subscribes to a fresh `progressDao.observeForPeriod` whenever
     * [CurrentPeriodProvider.currentDailyKey] changes — the mechanism behind PERIOD-04: an app
     * left open across local midnight shows yesterday's completed cards as not-done for the new
     * day with no restart and no navigation. Achievements with no progress row for the current
     * key map to `count = 0, isDone = false`; `isDone` is always read from the progress row's
     * snapshotted `done` column, never recomputed from the achievement's *current*
     * `counterTarget`, so a closed period's result stays immutable even if the achievement is
     * later changed (TRACK-10, D-28).
     */
    fun observeDaily(): Flow<List<DailyAchievement>> =
        currentPeriodProvider.currentDailyKey.flatMapLatest { key ->
            combine(
                achievementDao.observeActive(PeriodType.DAILY.name),
                progressDao.observeForPeriod(key.value),
            ) { achievements, progressRows ->
                val progressByAchievementId = progressRows.associateBy { it.achievementId }
                achievements.map { achievement ->
                    val progress = progressByAchievementId[achievement.id]
                    DailyAchievement(
                        id = achievement.id,
                        name = achievement.name,
                        icon = achievement.icon,
                        accent = AccentSwatch.valueOf(achievement.accentSwatch),
                        target = achievement.counterTarget,
                        count = progress?.count ?: 0,
                        isDone = progress?.done ?: false,
                        currentStreak = achievement.currentStreak,
                        bestStreak = achievement.bestStreak,
                    )
                }
            }
        }

    /**
     * The read-only detail-screen source (TRACK-08, D-13) — the same join shape as
     * [observeDaily] narrowed to one achievement, re-subscribing on the same
     * [CurrentPeriodProvider.currentDailyKey] signal. Emits `null` when [id] does not resolve to
     * a row, which [com.achievementfactory.ui.detail.AchievementDetailViewModel] maps to its
     * `NotFound` state. This is the only read path that ever surfaces [AchievementDetail.bestStreak].
     */
    fun observeDetail(id: Long): Flow<AchievementDetail?> =
        currentPeriodProvider.currentDailyKey.flatMapLatest { key ->
            combine(
                achievementDao.observeById(id),
                progressDao.observeForPeriod(key.value),
            ) { achievement, progressRows ->
                if (achievement == null) return@combine null
                val progress = progressRows.firstOrNull { it.achievementId == achievement.id }
                AchievementDetail(
                    id = achievement.id,
                    name = achievement.name,
                    icon = achievement.icon,
                    accent = AccentSwatch.valueOf(achievement.accentSwatch),
                    target = achievement.counterTarget,
                    todayCount = progress?.count ?: 0,
                    isDone = progress?.done ?: false,
                    currentStreak = achievement.currentStreak,
                    bestStreak = achievement.bestStreak,
                )
            }
        }

    /** Creates a new daily achievement, zeroed streaks and no tiers configured. Returns its id. */
    suspend fun create(name: String, icon: String?, accent: AccentSwatch, target: Int): Long {
        val entity = AchievementEntity(
            name = name,
            periodType = PeriodType.DAILY.name,
            icon = icon,
            accentSwatch = accent.name,
            counterTarget = target,
            bronzeThreshold = null,
            silverThreshold = null,
            goldThreshold = null,
            createdAtEpochDay = clock.epochDay(),
            currentStreak = 0,
            bestStreak = 0,
            streakCachedForPeriodKey = null,
        )
        return achievementDao.insert(entity)
    }

    /**
     * Logs one tap of progress against **today only** — this method resolves today's key itself
     * (see [resolveTodayKeyForWrite]) and never accepts a period key from its caller, which is
     * what makes TRACK-05/TRACK-10 structural rather than a runtime check
     * someone could forget (T-01-14). Row creation, the atomic counter update, and the
     * read-back of the resulting count all happen inside
     * [ProgressDao.incrementAndRead]'s single transaction (T-01-15), so [IncrementOutcome.newCount]
     * always matches exactly what *this* call produced, even under concurrent taps.
     *
     * [IncrementOutcome.justCompleted] is derived from [IncrementOutcome.newCount] alone
     * (`previousCount = newCount - 1`) rather than from a separate "before" read, so there is no
     * second read that could race a concurrent increment and report the wrong transition.
     *
     * [reconcileStreaks] runs (forced — see [ReconcileStreaksUseCase]'s own doc) inside the same
     * transaction as the counter write, so the streak the UI reads on the next `observeDaily`
     * emission is already correct — the tap and the streak tick-up are the same interaction
     * (TRACK-06) and, because they commit together, process death between them cannot leave
     * `streakCachedForPeriodKey` stamped with a value the completion log no longer supports.
     *
     * What [IncrementOutcome.newStreak] guarantees, precisely: it is a value read after this
     * call's own reconcile, inside this call's own transaction. It is *not* guaranteed to be
     * unaffected by a concurrent tap on the same achievement — the second transaction's reconcile
     * recomputes from the same log and both arrive at the same number, so the value is correct,
     * but it is a post-commit read rather than a snapshot owned exclusively by this call.
     *
     * Returns `null` — without touching any row — when [achievementId] no longer resolves to an
     * achievement. A row that was deleted between the frame the card rendered on and the tap
     * landing is an ordinary race on a live-Room-backed list, not a programming error, so it is
     * reported as a value the caller can ignore rather than as a thrown exception that would take
     * the process down from inside a `viewModelScope` coroutine.
     */
    suspend fun increment(achievementId: Long): IncrementOutcome? {
        // Resolved before the transaction opens: refresh() assigns a StateFlow that read flows are
        // subscribed to, and firing that while holding a write transaction would have collectors
        // querying a database they cannot read from yet.
        val todayKey = resolveTodayKeyForWrite()
        return database.withTransaction<IncrementOutcome?> {
            val target = achievementDao.getById(achievementId)?.counterTarget ?: return@withTransaction null
            val newCount = progressDao.incrementAndRead(achievementId, todayKey.value, target, clock.epochDay())
            val isDone = newCount >= target
            val previousCount = newCount - 1
            val justCompleted = isDone && previousCount < target
            reconcileStreaks.runFor(achievementId, todayKey, force = true)
            val newStreak = achievementDao.getById(achievementId)?.currentStreak ?: 0
            IncrementOutcome(
                newCount = newCount,
                isDone = isDone,
                justCompleted = justCompleted,
                newStreak = newStreak,
                undoToken = UndoToken(achievementId, todayKey, newCount),
            )
        }
    }

    /**
     * Reverses the specific increment identified by [token] via a compare-and-set at the
     * database layer (see [ProgressDao.undoIfUnchanged]). Returns `false` — without touching any
     * row — when [token]'s period key is not today's: a token that survived past midnight is
     * refused rather than applied to a new day (T-01-16). This, combined with [increment] never
     * naming any period other than today, is the whole mechanism behind D-28: a backward clock
     * move cannot reopen a day that isn't the one the trusted clock currently reports as today.
     *
     * On a successful reversal, [reconcileStreaks] runs (forced, same as [increment]) inside the
     * same transaction as the compare-and-set, so the counter and the cached streak can never be
     * left disagreeing by a crash between the two.
     */
    suspend fun undo(token: UndoToken): Boolean {
        val todayKey = resolveTodayKeyForWrite()
        if (token.periodKey != todayKey) return false
        return database.withTransaction {
            val target = achievementDao.getById(token.achievementId)?.counterTarget
                ?: return@withTransaction false
            val affected = progressDao.undoIfUnchanged(
                achievementId = token.achievementId,
                periodKey = todayKey.value,
                expectedCount = token.expectedCount,
                target = target,
                epochDay = clock.epochDay(),
            )
            val succeeded = affected > 0
            if (succeeded) reconcileStreaks.runFor(token.achievementId, todayKey, force = true)
            succeeded
        }
    }

    /**
     * Resolves "today" for a write by refreshing [CurrentPeriodProvider] and then reading it,
     * rather than calling `periodEngine.current` independently.
     *
     * [refresh][CurrentPeriodProvider.refresh] recomputes from the live clock, so this still
     * resolves today's key *at the moment of the write* — TRACK-05/TRACK-10 and D-28 are
     * unaffected, and an increment still cannot land anywhere but the current period.
     *
     * What it additionally buys is that the read side agrees. [observeDaily]/[observeDetail] are
     * joined against `currentPeriodProvider.currentDailyKey`, which only moves when something
     * calls `refresh()` — a system date broadcast or a process resume. Between real midnight and
     * that signal arriving, a write resolving the clock independently would key its progress row
     * to the *new* day while the UI was still showing the *old* day's row: the tap would appear to
     * do nothing, the undo token would name a row the user cannot see, and a completion
     * celebration could fire on a card that visibly stayed incomplete. Refreshing first collapses
     * that window — the read flows re-subscribe to the same key this write is about to use.
     *
     * `refresh()` is a [kotlinx.coroutines.flow.MutableStateFlow] assignment, which suppresses an
     * assignment equal to the current value, so on an unchanged day this is free and produces no
     * downstream re-emission.
     */
    private fun resolveTodayKeyForWrite(): PeriodKey {
        currentPeriodProvider.refresh()
        return currentPeriodProvider.currentDailyKey.value
    }
}
