package eu.darken.sdmse.systemcleaner.ui.list

import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerTask

sealed class SystemCleanerListEvents {
    data class ConfirmDeletion(val items: Collection<SystemCleanerListAdapter.Item>) : SystemCleanerListEvents()
    data class TaskResult(val result: SystemCleanerTask.Result) : SystemCleanerListEvents()
}
