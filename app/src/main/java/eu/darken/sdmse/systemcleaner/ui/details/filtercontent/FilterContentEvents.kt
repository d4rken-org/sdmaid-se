package eu.darken.sdmse.systemcleaner.ui.details.filtercontent

import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerTask

sealed class FilterContentEvents {
    data class ConfirmDeletion(val items: Collection<FilterContentElementsAdapter.Item>) : FilterContentEvents()
    data class TaskForParent(val task: SystemCleanerTask) : FilterContentEvents()
}
