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
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.sieve.TypeCriterium
import eu.darken.sdmse.systemcleaner.core.filter.BaseSystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.sieve.SystemCrawlerSieve

class CustomFilter @AssistedInject constructor(
    @Assisted private val filterConfig: CustomFilterConfig,
    private val systemCrawlerSieveFactory: SystemCrawlerSieve.Factory,
    private val gatewaySwitch: GatewaySwitch,
) : BaseSystemCleanerFilter() {

    override val identifier: FilterIdentifier = filterConfig.identifier

    override suspend fun getIcon(): CaDrawable = R.drawable.air_filter.toCaDrawable()

    override suspend fun getLabel(): CaString = filterConfig.label.toCaString()

    override suspend fun getDescription(): CaString = R.string.systemcleaner_customfilter_label.toCaString()

    override suspend fun targetAreas(): Set<DataArea.Type> = filterConfig.areas ?: DataArea.Type.entries.toSet()

    private lateinit var sieve: SystemCrawlerSieve

    override suspend fun initialize() {
        val sieveConfig = SystemCrawlerSieve.Config(
            areaTypes = targetAreas(),
            targetTypes = filterConfig.fileTypes?.map {
                when (it) {
                    FileType.DIRECTORY -> TypeCriterium.DIRECTORY
                    FileType.SYMBOLIC_LINK -> TypeCriterium.FILE
                    FileType.FILE -> TypeCriterium.FILE
                    FileType.UNKNOWN -> TypeCriterium.FILE
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
        sieve = systemCrawlerSieveFactory.create(sieveConfig)
        log(TAG) { "initialized()" }
    }

    override suspend fun match(item: APathLookup<*>): SystemCleanerFilter.Match? {
        if (!sieve.match(item).matches) return null

        return SystemCleanerFilter.Match.Deletion(item)
    }

    override suspend fun process(
        matches: Collection<SystemCleanerFilter.Match>
    ): Collection<SystemCleanerFilter.Processed> {
        return matches.filterIsInstance<SystemCleanerFilter.Match.Deletion>().deleteAll(gatewaySwitch)
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
