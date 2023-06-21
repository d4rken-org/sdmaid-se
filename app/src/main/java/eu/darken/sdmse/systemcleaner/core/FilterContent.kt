package eu.darken.sdmse.systemcleaner.core

import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier

data class FilterContent(
    val identifier: FilterIdentifier,
    val items: Collection<APathLookup<*>>
) {
    val size: Long
        get() = items.sumOf { it.size }
}