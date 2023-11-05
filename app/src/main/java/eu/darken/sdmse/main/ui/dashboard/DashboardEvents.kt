package eu.darken.sdmse.main.ui.dashboard

import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerDeleteTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorDeleteTask
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerDeleteTask

sealed interface DashboardEvents {

    object TodoHint : DashboardEvents
    object SetupDismissHint : DashboardEvents

    data class CorpseFinderDeleteConfirmation(
        val task: CorpseFinderDeleteTask,
    ) : DashboardEvents

    data class SystemCleanerDeleteConfirmation(
        val task: SystemCleanerDeleteTask,
    ) : DashboardEvents

    data class AppCleanerDeleteConfirmation(
        val task: AppCleanerDeleteTask,
    ) : DashboardEvents

    data class DeduplicatorDeleteConfirmation(
        val task: DeduplicatorDeleteTask,
        val clusters: List<Duplicate.Cluster>? = null,
    ) : DashboardEvents

    data class TaskResult(
        val result: SDMTool.Task.Result
    ) : DashboardEvents
}