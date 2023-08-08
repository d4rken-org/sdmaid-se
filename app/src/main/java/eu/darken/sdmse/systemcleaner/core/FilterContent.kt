package eu.darken.sdmse.systemcleaner.core

import eu.darken.sdmse.common.ca.CaDrawable
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier

data class FilterContent(
    val identifier: FilterIdentifier,
    val icon: CaDrawable,
    val label: CaString,
    val description: CaString,
    val items: Collection<APathLookup<*>>
) {
    val size: Long
        get() = items.sumOf { it.size }
}