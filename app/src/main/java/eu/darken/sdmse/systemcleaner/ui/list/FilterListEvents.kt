package eu.darken.sdmse.systemcleaner.ui.list

import eu.darken.sdmse.systemcleaner.core.FilterContent

sealed class FilterListEvents {
    data class ConfirmDeletion(
        val filterContent: FilterContent
    ) : FilterListEvents()
}
