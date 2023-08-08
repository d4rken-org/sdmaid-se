package eu.darken.sdmse.systemcleaner.core.filter

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.ca.CaDrawable
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.files.APathLookup
import kotlin.reflect.KClass

interface SystemCleanerFilter {

    val identifier: FilterIdentifier
        get() = this::class.qualifiedName!!

    suspend fun getIcon(): CaDrawable

    suspend fun getLabel(): CaString

    suspend fun getDescription(): CaString

    suspend fun targetAreas(): Set<DataArea.Type>

    suspend fun initialize()

    suspend fun matches(item: APathLookup<*>): Boolean

    interface Factory {
        suspend fun isEnabled(): Boolean
        suspend fun create(): SystemCleanerFilter
    }
}

val <T : SystemCleanerFilter> KClass<T>.filterIdentifier: FilterIdentifier
    get() = this.qualifiedName!!
