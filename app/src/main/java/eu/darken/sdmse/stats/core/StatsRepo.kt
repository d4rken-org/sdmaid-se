package eu.darken.sdmse.stats.core

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.shareLatest
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.stats.core.db.ReportsDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepo @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val reportsDatabase: ReportsDatabase,
    private val statsSettings: StatsSettings,
) {

    val reports = reportsDatabase.reports

    val state = combine(
        reportsDatabase.reports,
        statsSettings.totalSpaceFreed.flow,
        statsSettings.totalItemsProcessed.flow,
    ) { reports, spaceFreed, itemsProcessed ->
        State(
            reports = reports,
            totalSpaceFreed = spaceFreed,
            itemsProcessed = itemsProcessed,
        )
    }
        .throttleLatest(500)
        .shareLatest(appScope)

    suspend fun report(report: Report, details: Report.Details?) {
        log(TAG, INFO) { "report($report, $details)..." }
        reportsDatabase.add(report)
        if (details is Report.Details.SpaceFreed) {
            log(TAG) { "Saving details about freed space from $details" }
            statsSettings.totalSpaceFreed.update { it + details.spaceFreed }
        }
        if (details is Report.Details.ItemsProcessed) {
            log(TAG) { "Saving details about processed items from $details" }
            statsSettings.totalItemsProcessed.update { it + details.processedCount }
        }
    }

    data class State(
        val reports: List<Report>,
        val totalSpaceFreed: Long,
        val itemsProcessed: Long,
    )

    companion object {
        private val TAG = logTag("Stats", "Repo")
    }
}