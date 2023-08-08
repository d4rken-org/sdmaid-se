package eu.darken.sdmse.systemcleaner.core.filter

import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterLoader
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterRepo
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import javax.inject.Inject

@Reusable
class FilterSource @Inject constructor(
    private val filterFactories: Set<@JvmSuppressWildcards SystemCleanerFilter.Factory>,
    private val customFilterRepo: CustomFilterRepo,
    private val customFilterLoader: CustomFilterLoader.Factory,
) {

    init {
        filterFactories.forEach { log(TAG) { "Available filter: $it" } }
    }

    suspend fun create(): Set<SystemCleanerFilter> {
        val builtInFilters = filterFactories
            .asFlow()
            .filter { it.isEnabled() }
            .map { it.create() }
            .onEach {
                log(TAG) { "Initializing $it" }
                it.initialize()
            }
            .toList()

        val customFilters = customFilterRepo.configs.first()
            .asFlow()
            .map { customFilterLoader.create(it) }
            .filter { it.isEnabled() }
            .map { it.create() }
            .onEach {
                log(TAG) { "Initializing $it" }
                it.initialize()
            }
            .toList()

        return (builtInFilters + customFilters).toSet()
    }


    companion object {
        private val TAG = logTag("SystemCleaner", "FilterSource")
    }
}