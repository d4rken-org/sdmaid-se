package eu.darken.sdmse.systemcleaner.core.filter.custom

import com.squareup.moshi.Moshi
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomFilterRepo @Inject constructor(
    private val moshi: Moshi,
    private val settings: SystemCleanerSettings,
) {

    val configs: Flow<Collection<CustomFilterConfig>> = flowOf(
        setOf(
            CustomFilterConfig(
                label = "filter1",
                identifier = generateIdentifier()
            ),
            CustomFilterConfig(
                label = "filter2",
                identifier = generateIdentifier()
            ),
            CustomFilterConfig(
                label = "filter3",
                identifier = generateIdentifier()
            ),
        )
    )

    suspend fun importFilter(path: APath): CustomFilterConfig {
        TODO()
    }

    suspend fun exportFilter(filterConfig: CustomFilterConfig): ByteArray {
        TODO()
    }

    suspend fun save(configs: Set<CustomFilterConfig>) {
        log(TAG) { "save($configs)" }
        TODO("Not yet implemented")
    }

    suspend fun remove(ids: Set<FilterIdentifier>) {
        log(TAG) { "remove($ids)" }
        ids.forEach { id ->
            // TODO deflete json from storage
            settings.clearCustomFilter(id)
        }
    }

    fun generateIdentifier() = UUID.randomUUID().toString()


    companion object {
        private val TAG = logTag("SystemCleaner", "CustomFilter", "Repo")
    }
}