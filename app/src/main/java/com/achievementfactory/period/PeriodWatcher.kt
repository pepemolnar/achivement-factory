package com.achievementfactory.period

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.achievementfactory.domain.maintenance.ReconcileStreaksUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Notices the day changing underneath a running process (PERIOD-04, PERIOD-06): system
 * broadcasts for a date change, a manual clock edit, and a timezone change, plus process resume,
 * all funnel into the same [onSignal] — [CurrentPeriodProvider.refresh] followed by relaunching
 * [ReconcileStreaksUseCase.runAll] for every [PeriodType] on [applicationScope]. Three signals, one
 * mechanism.
 *
 * Per D-27 there is no cross-check against a second clock source or unusual-jump heuristic
 * anywhere here — every signal is a genuine recomputation from the trusted device clock.
 * [ACTION_DATE_CHANGED], [ACTION_TIME_CHANGED] and [ACTION_TIMEZONE_CHANGED] are protected
 * system broadcasts that only the OS can send; the receiver is registered at runtime with
 * [ContextCompat.RECEIVER_NOT_EXPORTED] and declares nothing in the manifest (T-01-23).
 */
class PeriodWatcher(
    private val context: Context,
    private val currentPeriodProvider: CurrentPeriodProvider,
    private val reconcileStreaks: ReconcileStreaksUseCase,
    private val applicationScope: CoroutineScope,
) {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = onSignal()
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = onSignal()
    }

    /**
     * One reconciliation pass per recurrence unit, each with its own current key — a weekly
     * streak is walked Monday-to-Monday against the current week's key, never against today's
     * date (see [ReconcileStreaksUseCase.runFor]'s period-type guard). Run sequentially inside a
     * single launch so the three passes never interleave with each other.
     */
    private fun onSignal() {
        currentPeriodProvider.refresh()
        applicationScope.launch {
            PeriodType.entries.forEach { type ->
                reconcileStreaks.runAll(currentPeriodProvider.current(type).value, type)
            }
        }
    }

    /**
     * Registers the receiver and the process-lifecycle observer. Call once, at process start.
     *
     * There is deliberately no `stop()`. Both registrations are meant to last the process's whole
     * lifetime — that is what makes "notice the day changing underneath a running process" work
     * at all — and the OS tears down a runtime-registered receiver with the process anyway, so
     * there is nothing to leak. A `stop()` with no call site would only be an invitation to call
     * it wrongly: `unregisterReceiver` throws `IllegalArgumentException` if the receiver was never
     * registered or was already unregistered, and `removeObserver` must run on the main thread.
     * If a future phase genuinely needs to stop watching, add it *with* its call site and with a
     * `started` guard, rather than restoring an unused one.
     */
    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
    }
}
