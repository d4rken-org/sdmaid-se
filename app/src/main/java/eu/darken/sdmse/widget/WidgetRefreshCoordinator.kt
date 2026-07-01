package eu.darken.sdmse.widget

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.stats.core.StatsSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps placed widgets in sync with the lifetime "space freed" total.
 *
 * Driving off [StatsSettings.totalSpaceFreed] (the DataStore source of truth) rather than the task
 * idle-transition is deterministic: the emission carries the freshly-written value, so the resulting
 * render reads an up-to-date figure with no race against the stats writer. The flow also replays its
 * current value on subscription (→ one refresh at app start) and emits on `StatsRepo.resetAll()`.
 *
 * This complements the host-driven refresh (add/resize) and the 6h [eu.darken.sdmse.stats.core.SpaceMonitorWorker]
 * backstop.
 */
@Singleton
class WidgetRefreshCoordinator @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val statsSettings: StatsSettings,
    private val widgetUpdater: WidgetUpdater,
) {

    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        log(TAG, VERBOSE) { "start()" }

        statsSettings.totalSpaceFreed.flow
            .distinctUntilChanged()
            .conflate() // coalesce bursts (e.g. parallel one-tap tasks each writing freed)
            .onEach { widgetUpdater.updateAll() }
            .launchIn(appScope)
    }

    companion object {
        private val TAG = logTag("Widget", "RefreshCoordinator")
    }
}
