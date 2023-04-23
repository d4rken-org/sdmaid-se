package eu.darken.sdmse.appcleaner.ui.details.appjunk

import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerDeleteTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerTask

sealed class AppJunkEvents {
    data class ConfirmDeletion(
        val appJunk: AppJunk,
        val deletionTask: AppCleanerDeleteTask,
    ) : AppJunkEvents()

    data class TaskForParent(val task: AppCleanerTask) : AppJunkEvents()
}
