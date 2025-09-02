package eu.darken.sdmse.systemcleaner.core.filter.stock

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.modules.misc.PortableModule
import eu.darken.sdmse.common.areas.modules.pub.SdcardsModule
import eu.darken.sdmse.common.ca.CaDrawable
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaDrawable
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.sieve.NameCriterium
import eu.darken.sdmse.common.sieve.SegmentCriterium
import eu.darken.sdmse.common.sieve.TypeCriterium
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.BaseSystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.filter.toDeletion
import eu.darken.sdmse.systemcleaner.core.sieve.SystemCrawlerSieve
import javax.inject.Inject
import javax.inject.Provider

class TempFilesFilter @Inject constructor(
    private val sieveFactory: SystemCrawlerSieve.Factory,
    private val gatewaySwitch: GatewaySwitch,
) : BaseSystemCleanerFilter() {

    override suspend fun getIcon(): CaDrawable = R.drawable.ic_baseline_access_time_filled_24.toCaDrawable()

    override suspend fun getLabel(): CaString = R.string.systemcleaner_filter_tempfiles_label.toCaString()

    override suspend fun getDescription(): CaString = R.string.systemcleaner_filter_tempfiles_summary.toCaString()

    override suspend fun targetAreas(): Set<DataArea.Type> = setOf(
        DataArea.Type.SDCARD,
        DataArea.Type.DATA,
        DataArea.Type.DATA_SYSTEM,
        DataArea.Type.DATA_SYSTEM_DE,
    )

    private lateinit var sieve: SystemCrawlerSieve

    override suspend fun initialize() {
        val config = SystemCrawlerSieve.Config(
            targetTypes = setOf(TypeCriterium.FILE),
            areaTypes = targetAreas(),
            nameCriteria = setOf(
                NameCriterium(".tmp", mode = NameCriterium.Mode.End()),
                NameCriterium(".temp", mode = NameCriterium.Mode.End()),
                NameCriterium(".mmsyscache", mode = NameCriterium.Mode.Equal()),
                NameCriterium("sdm_write_test-", mode = NameCriterium.Mode.Start()),
                NameCriterium(SdcardsModule.TEST_PREFIX, mode = NameCriterium.Mode.Start()),
                NameCriterium(PortableModule.TEST_PREFIX, mode = NameCriterium.Mode.Start()),
            ),
            pathExclusions = setOf(
                SegmentCriterium(segs("backup", "pending"), mode = SegmentCriterium.Mode.Ancestor()),
                SegmentCriterium(segs("cache", "recovery"), mode = SegmentCriterium.Mode.Ancestor()),
            ),
        )

        sieve = sieveFactory.create(config)
        log(TAG) { "initialized() with $config" }
    }

    override suspend fun match(item: APathLookup<*>): SystemCleanerFilter.Match? {
        return sieve.match(item).toDeletion()
    }

    override suspend fun process(
        matches: Collection<SystemCleanerFilter.Match>
    ): Collection<SystemCleanerFilter.Processed> {
        return matches.filterIsInstance<SystemCleanerFilter.Match.Deletion>().deleteAll(gatewaySwitch)
    }

    override fun toString(): String = "${this::class.simpleName}(${hashCode()})"

    @Reusable
    class Factory @Inject constructor(
        private val settings: SystemCleanerSettings,
        private val filterProvider: Provider<TempFilesFilter>
    ) : SystemCleanerFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterTempFilesEnabled.value()
        override suspend fun create(): SystemCleanerFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): SystemCleanerFilter.Factory
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "Filter", "TempFiles")
    }
}
