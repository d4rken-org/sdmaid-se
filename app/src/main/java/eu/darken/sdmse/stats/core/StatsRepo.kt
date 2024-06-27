package eu.darken.sdmse.stats.core

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.flow.shareLatest
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.stats.core.db.ReportEntity
import eu.darken.sdmse.stats.core.db.ReportsDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import java.time.Instant
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

    suspend fun report(task: TaskManager.ManagedTask) {
        log(TAG, INFO) { "report(${task.id})..." }

        if (task.task !is Reportable) {
            log(TAG) { "report(${task.id}): Not reportable" }
            return
        }

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
            errorMessage = task.error?.toString(),
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

        reportDetails?.affectedPaths?.let { files ->
            log(TAG) { "report(${task.id}): Saving details about affected ${files.size} files " }
            val affectedPaths = files.map { it.toAffectedPath(report.reportId, report.tool) }
            reportsDatabase.addFiles(affectedPaths)
        }

        reportDetails?.affectedPkgs?.let { files ->
            log(TAG) { "report(${task.id}): Saving details about affected pkgs: ${files.size}" }
            // TODO
        }
    }

    private fun APath.toAffectedPath(
        id: ReportId,
        tool: SDMTool.Type,
        action: AffectedPath.Action = AffectedPath.Action.DELETED
    ) = object : AffectedPath {
        override val reportId: ReportId = id
        override val tool: SDMTool.Type = tool
        override val action: AffectedPath.Action = action
        override val path: APath = this@toAffectedPath
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