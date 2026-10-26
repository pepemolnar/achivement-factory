package com.achievementfactory.data.achievement

import androidx.room.withTransaction
import com.achievementfactory.data.AppDatabase
import com.achievementfactory.data.progress.ProgressDao
import com.achievementfactory.data.progress.ProgressEntity
import com.achievementfactory.domain.maintenance.ReconcileStreaksUseCase
import com.achievementfactory.domain.model.AchievementDetail
import com.achievementfactory.domain.model.IncrementOutcome
import com.achievementfactory.domain.model.TrackedAchievement
import com.achievementfactory.domain.model.UndoToken
import com.achievementfactory.domain.tier.Tier
import com.achievementfactory.domain.tier.TierLadder
import com.achievementfactory.period.AppClock
import com.achievementfactory.period.CurrentPeriodProvider
import com.achievementfactory.period.PeriodType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The single read/write entry point between ViewModels and the DAOs (ARCHITECTURE.md
 * "Pattern 1"). [TrackedAchievement] and [AchievementDetail] are the only types this repository
 * exposes across the `ui/` boundary — [AchievementEntity]/[ProgressEntity] never cross it
 * (T-01-09).
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
     * Reactively joins the active achievements of [periodType] with the current period's
     * progress rows. Re-subscribes to a fresh `progressDao.observeForPeriod` whenever
     * [CurrentPeriodProvider.current] for [periodType] changes — the mechanism behind PERIOD-04:
     * an app left open across local midnight (or across Sunday→Monday, or a month boundary) shows
     * the previous period's completed cards as not-done for the new one with no restart and no
     * navigation. Achievements with no progress row for the current key map to
     * `count = 0, isDone = false, tierReached = null`; `isDone` and `tierReached` are always read
     * from the progress row's snapshotted columns, never recomputed from the achievement's
     * *current* target/thresholds, so a closed period's result stays immutable (TRACK-10, D-28).
     *
     * `observeForPeriod` matches on the key string alone, and a weekly key (that week's Monday)
     * is textually identical to the daily key for that Monday. That is harmless here: rows are
     * joined by achievement id against achievements of [periodType] only, so a daily
     * achievement's row can never be attached to a weekly card.
     */
    fun observePeriod(periodType: PeriodType): Flow<List<TrackedAchievement>> =
        currentPeriodProvider.current(periodType).flatMapLatest { key ->
            combine(
                achievementDao.observeActive(periodType.name),
                progressDao.observeForPeriod(key.value),
            ) { achievements, progressRows ->
                val progressByAchievementId = progressRows.associateBy { it.achievementId }
                achievements.map { achievement ->
                    achievement.toTracked(periodType, progressByAchievementId[achievement.id])
                }
            }
        }

    /**
     * The read-only detail-screen source (TRACK-08, D-13), keyed to the current period of the
     * achievement's *own* period type — today for a daily achievement, this week for a weekly one.
     * Emits `null` when [id] does not resolve to a row, which
     * [com.achievementfactory.ui.detail.AchievementDetailViewModel] maps to its `NotFound` state.
     * This is the only read path that ever surfaces [AchievementDetail.bestStreak].
     */
    fun observeDetail(id: Long): Flow<AchievementDetail?> =
        achievementDao.observeById(id).flatMapLatest { achievement ->
            if (achievement == null) return@flatMapLatest flowOf<AchievementDetail?>(null)
            val periodType = periodTypeOf(achievement) ?: PeriodType.DAILY
            currentPeriodProvider.current(periodType).flatMapLatest { key ->
                progressDao.observeFor(achievement.id, key.value).map { progress ->
                    achievement.toDetail(periodType, progress)
                }
            }
        }

    /**
     * Creates a new achievement with zeroed streaks. Returns its id.
     *
     * When [tiers] is given, its [TierLadder.streakThreshold] — the lowest configured tier — is what
     * gets stored as `counterTarget`, and [target] is ignored. For a laddered achievement the
     * progress row's `done` column therefore means "this period counts toward the streak", which
     * keeps the `done` SQL in [ProgressDao] and the streak walk unchanged. Whether the card is
     * *done* (Gold) is decided on read, from the snapshotted `tierReached` (TIER-06).
     */
    suspend fun create(
        name: String,
        icon: String?,
        accent: AccentSwatch,
        target: Int,
        periodType: PeriodType = PeriodType.DAILY,
        tiers: TierLadder? = null,
    ): Long {
        val entity = AchievementEntity(
            name = name,
            periodType = periodType.name,
            icon = icon,
            accentSwatch = accent.name,
            counterTarget = tiers?.streakThreshold ?: target,
            bronzeThreshold = tiers?.bronze,
            silverThreshold = tiers?.silver,
            goldThreshold = tiers?.gold,
            createdAtEpochDay = clock.epochDay(),
            currentStreak = 0,
            bestStreak = 0,
            streakCachedForPeriodKey = null,
        )
        return achievementDao.insert(entity)
    }

    /**
     * Logs one tap of progress against **the current period only** — this method resolves the
     * achievement's current period key itself (see [refreshForWrite]) and never accepts a period
     * key from its caller, which is what makes TRACK-05/TRACK-10 structural rather than a runtime
     * check someone could forget (T-01-14). Row creation, the atomic counter update (including the
     * `tierReached` snapshot), and the read-back of the resulting count all happen inside
     * [ProgressDao.incrementAndRead]'s single transaction (T-01-15), so
     * [IncrementOutcome.newCount] always matches exactly what *this* call produced, even under
     * concurrent taps.
     *
     * [IncrementOutcome.justCompleted], [IncrementOutcome.tierReached] and
     * [IncrementOutcome.tierUpgraded] are all derived from [IncrementOutcome.newCount] alone
     * (`previousCount = newCount - 1`) rather than from a separate "before" read, so there is no
     * second read that could race a concurrent increment and report the wrong transition.
     *
     * The thresholds handed to the SQL come from [TierLadder.fromStoredColumns], not the raw
     * columns, so a stored combination that is not a valid ladder snapshots no tier at all rather
     * than a nonsensical one — and the done threshold is the ladder's, falling back to
     * `counterTarget` only when there is no ladder.
     *
     * [reconcileStreaks] runs (forced — see [ReconcileStreaksUseCase]'s own doc) for the
     * achievement's own period type inside the same transaction as the counter write, so the
     * streak the UI reads on the next emission is already correct — the tap and the streak
     * tick-up are the same interaction (TRACK-06) and commit together.
     *
     * Returns `null` — without touching any row — when [achievementId] no longer resolves to an
     * achievement (an ordinary race on a live-Room-backed list, not a programming error) or when
     * its stored period type is unrecognized.
     */
    suspend fun increment(achievementId: Long): IncrementOutcome? {
        // Resolved before the transaction opens: refresh() assigns StateFlows that read flows are
        // subscribed to, and firing that while holding a write transaction would have collectors
        // querying a database they cannot read from yet.
        refreshForWrite()
        return database.withTransaction<IncrementOutcome?> {
            val achievement = achievementDao.getById(achievementId) ?: return@withTransaction null
            val periodType = periodTypeOf(achievement) ?: return@withTransaction null
            val currentKey = currentPeriodProvider.current(periodType).value
            val ladder = achievement.ladder()
            val streakTarget = ladder?.streakThreshold ?: achievement.counterTarget
            val completeTarget = ladder?.completeThreshold ?: achievement.counterTarget

            val newCount = progressDao.incrementAndRead(
                achievementId = achievementId,
                periodKey = currentKey.value,
                target = streakTarget,
                bronze = ladder?.bronze,
                silver = ladder?.silver,
                gold = ladder?.gold,
                epochDay = clock.epochDay(),
            )
            val previousCount = newCount - 1
            val isDone = newCount >= completeTarget
            val justCompleted = isDone && previousCount < completeTarget
            val streakSecured = newCount >= streakTarget && previousCount < streakTarget
            val previousTier = ladder?.tierFor(previousCount)
            val newTier = ladder?.tierFor(newCount)
            val tierUpgraded = !justCompleted && newTier != null && newTier != previousTier

            reconcileStreaks.runFor(achievementId, currentKey, force = true, keyPeriodType = periodType)
            val newStreak = achievementDao.getById(achievementId)?.currentStreak ?: 0
            IncrementOutcome(
                newCount = newCount,
                isDone = isDone,
                justCompleted = justCompleted,
                newStreak = newStreak,
                undoToken = UndoToken(achievementId, currentKey, newCount),
                tierReached = newTier,
                tierUpgraded = tierUpgraded,
                streakSecured = streakSecured,
            )
        }
    }

    /**
     * Reverses the specific increment identified by [token] via a compare-and-set at the
     * database layer (see [ProgressDao.undoIfUnchanged]), re-snapshotting `tierReached` for the
     * lowered count in the same statement. Returns `false` — without touching any row — when
     * [token]'s period key is not the achievement's *current* period key: a token that survived
     * past midnight (or past the end of the week/month) is refused rather than applied to a new
     * period (T-01-16). This, combined with [increment] never naming any period other than the
     * current one, is the whole mechanism behind D-28.
     *
     * On a successful reversal, [reconcileStreaks] runs (forced, same as [increment]) inside the
     * same transaction as the compare-and-set.
     */
    suspend fun undo(token: UndoToken): Boolean {
        refreshForWrite()
        return database.withTransaction {
            val achievement = achievementDao.getById(token.achievementId) ?: return@withTransaction false
            val periodType = periodTypeOf(achievement) ?: return@withTransaction false
            val currentKey = currentPeriodProvider.current(periodType).value
            if (token.periodKey != currentKey) return@withTransaction false

            val ladder = achievement.ladder()
            val affected = progressDao.undoIfUnchanged(
                achievementId = token.achievementId,
                periodKey = currentKey.value,
                expectedCount = token.expectedCount,
                target = ladder?.streakThreshold ?: achievement.counterTarget,
                bronze = ladder?.bronze,
                silver = ladder?.silver,
                gold = ladder?.gold,
                epochDay = clock.epochDay(),
            )
            val succeeded = affected > 0
            if (succeeded) {
                reconcileStreaks.runFor(token.achievementId, currentKey, force = true, keyPeriodType = periodType)
            }
            succeeded
        }
    }

    /**
     * Refreshes [CurrentPeriodProvider] before a write, rather than resolving the clock
     * independently. [refresh][CurrentPeriodProvider.refresh] recomputes every period's key from
     * the live clock, so a write still lands on the current period *at the moment of the write* —
     * TRACK-05/TRACK-10 and D-28 are unaffected.
     *
     * What it additionally buys is that the read side agrees. The read flows are joined against
     * the provider's keys, which only move when something calls `refresh()` — a system date
     * broadcast or a process resume. Between real midnight and that signal arriving, a write
     * resolving the clock independently would key its progress row to the *new* period while the
     * UI was still showing the *old* period's row: the tap would appear to do nothing, the undo
     * token would name a row the user cannot see, and a celebration could fire on a card that
     * visibly stayed incomplete. Refreshing first collapses that window.
     *
     * `refresh()` assigns [kotlinx.coroutines.flow.MutableStateFlow]s, which suppress an
     * assignment equal to the current value, so on an unchanged day this is free.
     */
    private fun refreshForWrite() {
        currentPeriodProvider.refresh()
    }

    private fun AchievementEntity.ladder(): TierLadder? =
        TierLadder.fromStoredColumns(bronzeThreshold, silverThreshold, goldThreshold)

    /** What the card's ring fills toward: Gold for a laddered achievement, else the target. */
    private fun AchievementEntity.displayTarget(): Int = ladder()?.completeThreshold ?: counterTarget

    /**
     * Whether the achievement is *done* for the period. With no ladder that is the row's
     * snapshotted `done` column, exactly as in Phase 1. With a ladder `done` only means "counts
     * toward the streak" (the lowest tier), so done is read from the snapshotted `tierReached`
     * instead — Gold and nothing else. Both are snapshots written with the count, so a closed
     * period's result stays immutable either way (TRACK-10).
     */
    private fun AchievementEntity.isDoneFor(progress: ProgressEntity?): Boolean =
        if (ladder() != null) {
            Tier.fromStored(progress?.tierReached) == Tier.GOLD
        } else {
            progress?.done ?: false
        }

    private fun AchievementEntity.toTracked(periodType: PeriodType, progress: ProgressEntity?) =
        TrackedAchievement(
            id = id,
            name = name,
            icon = icon,
            accent = swatchOf(accentSwatch),
            target = displayTarget(),
            count = progress?.count ?: 0,
            isDone = isDoneFor(progress),
            currentStreak = currentStreak,
            bestStreak = bestStreak,
            periodType = periodType,
            tiers = ladder(),
            tierReached = Tier.fromStored(progress?.tierReached),
        )

    private fun AchievementEntity.toDetail(periodType: PeriodType, progress: ProgressEntity?) =
        AchievementDetail(
            id = id,
            name = name,
            icon = icon,
            accent = swatchOf(accentSwatch),
            target = displayTarget(),
            periodCount = progress?.count ?: 0,
            isDone = isDoneFor(progress),
            currentStreak = currentStreak,
            bestStreak = bestStreak,
            periodType = periodType,
            tiers = ladder(),
            tierReached = Tier.fromStored(progress?.tierReached),
        )

    /**
     * Tolerant parse of the stored `periodType` — a hand-edited or future value maps to null
     * rather than throwing out of a read flow or a write.
     */
    private fun periodTypeOf(achievement: AchievementEntity): PeriodType? =
        PeriodType.entries.firstOrNull { it.name == achievement.periodType }

    /**
     * Maps a stored swatch name to an [AccentSwatch], falling back to [AccentSwatch.INDIGO] — the
     * creation screen's own default — rather than throwing.
     *
     * `AccentSwatch.valueOf` throws `IllegalArgumentException` for any string outside the enum,
     * and `accentSwatch` is a plain `TEXT` column with no `CHECK` constraint, so a hand-edited
     * row, a future migration, or a renamed/removed swatch can all produce one. Thrown from
     * inside a read flow, that exception reaches the tab ViewModel's `.catch`, which *terminates*
     * the upstream flow — leaving the tab permanently stuck on "Restart the app to try again".
     * A cosmetic column is not worth an unrecoverable screen; the wrong colour is.
     */
    private fun swatchOf(name: String): AccentSwatch =
        AccentSwatch.entries.firstOrNull { it.name == name } ?: AccentSwatch.INDIGO
}
