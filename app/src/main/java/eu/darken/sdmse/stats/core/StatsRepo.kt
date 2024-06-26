package eu.darken.sdmse.stats.core

import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.stats.core.db.ReportsDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepo @Inject constructor(
    private val reportsDatabase: ReportsDatabase,
) {

    suspend fun report(report: Report, details: Report.Details?) {
        log(TAG, INFO) { "report($report, $details)..." }
        reportsDatabase.add(report)
    }

    companion object {
        private val TAG = logTag("Stats", "Repo")
    }
}