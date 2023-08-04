package eu.darken.sdmse.systemcleaner.core.filter.custom

import com.squareup.moshi.Moshi
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomFilterRepo @Inject constructor(
    private val moshi: Moshi,
) {

    val configs: Flow<Collection<CustomFilterConfig>> = flowOf(emptySet())

    suspend fun importFilter(path: APath): CustomFilterConfig {
        TODO()
    }

    suspend fun exportFilter(filterConfig: CustomFilterConfig): ByteArray {
        TODO()
    }

    fun save(configs: Set<CustomFilterConfig>) {
        TODO("Not yet implemented")
    }

    fun remove(ids: Set<FilterIdentifier>) {
        TODO("Not yet implemented")
    }

    fun generateIdentifier() = UUID.randomUUID().toString()

}