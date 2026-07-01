package eu.darken.sdmse.widget

import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.stats.core.SpaceTracker
import eu.darken.sdmse.stats.core.StatsSettings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single testable read-seam for the home-screen widget.
 *
 * Reads the live primary + secondary (SD card / USB) storage figures and the lifetime "space freed"
 * total, clamps them, and maps them to an immutable [WidgetRenderState]. Freed bytes come from the
 * [StatsSettings] DataStore value (the source of truth) rather than the throttled `StatsRepo.state`.
 */
@Singleton
class WidgetDataProvider @Inject constructor(
    private val spaceTracker: SpaceTracker,
    private val statsSettings: StatsSettings,
) {

    suspend fun snapshot(): WidgetRenderState {
        val entries = buildList {
            spaceTracker.readPrimaryStorage()
                ?.let { entry(WidgetRenderState.Data.StorageEntry.Kind.INTERNAL, it) }
                ?.let { add(it) }
            spaceTracker.readSecondaryStorages()
                .mapNotNull { entry(WidgetRenderState.Data.StorageEntry.Kind.EXTERNAL, it) }
                .let { addAll(it) }
        }.take(MAX_STORAGES)

        if (entries.isEmpty()) {
            log(TAG, WARN) { "snapshot(): no readable storage volume → Unavailable" }
            return WidgetRenderState.Unavailable
        }

        val freed = statsSettings.totalSpaceFreed.value().coerceAtLeast(0L)
        return WidgetRenderState.Data(storages = entries, freedBytes = freed).also {
            log(TAG, VERBOSE) { "snapshot(): $it" }
        }
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
