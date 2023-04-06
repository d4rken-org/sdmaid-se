package eu.darken.sdmse.systemcleaner.ui.details.filtercontent

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerTask

sealed class FilterContentEvents {
    data class ConfirmDeletion(val identifier: FilterIdentifier) : FilterContentEvents()
    data class ConfirmFileDeletion(val identifier: FilterIdentifier, val path: APath) : FilterContentEvents()
    data class TaskForParent(val task: SystemCleanerTask) : FilterContentEvents()
}
