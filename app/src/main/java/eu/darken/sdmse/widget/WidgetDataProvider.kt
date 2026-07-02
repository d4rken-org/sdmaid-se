package eu.darken.sdmse.widget

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.shareLatest
import eu.darken.sdmse.main.core.shortcuts.OneTapRunGuard
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.stats.core.SpaceTracker
import eu.darken.sdmse.stats.core.StatsSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single testable read-seam for the home-screen widget.
 *
 * Reads the live primary + secondary (SD card / USB) storage figures, the lifetime "space freed"
 * total and the working/cancellable task state, clamps them, and maps them to an immutable
 * [WidgetRenderState]. Freed bytes come from the [StatsSettings] DataStore value (the source of
 * truth) rather than the throttled `StatsRepo.state`.
 */
@Singleton
class WidgetDataProvider @Inject constructor(
    @AppScope appScope: CoroutineScope,
    private val spaceTracker: SpaceTracker,
    private val statsSettings: StatsSettings,
    private val taskSubmitter: TaskSubmitter,
    private val oneTapRunGuard: OneTapRunGuard,
) {

    /**
     * Reactive render state, collected INSIDE the widget composition ([SdmHomeWidget.provideGlance]'s
     * `provideContent` block). This must be a flow: Glance keeps a widget session alive for a while
     * after a render, and `updateAll()` on a live session only recomposes the content lambda — it
     * does NOT re-run `provideGlance`. A one-shot preamble read therefore goes stale exactly when a
     * clean starts shortly after the last render; emissions here recompose the live session instead.
     *
     * "Working" spans the whole OneTap run via the guard (TaskManager reports idle between the
     * sequentially-submitted tools); the task-state branch also covers work started elsewhere
     * (dashboard scan, scheduler). Cancellable while the guard runs (its job can be cancelled) or
     * while any task isn't already cancelling — mirrors the dashboard FAB's WORKING_CANCELABLE.
     */
    val renderState: Flow<WidgetRenderState> = combine(
        oneTapRunGuard.running,
        taskSubmitter.state.map { !it.isIdle to it.hasCancellable }.distinctUntilChanged(),
        statsSettings.totalSpaceFreed.flow,
    ) { guardRunning, (busy, cancellable), freed ->
        Signals(
            working = guardRunning || busy,
            cancellable = guardRunning || cancellable,
            freed = freed,
        )
    }
        // Hold the displayed lifetime total steady while a run is in progress: the counter is
        // bumped once per finished tool (4x per one-tap run) and per-task ticking reads as flicker
        // on the widget — especially across unit boundaries ("981 MB" → "1.0 GB" looks smaller).
        // The aggregate is released once the run settles. Note task-idle isn't guaranteed to be
        // stats-settled, so the final total may arrive as one quick follow-up right after idle.
        .scan(null as Signals?) { prev, cur ->
            if (cur.working && prev != null) cur.copy(freed = prev.freed) else cur
        }
        .filterNotNull()
        .distinctUntilChanged()
        .map { buildState(working = it.working, cancellable = it.cancellable, freedBytes = it.freed) }
        .distinctUntilChanged()
        .map { it.also { log(TAG, VERBOSE) { "renderState: $it" } } }
        // Shared so the latch lives ONCE in this singleton: a cold per-collector chain would let a
        // Glance session created mid-run (resize, launcher restart) start with prev=null and show a
        // partial value the other collectors are latching. WidgetRefreshCoordinator subscribes for
        // the app's lifetime, keeping the latch state alive; sharing also dedupes the storage reads
        // across collectors (coordinator + one per widget session).
        .shareLatest(appScope)

    private data class Signals(val working: Boolean, val cancellable: Boolean, val freed: Long)

    suspend fun snapshot(): WidgetRenderState = renderState.first()

    private suspend fun buildState(
        working: Boolean,
        cancellable: Boolean,
        freedBytes: Long,
    ): WidgetRenderState {
        val entries = buildList {
            spaceTracker.readPrimaryStorage()
                ?.let { entry(WidgetRenderState.Data.StorageEntry.Kind.INTERNAL, it) }
                ?.let { add(it) }
            spaceTracker.readSecondaryStorages()
                .mapNotNull { entry(WidgetRenderState.Data.StorageEntry.Kind.EXTERNAL, it) }
                .let { addAll(it) }
        }.take(MAX_STORAGES)

        if (entries.isEmpty()) {
            log(TAG, WARN) { "buildState(): no readable storage volume → Unavailable" }
            return WidgetRenderState.Unavailable
        }

        return WidgetRenderState.Data(
            storages = entries,
            freedBytes = freedBytes.coerceAtLeast(0L),
            isWorking = working,
            isCancellable = working && cancellable,
        )
    }

    private fun entry(
        kind: WidgetRenderState.Data.StorageEntry.Kind,
        snapshot: SpaceTracker.StorageSnapshot,
    ): WidgetRenderState.Data.StorageEntry? {
        val total = snapshot.spaceCapacity
        if (total <= 0L) return null
        val free = snapshot.spaceFree.coerceIn(0L, total)
        return WidgetRenderState.Data.StorageEntry(
            kind = kind,
            usedBytes = (total - free).coerceIn(0L, total),
            totalBytes = total,
        )
    }

    companion object {
        /** Cap rows so the widget height stays bounded on multi-volume devices. */
        private const val MAX_STORAGES = 3
        private val TAG = logTag("Widget", "DataProvider")
    }
}
