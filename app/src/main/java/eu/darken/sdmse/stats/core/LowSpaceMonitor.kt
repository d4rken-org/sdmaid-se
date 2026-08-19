package eu.darken.sdmse.stats.core

import eu.darken.sdmse.analyzer.core.AnalyzerSettings
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isProSettled
import eu.darken.sdmse.stats.core.forecast.StorageForecaster
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether the Pro low-space warning should be showing, and makes it so.
 *
 * Driven by the six-hourly [SpaceMonitorWorker] plus an observer that reacts the moment the user
 * flips the setting or their Pro state changes. Six-hourly is inexact and Doze can defer it, which
 * is acceptable for a gigabyte-scale advisory.
 */
@Singleton
class LowSpaceMonitor @Inject constructor(
    private val spaceTracker: SpaceTracker,
    private val spaceHistoryRepo: SpaceHistoryRepo,
    private val analyzerSettings: AnalyzerSettings,
    private val upgradeRepo: UpgradeRepo,
    private val notifications: LowSpaceNotifications,
) {

    private val started = AtomicBoolean(false)

    /**
     * Serializes the read-decide-post-write transaction.
     *
     * The worker run, the observer and a toggle-off can all land at once. Without this, two checks
     * could both read `armed = true` and post twice, and a [cancelAndRearm] could finish before an
     * in-flight check posts, leaving a warning on screen with the latch disarmed after the feature
     * was switched off or Pro lapsed.
     */
    private val mutex = Mutex()

    /**
     * Reacts to the toggle and to Pro state without waiting for the next worker run.
     *
     * Cancelling on the way down keeps a posted warning from lingering for up to six hours after
     * the user switched it off, and the [check] on the way up makes enabling the setting do
     * something observable instead of looking broken.
     */
    fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        log(TAG, VERBOSE) { "start()" }

        combine(
            analyzerSettings.lowSpaceNotificationEnabled.flow,
            // `null` means "not known yet", not "not Pro": the pre-billing seed reports non-Pro even
            // for paying users, and acting on it would cancel and re-arm on every process start,
            // re-notifying a user who already dismissed the warning. Mapping (instead of filtering
            // the unsettled emissions away) keeps the toggle-off branch below reachable while
            // billing is still settling.
            upgradeRepo.upgradeInfo.map { if (it.isSettled) it.isPro else null },
        ) { enabled, isPro -> enabled to isPro }
            .distinctUntilChanged()
            .onEach { (enabled, isPro) ->
                when {
                    // Switched off wins over an unknown entitlement: a warning left over from a
                    // previous process must go away now, not once billing settles.
                    !enabled -> {
                        log(TAG) { "Low space warning is disabled, cancelling" }
                        cancelAndRearm()
                    }

                    isPro == true -> {
                        log(TAG) { "Low space warning became active, checking now" }
                        check()
                    }

                    isPro == false -> {
                        log(TAG) { "Low space warning is not available without Pro, cancelling" }
                        cancelAndRearm()
                    }

                    else -> log(TAG, VERBOSE) { "Entitlement is unsettled, waiting" }
                }
            }
            .launchIn(scope)
    }

    /**
     * One evaluation of the primary volume. Never throws: a notification failure must not break
     * snapshot recording or the widget refresh in [SpaceMonitorWorker].
     */
    suspend fun check() {
        try {
            mutex.withLock { checkInternal() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "check() failed: ${e.asLog()}" }
        }
    }

    private suspend fun checkInternal() {
        val enabled = analyzerSettings.lowSpaceNotificationEnabled.value()
        val armed = analyzerSettings.lowSpaceNotificationArmed.value()

        // recordSnapshot() returns Unit and is throttled to 30 minutes, so the worker holds no
        // fresh reading of its own.
        val primary = spaceTracker.readPrimaryStorage()
        // readPrimaryStorage() can return a non-null 0/0 reading, which would otherwise classify as
        // a warning state and post "0 B free".
        if (primary == null || primary.spaceCapacity <= 0L || primary.spaceFree !in 0L..primary.spaceCapacity) {
            log(TAG, WARN) { "check(): Implausible primary reading, skipping: $primary" }
            return
        }

        // isProSettled() is the background gate; isProForUi() is for tap routing.
        val isPro = if (enabled) upgradeRepo.isProSettled() else false

        val forecast = if (enabled && isPro) {
            // Single-storage history only: StorageTrendCalculator needs one volume's snapshots, a
            // merged multi-volume list yields a meaningless rate.
            val history = spaceHistoryRepo
                .getHistory(primary.storageId, Instant.now() - HISTORY_WINDOW)
                .first()
            StorageForecaster.forecast(
                history = history,
                current = primary,
                lowStorageThresholdBytes = LowStorage.resolveThreshold(
                    capacityBytes = primary.spaceCapacity,
                    customThresholdBytes = analyzerSettings.lowStorageThresholdBytes.value(),
                ),
            )
        } else {
            null
        }

        val decision = LowSpaceAlertDecider.decide(
            enabled = enabled,
            isPro = isPro,
            armed = armed,
            forecast = forecast,
            freeBytes = primary.spaceFree,
        )
        log(TAG) { "check(): $decision (forecast=$forecast, armed=$armed, enabled=$enabled, isPro=$isPro)" }

        var armedAfter = decision.armedAfter
        when (val action = decision.action) {
            is LowSpaceAction.Notify -> {
                val result = notifications.notifyLowSpace(action.forecast, action.freeBytes)
                // Only a successful post spends the latch.
                if (result == LowSpaceNotifications.PostResult.POSTED) armedAfter = false
            }

            LowSpaceAction.Cancel -> notifications.cancel()
            LowSpaceAction.Nothing -> {}
        }

        if (armedAfter != null) setArmed(armedAfter)
    }

    private suspend fun cancelAndRearm() {
        try {
            mutex.withLock {
                notifications.cancel()
                setArmed(true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "cancelAndRearm() failed: ${e.asLog()}" }
        }
    }

    /** Writes only on an actual change, so a device with the feature off takes no six-hourly write. */
    private suspend fun setArmed(value: Boolean) {
        if (analyzerSettings.lowSpaceNotificationArmed.value() == value) return
        log(TAG) { "setArmed($value)" }
        analyzerSettings.lowSpaceNotificationArmed.value(value)
    }

    companion object {
        // Same window the dashboard forecast uses.
        private val HISTORY_WINDOW: Duration = Duration.ofDays(7)
        private val TAG = logTag("Stats", "LowSpace", "Monitor")
    }
}
