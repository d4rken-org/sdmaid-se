package eu.darken.sdmse.systemcleaner.core.filter.custom

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter

class CustomFilter @AssistedInject constructor(
    @Assisted private val filterConfig: CustomFilterConfig,
    private val baseSieveFactory: BaseSieve.Factory,
    private val areaManager: DataAreaManager,
) : SystemCleanerFilter {

    override suspend fun targetAreas(): Set<DataArea.Type> = filterConfig.areas ?: DataArea.Type.values().toSet()

    private lateinit var sieve: BaseSieve

    override suspend fun initialize() {
        TODO("Init sieve filter?")
        log(TAG) { "initialized()" }
    }

    override suspend fun matches(item: APathLookup<*>): Boolean {
        return sieve.match(item).matches
    }

    override fun toString(): String = "${this::class.simpleName}(${filterConfig.label})"

    @AssistedFactory
    interface Factory {
        fun create(filterConfig: CustomFilterConfig): CustomFilter
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "Filter", "Custom")
    }
}
