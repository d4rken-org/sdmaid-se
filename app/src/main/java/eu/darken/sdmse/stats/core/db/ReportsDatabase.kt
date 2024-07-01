package eu.darken.sdmse.stats.core.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportsDatabase @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val statsSettings: StatsSettings,
    private val aPathTypeConverter: APathTypeConverter,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val database by lazy {
        Room
            .databaseBuilder(context, ReportsRoomDb::class.java, DB_NAME)
            .addTypeConverter(aPathTypeConverter)
            .build()
    }

    private val dbFile: File
        get() = context.getDatabasePath(DB_NAME)

    private fun getDatabaseSize(): Long {
        val total = listOf("", "-shm", "-wal").map { suffix ->
            val path = File(dbFile.parent, "$DB_NAME$suffix")
            val size = path.takeIf { it.exists() }?.length() ?: 0L
            log(TAG) { "Size of $path is $size" }
            size
        }.sum()
        log(TAG) { "Total size is $total" }
        return total
    }

    val databaseSize = MutableStateFlow(getDatabaseSize())

    private val reportsDao: ReportsDao
        get() = database.reports()

    private val filesDao: AffectedFilesDao
        get() = database.files()

    init {
        statsSettings.retentionReports.flow
            .mapNotNull { retention ->
                log(TAG, INFO) { "Retention for reports is $retention" }
                reportsDao.getReportsOlderThan(Instant.now() - retention)
            }
            .map { reports -> reports.map { it.reportId } }
            .onEach { oldReportIds ->
                var updateSize = false
                run {
                    val beforeCount = reportsDao.reportCount().first()
                    log(TAG) { "Deleting old reports (${oldReportIds.size})" }
                    reportsDao.delete(oldReportIds)
                    val deleted = beforeCount - reportsDao.reportCount().first()
                    log(TAG) { "Clean up of reports finished, deleted $deleted" }
                    if (deleted > 0) updateSize = true
                }

                run {
                    val filesBefore = filesDao.filesCount().first()
                    log(TAG) { "Deleting file infos related to old reports (${filesBefore})" }
                    filesDao.delete(oldReportIds)
                    val deleted = filesBefore - filesDao.filesCount().first()
                    log(TAG) { "Clean up of file infos finished, deleted $deleted" }
                    if (deleted > 0) updateSize = true
                }

                if (updateSize) databaseSize.value = getDatabaseSize()
            }
            .catch { log(TAG, ERROR) { "Failed to clean up reports: ${it.asLog()}" } }
            .launchIn(appScope + dispatcherProvider.IO)

        statsSettings.retentionPaths.flow
            .mapNotNull { retention ->
                log(TAG, INFO) { "Retention for affected files is $retention" }
                reportsDao.getReportsOlderThan(Instant.now() - retention).takeIf { it.isNotEmpty() }
            }
            .map { reports -> reports.map { it.reportId } }
            .onEach { oldReportIds ->
                val filesBefore = filesDao.filesCount().first()
                log(TAG) { "Deleting stale infos about affected files (${filesBefore})" }
                filesDao.delete(oldReportIds)
                val deleted = filesBefore - filesDao.filesCount().first()
                log(TAG) { "Clean up of stale file infos finished, deleted $deleted" }

                if (deleted > 0) databaseSize.value = getDatabaseSize()
            }
            .catch { log(TAG, ERROR) { "Failed to clean up affected files: ${it.asLog()}" } }
            .launchIn(appScope + dispatcherProvider.IO)
    }

    val reports = reportsDao.waterfall()

    val reportCount: Flow<Int>
        get() = reportsDao.reportCount()

    suspend fun find(id: ReportId): Report? = reportsDao.getById(id)
        .also { log(TAG, VERBOSE) { "find($id) -> it" } }

    suspend fun addReport(report: Report) {
        log(TAG) { "addReport(): $report" }
        val entity = ReportEntity.from(report)
        reportsDao.insert(entity)
        databaseSize.value = getDatabaseSize()
    }

    suspend fun addFiles(files: Collection<AffectedPath>) {
        if (files.isEmpty()) return
        log(TAG) { "addFiles(): ${files.size} files for ${files.first().reportId}" }
        val entities = files.map { AffectedPathEntity.from(it) }
        filesDao.insert(entities)
        databaseSize.value = getDatabaseSize()
    }

    suspend fun clear() = withContext(dispatcherProvider.IO) {
        log(TAG, INFO) { "clear()" }
        val walFile = File(context.getDatabasePath(DB_NAME).parent, "$DB_NAME-wal")
        val shmFile = File(context.getDatabasePath(DB_NAME).parent, "$DB_NAME-shm")

        if (walFile.exists() || shmFile.exists()) {
            SQLiteDatabase.openDatabase(
                dbFile.path,
                null,
                SQLiteDatabase.OPEN_READWRITE
            ).use {
                it.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { }
            }
        }

        walFile.delete()
        shmFile.delete()

        database.clearAllTables()

        databaseSize.value = getDatabaseSize()
    }

    companion object {
        private const val DB_NAME = "reports"
        internal val TAG = logTag("Stats", "Reports", "Database")
    }
}