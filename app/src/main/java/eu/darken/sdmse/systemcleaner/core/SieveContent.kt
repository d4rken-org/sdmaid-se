package eu.darken.sdmse.systemcleaner.core

import eu.darken.sdmse.common.files.core.APathLookup
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier

data class SieveContent(
    val filterIdentifier: FilterIdentifier,
    val items: Collection<APathLookup<*>>
) {
    val size: Long
        get() = items.sumOf { it.size }
}