package eu.darken.sdmse.stats.core

import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepo @Inject constructor() {

    suspend fun report(report: Report, details: Report.Details?) {
        log(TAG, Logging.Priority.INFO) { "report($report, $details)..." }

    }

    companion object {
        private val TAG = logTag("Stats", "Repo")
    }
}