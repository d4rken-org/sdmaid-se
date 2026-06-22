package eu.darken.sdmse.systemcleaner.core

import androidx.compose.ui.graphics.vector.ImageVector
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter

data class FilterContent(
    val identifier: FilterIdentifier,
    val icon: ImageVector,
    val label: CaString,
    val description: CaString,
    val items: Collection<SystemCleanerFilter.Match>
) {
    val size: Long
        get() = items.sumOf { it.lookup.size }
}
