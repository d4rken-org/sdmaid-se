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
import eu.darken.sdmse.common.room.APathTypeConverter
import eu.darken.sdmse.stats.core.AffectedPath
import eu.darken.sdmse.stats.core.Report
import eu.darken.sdmse.stats.core.ReportId
import eu.darken.sdmse.stats.core.StatsSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportsDatabase @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val statsSettings: StatsSettings,
    private val aPathTypeConverter: APathTypeConverter,
) {

    private val database by lazy {
        Room
            .databaseBuilder(context, ReportsRoomDb::class.java, "reports")
            .addTypeConverter(aPathTypeConverter)
            .build()
    }

    private val reportsDao: ReportsDao
        get() = database.reports()

    private val filesDao: AffectedFilesDao
        get() = database.files()

    init {
        appScope.launch {
            try {
                val oldReports = reportsDao.getReportsOlderThan(
                    Instant.now() - statsSettings.reportRetention.value()
                )
                if (oldReports.isNotEmpty()) {
                    run {
                        val beforeCount = reportsDao.countAll()
                        log(TAG) { "Deleting old reports (${oldReports.size})" }
                        reportsDao.delete(oldReports.map { it.reportId })
                        val afterCount = reportsDao.countAll()
                        log(TAG) { "Clean up of reports finished: before$beforeCount, after=$afterCount" }
                    }

                    run {
                        val filesBefore = filesDao.countAll()
                        log(TAG) { "Deleting file infos related to old reports (${filesBefore})" }
                        filesDao.delete(oldReports.map { it.reportId })
                        val filesAfter = filesDao.countAll()
                        log(TAG) { "Clean up of file infos finished: before$filesBefore, after=$filesAfter" }
                    }

                }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to clean up reports: ${e.asLog()}" }
            }
        }
    }

    val reports = reportsDao.waterfall()

    suspend fun find(id: ReportId): Report? = reportsDao.getById(id)
        .also { log(TAG, VERBOSE) { "find($id) -> it" } }

    suspend fun addReport(report: Report) {
        log(TAG) { "addReport(): $report" }
        val entity = ReportEntity.from(report)
        reportsDao.insert(entity)
    }

    suspend fun addFiles(files: Collection<AffectedPath>) {
        if (files.isEmpty()) return
        log(TAG) { "addFiles(): ${files.size} files for ${files.first().reportId}" }
        val entities = files.map { AffectedPathEntity.from(it) }
        filesDao.insert(entities)
    }

    companion object {
        internal val TAG = logTag("Stats", "Reports", "Database")
    }
}