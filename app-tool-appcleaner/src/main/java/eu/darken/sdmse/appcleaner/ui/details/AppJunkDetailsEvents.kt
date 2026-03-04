package eu.darken.sdmse.appcleaner.ui.details

import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerTask

sealed class AppJunkDetailsEvents {
    data class TaskResult(val result: AppCleanerTask.Result) : AppJunkDetailsEvents()
}
