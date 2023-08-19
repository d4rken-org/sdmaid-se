package eu.darken.sdmse.systemcleaner.core.filter.custom

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.R
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.ca.CaDrawable
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaDrawable
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve

class CustomFilter @AssistedInject constructor(
    @Assisted private val filterConfig: CustomFilterConfig,
    private val baseSieveFactory: BaseSieve.Factory,
) : SystemCleanerFilter {

    override val identifier: FilterIdentifier = filterConfig.identifier

    override suspend fun getIcon(): CaDrawable = R.drawable.air_filter.toCaDrawable()

    override suspend fun getLabel(): CaString = filterConfig.label.toCaString()

    override suspend fun getDescription(): CaString = R.string.systemcleaner_customfilter_label.toCaString()

    override suspend fun targetAreas(): Set<DataArea.Type> = filterConfig.areas ?: DataArea.Type.values().toSet()

    private lateinit var sieve: BaseSieve

    override suspend fun initialize() {
        val sieveConfig = BaseSieve.Config(
            areaTypes = targetAreas(),
            targetTypes = filterConfig.fileTypes?.map {
                when (it) {
                    FileType.DIRECTORY -> BaseSieve.TargetType.DIRECTORY
                    FileType.SYMBOLIC_LINK -> BaseSieve.TargetType.FILE
                    FileType.FILE -> BaseSieve.TargetType.FILE
                    FileType.UNKNOWN -> BaseSieve.TargetType.FILE
                }
            }?.toSet(),
            pathCriteria = filterConfig.pathCriteria,
            nameCriteria = filterConfig.nameCriteria,
            pathExclusions = filterConfig.exclusionCriteria,
            minimumSize = filterConfig.sizeMinimum,
            maximumSize = filterConfig.sizeMaximum,
            minimumAge = filterConfig.ageMinimum,
            maximumAge = filterConfig.ageMaximum,
            pathRegexes = filterConfig.pathRegexes,
        )
        sieve = baseSieveFactory.create(sieveConfig)
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
