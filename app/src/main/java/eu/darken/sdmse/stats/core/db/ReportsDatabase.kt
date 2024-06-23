package eu.darken.sdmse.stats.core.db

import android.content.Context
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.stats.core.Report
import eu.darken.sdmse.stats.core.ReportId
import eu.darken.sdmse.stats.core.StatisticsSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportsDatabase @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val statisticsSettings: StatisticsSettings,
) {

    private val database by lazy {
        Room.databaseBuilder(context, ReportsRoomDb::class.java, "reports").build()
    }

    init {
        appScope.launch {
            try {
                val oldReports = database.reports().getReportsOlderThan(
                    Instant.now() - statisticsSettings.reportRetention.value()
                )
                if (oldReports.isNotEmpty()) {
                    val beforeCount = database.reports().countAll()
                    log(TAG) { "Deleting old reports (${oldReports.size}): $oldReports" }
                    database.reports().delete(oldReports.map { it.reportId })
                    val afterCount = database.reports().countAll()
                    log(TAG) { "Clean up finished, reports before $beforeCount and after $afterCount" }
                }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to clean up reports: ${e.asLog()}" }
            }
        }
    }

    suspend fun find(id: ReportId): Report? = database.reports().getById(id)
        .also { log(TAG, VERBOSE) { "find($id) -> it" } }

    suspend fun add(report: Report) {
        log(TAG) { "add(): $report" }
        val entity = ReportEntity.from(report)
        database.reports().insert(entity)
    }

    companion object {
        internal val TAG = logTag("Stats", "Reports", "Database")
    }
}