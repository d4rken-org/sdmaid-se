package eu.darken.sdmse.appcleaner.ui.list

import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerTask

sealed class AppCleanerListEvents {
    data class ConfirmDeletion(val appJunk: AppJunk) : AppCleanerListEvents()
    data class TaskResult(val result: AppCleanerTask.Result) : AppCleanerListEvents()
}
