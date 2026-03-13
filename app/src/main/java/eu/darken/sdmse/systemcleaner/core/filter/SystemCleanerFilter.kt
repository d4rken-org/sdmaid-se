package eu.darken.sdmse.systemcleaner.core.filter

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.ca.CaDrawable
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.progress.Progress
import kotlin.reflect.KClass

interface SystemCleanerFilter : Progress.Host, Progress.Client {

    val identifier: FilterIdentifier
        get() = this::class.qualifiedName!!

    suspend fun getIcon(): CaDrawable

    suspend fun getLabel(): CaString

    suspend fun getDescription(): CaString

    suspend fun targetAreas(): Set<DataArea.Type>

    suspend fun initialize()

    suspend fun match(item: APathLookup<*>): Match?

    suspend fun process(matches: Collection<Match>): Collection<Processed>

    data class Processed(
        val match: Match,
        val error: Throwable?,
    ) {
        val success: Boolean
            get() = error == null
    }

    interface Match {
        val expectedGain: Long
        val lookup: APathLookup<*>

        val path: APath
            get() = lookup.lookedUp

        data class Deletion(
            override val lookup: APathLookup<*>,
        ) : Match {
            override val expectedGain: Long
                get() = lookup.size
        }
    }

    interface Factory {
        suspend fun isEnabled(): Boolean
        suspend fun create(): SystemCleanerFilter
    }
}

val <T : SystemCleanerFilter> KClass<T>.filterIdentifier: FilterIdentifier
    get() = this.qualifiedName!!
