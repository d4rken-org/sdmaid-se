package eu.darken.sdmse.systemcleaner.ui.list

import eu.darken.sdmse.systemcleaner.core.FilterContent

sealed class SystemCleanerListEvents {
    data class ConfirmDeletion(
        val filterContent: FilterContent
    ) : SystemCleanerListEvents()
}
