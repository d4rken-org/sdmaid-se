package eu.darken.sdmse.systemcleaner.core.filter

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.core.APathLookup

interface SystemCleanerFilter {

    suspend fun targetAreas(): Set<DataArea.Type>

    suspend fun initialize()

    suspend fun sieve(item: APathLookup<*>): Boolean

    interface Factory {
        suspend fun isEnabled(): Boolean
        suspend fun create(): SystemCleanerFilter
    }
}