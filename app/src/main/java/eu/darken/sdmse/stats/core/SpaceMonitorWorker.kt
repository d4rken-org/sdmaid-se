package eu.darken.sdmse.stats.core

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.widget.WidgetUpdater

@HiltWorker
class SpaceMonitorWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val spaceTracker: SpaceTracker,
    private val widgetUpdater: WidgetUpdater,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        log(TAG, VERBOSE) { "doWork(): Recording storage snapshot" }
        spaceTracker.recordSnapshot()
        // Backstop refresh for placed home-screen widgets (free space may drift between cleans).
        widgetUpdater.updateAll()
        log(TAG, VERBOSE) { "doWork(): Done" }
        return Result.success()
    }

    companion object {
        val TAG = logTag("Stats", "SpaceMonitor", "Worker")
    }
}
