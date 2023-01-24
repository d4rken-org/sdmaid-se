package eu.darken.sdmse.systemcleaner.core.filter

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.core.APathLookup

interface SystemCleanerFilter {

    val filterIdentifier: FilterIdentifier
        get() = this::class.qualifiedName!!

    suspend fun targetAreas(): Set<DataArea.Type>

    suspend fun initialize()

    suspend fun matches(item: APathLookup<*>): Boolean

    interface Factory {
        suspend fun isEnabled(): Boolean
        suspend fun create(): SystemCleanerFilter
    }
}
