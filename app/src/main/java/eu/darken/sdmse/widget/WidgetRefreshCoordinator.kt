package eu.darken.sdmse.widget

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps placed widgets in sync with [WidgetDataProvider.renderState] (freed bytes, storage figures,
 * working/cancellable transitions).
 *
 * A LIVE Glance session recomposes by itself — it collects `renderState` inside its composition. This
 * coordinator's `updateAll()` exists for DEAD sessions (Glance tears the composition down a while
 * after the last render): it re-runs `provideGlance`, which snapshots fresh state. On live sessions
 * the extra update is a cheap no-op recomposition.
 *
 * This complements the host-driven refresh (add / resize) and the 6h
 * [eu.darken.sdmse.stats.core.SpaceMonitorWorker] backstop.
 */
@Singleton
class WidgetRefreshCoordinator @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val widgetDataProvider: WidgetDataProvider,
    private val widgetUpdater: WidgetUpdater,
) {

    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        log(TAG, VERBOSE) { "start()" }

        // Once per process: refresh the A15+ generated picker preview (rate-limited system API).
        appScope.launch { widgetUpdater.publishPreviews() }

        // No drop(1): the initial replay produces exactly one refresh at app start, which re-bakes a
        // placed widget's RemoteViews + PendingIntents after an app update (the system alone doesn't
        // reliably do that — device-observed with install -r).
        widgetDataProvider.renderState
            .distinctUntilChanged()
            .conflate() // coalesce bursts (e.g. parallel one-tap tasks each writing freed bytes)
            .onEach { widgetUpdater.updateAll() }
            .launchIn(appScope)
    }

    companion object {
        private val TAG = logTag("Widget", "RefreshCoordinator")
    }
}
