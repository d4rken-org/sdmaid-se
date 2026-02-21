package eu.darken.sdmse.main.ui.dashboard

import android.content.Intent
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerProcessingTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorDeleteTask
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerProcessingTask

sealed interface DashboardEvents {

    data object TodoHint : DashboardEvents
    data object SetupDismissHint : DashboardEvents

    data class CorpseFinderDeleteConfirmation(
        val task: CorpseFinderDeleteTask,
    ) : DashboardEvents

    data class SystemCleanerDeleteConfirmation(
        val task: SystemCleanerProcessingTask,
    ) : DashboardEvents

    data class AppCleanerDeleteConfirmation(
        val task: AppCleanerProcessingTask,
    ) : DashboardEvents

    data class DeduplicatorDeleteConfirmation(
        val task: DeduplicatorDeleteTask,
        val clusters: List<Duplicate.Cluster>? = null,
    ) : DashboardEvents

    data class TaskResult(
        val result: SDMTool.Task.Result
    ) : DashboardEvents

    data class OpenIntent(val intent: Intent) : DashboardEvents

    data object SqueezerSetup : DashboardEvents

}
