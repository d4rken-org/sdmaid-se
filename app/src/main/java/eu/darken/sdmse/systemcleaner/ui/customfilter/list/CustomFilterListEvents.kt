package eu.darken.sdmse.systemcleaner.ui.customfilter.list

import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterConfig

sealed class CustomFilterListEvents {
    data class UndoRemove(val exclusions: Set<CustomFilterConfig>) : CustomFilterListEvents()
}
