package eu.darken.sdmse.systemcleaner.ui.details

import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerTask

sealed class FilterContentDetailsEvents {
    data class TaskResult(val result: SystemCleanerTask.Result) : FilterContentDetailsEvents()
}
