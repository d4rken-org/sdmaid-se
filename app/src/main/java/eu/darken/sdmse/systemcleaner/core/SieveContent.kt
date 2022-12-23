package eu.darken.sdmse.systemcleaner.core

import eu.darken.sdmse.common.files.core.APathLookup

data class SieveContent(
    val items: Collection<APathLookup<*>>
) {
    val size: Long
        get() = items.sumOf { it.size }
}