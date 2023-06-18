package eu.darken.sdmse.appcleaner.ui.list

import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerTask
import eu.darken.sdmse.exclusion.core.types.Exclusion

sealed class AppCleanerListEvents {
    data class ConfirmDeletion(val items: Collection<AppCleanerListAdapter.Item>) : AppCleanerListEvents()
    data class TaskResult(val result: AppCleanerTask.Result) : AppCleanerListEvents()

    data class ExclusionsCreated(val exclusions: Collection<Exclusion>) : AppCleanerListEvents()
}
