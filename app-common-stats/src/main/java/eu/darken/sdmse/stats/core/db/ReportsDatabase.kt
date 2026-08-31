package eu.darken.sdmse.stats.core.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.room.APathTypeConverter
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.stats.core.AffectedPath
import eu.darken.sdmse.stats.core.AffectedPkg
import eu.darken.sdmse.stats.core.Report
import eu.darken.sdmse.stats.core.ReportId
import eu.darken.sdmse.stats.core.StatsSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
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

    /** Underlying Room database, exposed for config backup/restore. */
    val roomDb: RoomDatabase
        get() = database

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

    val databaseSize = MutableStateFlow(0L)

    private val reportsDao: ReportsDao
        get() = database.reports()

    private val pathsDao: AffectedPathsDao
        get() = database.paths()

    private val pkgsDao: AffectedPkgsDao
        get() = database.pkgs()

    internal val spaceSnapshotDao: SpaceSnapshotDao
        get() = database.spaceSnapshots()

    private suspend fun pruneReports(retention: Duration) {
        log(TAG, INFO) { "Retention for reports is $retention" }
        val cutOff = Instant.now() - retention

        val deleted = database.withTransaction {
            val paths = pathsDao.deleteForReportsOlderThan(cutOff)
            val pkgs = pkgsDao.deleteForReportsOlderThan(cutOff)
            val reports = reportsDao.deleteOlderThan(cutOff)
            val orphanedPaths = pathsDao.deleteOrphans()
            val orphanedPkgs = pkgsDao.deleteOrphans()
            log(TAG) {
                "Clean up of reports older than $cutOff finished: $reports reports, " +
                        "$paths paths ($orphanedPaths orphaned), $pkgs pkgs ($orphanedPkgs orphaned)"
            }
            reports + paths + pkgs + orphanedPaths + orphanedPkgs
        }

        if (deleted > 0) databaseSize.value = getDatabaseSize()
    }

    private suspend fun pruneAffectedPaths(retention: Duration) {
        log(TAG, INFO) { "Retention for affected files is $retention" }
        val cutOff = Instant.now() - retention

        val deleted = database.withTransaction { pathsDao.deleteForReportsOlderThan(cutOff) }
        log(TAG) { "Clean up of stale file infos finished, deleted $deleted" }

        if (deleted > 0) databaseSize.value = getDatabaseSize()
    }

    /** Sweeps expired data now, instead of waiting for the retention settings to emit again. */
    suspend fun applyRetention() = withContext(dispatcherProvider.IO) {
        log(TAG) { "applyRetention()" }
        try {
            pruneReports(statsSettings.retentionReports.value())
            pruneAffectedPaths(statsSettings.retentionPaths.value())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "applyRetention() failed: ${e.asLog()}" }
        }
    }

    init {
        statsSettings.retentionReports.flow
            .onEach { retention ->
                try {
                    pruneReports(retention)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Failed to clean up reports: ${e.asLog()}" }
                }
            }
            .launchIn(appScope + dispatcherProvider.IO)

        statsSettings.retentionPaths.flow
            .onEach { retention ->
                try {
                    pruneAffectedPaths(retention)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Failed to clean up affected files: ${e.asLog()}" }
                }
            }
            .launchIn(appScope + dispatcherProvider.IO)

        statsSettings.retentionSnapshots.flow
            .onEach { retention ->
                val cutOff = Instant.now() - retention
                val beforeCount = spaceSnapshotDao.snapshotCount().first()
                log(TAG, INFO) { "Retention for snapshots is $retention, deleting older than $cutOff" }
                spaceSnapshotDao.deleteOlderThan(cutOff)
                val deleted = beforeCount - spaceSnapshotDao.snapshotCount().first()
                log(TAG) { "Clean up of snapshots finished, deleted $deleted" }

                if (deleted > 0) databaseSize.value = getDatabaseSize()
            }
            .catch { log(TAG, ERROR) { "Failed to clean up snapshots: ${it.asLog()}" } }
            .launchIn(appScope + dispatcherProvider.IO)

        appScope.launch(dispatcherProvider.IO) {
            databaseSize.value = getDatabaseSize()
        }
    }

    val reports = reportsDao.waterfall()

    val reportCount: Flow<Int>
        get() = reportsDao.reportCount()

    val snapshotsCount: Flow<Int>
        get() = spaceSnapshotDao.snapshotCount()

    fun getReportsSince(since: Instant): Flow<List<ReportEntity>> = reportsDao.getReportsSince(since)

    suspend fun getReport(id: ReportId): Report? = reportsDao.getById(id)

    suspend fun getReportForToolSince(tool: SDMTool.Type, since: Instant): Report? =
        reportsDao.getReportForToolSince(tool, since)

    suspend fun getAffectedPaths(id: ReportId): Collection<AffectedPath> {
        return pathsDao.getById(id)
    }

    suspend fun getAffectedPkgs(id: ReportId): Collection<AffectedPkg> {
        return pkgsDao.getById(id)
    }

    suspend fun addReport(report: Report) {
        log(TAG) { "addReport(): $report" }
        val entity = ReportEntity.from(report)
        reportsDao.insert(entity)
        databaseSize.value = getDatabaseSize()
    }

    suspend fun addPaths(paths: Collection<AffectedPath>) {
        if (paths.isEmpty()) return
        log(TAG) { "addPaths(): ${paths.size} paths for ${paths.first().reportId}" }
        val entities = paths.map { AffectedPathEntity.from(it) }
        pathsDao.insert(entities)
        databaseSize.value = getDatabaseSize()
    }

    suspend fun addPkgs(pkgs: Collection<AffectedPkg>) {
        if (pkgs.isEmpty()) return
        log(TAG) { "addPkgs(): ${pkgs.size} pkgs for ${pkgs.first().reportId}" }
        val entities = pkgs.map { AffectedPkgEntity.from(it) }
        pkgsDao.insert(entities)
        databaseSize.value = getDatabaseSize()
    }

    suspend fun clear() = withContext(dispatcherProvider.IO) {
        log(TAG, INFO) { "clear()" }
        database.clearAllTables()

        databaseSize.value = getDatabaseSize()
    }

    internal suspend fun <T> withTransaction(block: suspend () -> T): T {
        return database.withTransaction { block() }
    }

    internal fun refreshDatabaseSize() {
        databaseSize.value = getDatabaseSize()
    }

    companion object {
        private const val DB_NAME = "reports"
        internal val TAG = logTag("Stats", "Reports", "Database")
    }
}