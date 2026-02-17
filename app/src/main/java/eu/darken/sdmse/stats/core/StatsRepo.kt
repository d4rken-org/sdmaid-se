package eu.darken.sdmse.stats.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.localized
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.flow.shareLatest
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.stats.core.db.ReportEntity
import eu.darken.sdmse.stats.core.db.ReportsDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepo @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val reportsDatabase: ReportsDatabase,
    private val statsSettings: StatsSettings,
) {

    val reports: Flow<Collection<Report>>
        get() = reportsDatabase.reports

    data class State(
        val reportsCount: Int,
        val totalSpaceFreed: Long,
        val itemsProcessed: Long,
        val databaseSize: Long,
    ) {
        val isEmpty: Boolean
            get() = reportsCount == 0 && totalSpaceFreed == 0L && itemsProcessed == 0L
    }

    val state = combine(
        reportsDatabase.reportCount,
        statsSettings.totalSpaceFreed.flow,
        statsSettings.totalItemsProcessed.flow,
        reportsDatabase.databaseSize,
    ) { reportsCount, spaceFreed, itemsProcessed, databaseSize ->
        State(
            reportsCount = reportsCount,
            totalSpaceFreed = spaceFreed,
            itemsProcessed = itemsProcessed,
            databaseSize = databaseSize,
        )
    }
        .throttleLatest(500)
        .shareLatest(appScope)

    suspend fun report(task: TaskManager.ManagedTask) {
        log(TAG, INFO) { "report(${task.id})...${task.task.javaClass}" }

        if (task.task !is Reportable) {
            log(TAG) { "report(${task.id}): Not reportable" }
            return
        }

        if (Bugs.isDebug) log(TAG) { "report(${task.id})...$task" }

        val reportDetails = (task.result as? ReportDetails)
        val report = ReportEntity(
            startAt = task.startedAt ?: Instant.now(),
            endAt = task.completedAt ?: Instant.now(),
            tool = task.tool.type,
            status = when {
                task.error != null -> Report.Status.FAILURE
                reportDetails != null -> reportDetails.status
                else -> Report.Status.SUCCESS
            },
            primaryMessage = task.result?.primaryInfo?.get(context),
            secondaryMessage = task.result?.secondaryInfo?.get(context),
            errorMessage = task.error?.localized(context)?.asText()?.get(context),
            affectedCount = reportDetails?.affectedCount,
            affectedSpace = reportDetails?.affectedSpace,
            extra = null,
        )
        reportsDatabase.addReport(report)

        report.affectedSpace?.let { affected ->
            log(TAG) { "report(${task.id}): Saving details about affected space: $affected" }
            statsSettings.totalSpaceFreed.update { it + affected }
        }
        report.affectedCount?.let { affected ->
            log(TAG) { "report(${task.id}): Saving details about affected items: $affected" }
            statsSettings.totalItemsProcessed.update { it + affected }
        }

        (reportDetails as? ReportDetails.AffectedPaths)?.let { pathsReport ->
            log(TAG) { "report(${task.id}): Saving details about affected ${pathsReport.affectedPaths.size} files " }
            val affectedPaths = pathsReport.affectedPaths.map { it.toAffectedPath(report.reportId, pathsReport.action) }
            reportsDatabase.addPaths(affectedPaths)
        }

        reportDetails?.affectedPkgs?.let { pkgs ->
            log(TAG) { "report(${task.id}): Saving details about affected pkgs: ${pkgs.size}" }
            val affectedPkgs = pkgs.toAffectedPkgs(report.reportId)
            reportsDatabase.addPkgs(affectedPkgs)
        }
    }

    suspend fun resetAll() = withContext(NonCancellable) {
        log(TAG, INFO) { "resetAll()" }
        statsSettings.totalItemsProcessed.value(0L)
        statsSettings.totalSpaceFreed.value(0L)
        reportsDatabase.clear()
    }

    private fun APath.toAffectedPath(
        id: ReportId,
        action: AffectedPath.Action = AffectedPath.Action.DELETED
    ) = object : AffectedPath {
        override val reportId: ReportId = id
        override val action: AffectedPath.Action = action
        override val path: APath = this@toAffectedPath
    }

    private fun Map<Pkg.Id, AffectedPkg.Action>.toAffectedPkgs(
        id: ReportId,
    ) = this.map { (pkgId, action) ->
        object : AffectedPkg {
            override val reportId: ReportId = id
            override val action: AffectedPkg.Action = action
            override val pkgId: Pkg.Id = pkgId
        }
    }

    suspend fun getById(id: ReportId): Report? {
        log(TAG) { "getById($id)" }
        return reportsDatabase.getReport(id)
    }

    suspend fun getAffectedPaths(id: ReportId): Collection<AffectedPath> {
        log(TAG) { "getAffectedPaths($id)" }
        return reportsDatabase.getAffectedPaths(id)
    }

    suspend fun getAffectedPkgs(id: ReportId): Collection<AffectedPkg> {
        log(TAG) { "getAffectedPkgs($id)" }
        return reportsDatabase.getAffectedPkgs(id)
    }

    companion object {
        private val TAG = logTag("Stats", "Repo")
    }
}