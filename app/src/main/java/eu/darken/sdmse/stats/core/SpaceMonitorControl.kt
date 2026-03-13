package eu.darken.sdmse.stats.core

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpaceMonitorControl @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val workManager: WorkManager,
    private val spaceTracker: SpaceTracker,
) {

    fun start() {
        log(TAG, VERBOSE) { "start()" }

        appScope.launch {
            spaceTracker.recordSnapshot()
        }

        val workRequest = PeriodicWorkRequestBuilder<SpaceMonitorWorker>(
            INTERVAL_HOURS,
            TimeUnit.HOURS,
        ).build()

        workManager.enqueueUniquePeriodicWork(
            "${BuildConfigWrap.APPLICATION_ID}.stats.space-monitor",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest,
        )

        log(TAG) { "Periodic space monitoring scheduled (interval=${INTERVAL_HOURS}h)" }
    }

    fun stop() {
        log(TAG) { "stop(): Cancelling periodic space monitoring" }
        workManager.cancelUniqueWork("${BuildConfigWrap.APPLICATION_ID}.stats.space-monitor")
    }

    companion object {
        private const val INTERVAL_HOURS = 6L
        private val TAG = logTag("Stats", "SpaceMonitor", "Control")
    }
}
