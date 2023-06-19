package eu.darken.sdmse.appcleaner.ui.list

import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerTask

sealed class AppCleanerListEvents {
    data class ConfirmDeletion(val items: Collection<AppCleanerListAdapter.Item>) : AppCleanerListEvents()
    data class TaskResult(val result: AppCleanerTask.Result) : AppCleanerListEvents()

    data class ExclusionsCreated(val count: Int) : AppCleanerListEvents()
}
