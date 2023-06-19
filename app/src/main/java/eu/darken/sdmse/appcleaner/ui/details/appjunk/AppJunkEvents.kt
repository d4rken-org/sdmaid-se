package eu.darken.sdmse.appcleaner.ui.details.appjunk

import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerTask

sealed class AppJunkEvents {
    data class ConfirmDeletion(val items: Collection<AppJunkElementsAdapter.Item>) : AppJunkEvents()

    data class TaskForParent(val task: AppCleanerTask) : AppJunkEvents()
}
