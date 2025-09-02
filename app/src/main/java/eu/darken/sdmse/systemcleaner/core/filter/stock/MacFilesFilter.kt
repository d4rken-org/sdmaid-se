package eu.darken.sdmse.systemcleaner.core.filter.stock

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.ca.CaDrawable
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaDrawable
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.sieve.NameCriterium
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.BaseSystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.filter.toDeletion
import eu.darken.sdmse.systemcleaner.core.sieve.SystemCrawlerSieve
import javax.inject.Inject
import javax.inject.Provider

class MacFilesFilter @Inject constructor(
    private val sieveFactory: SystemCrawlerSieve.Factory,
    private val gatewaySwitch: GatewaySwitch,
) : BaseSystemCleanerFilter() {

    override suspend fun getIcon(): CaDrawable = R.drawable.ic_os_mac.toCaDrawable()

    override suspend fun getLabel(): CaString = R.string.systemcleaner_filter_macfiles_label.toCaString()

    override suspend fun getDescription(): CaString = R.string.systemcleaner_filter_macfiles_summary.toCaString()

    override suspend fun targetAreas(): Set<DataArea.Type> = setOf(
        DataArea.Type.SDCARD,
        DataArea.Type.PUBLIC_MEDIA,
        DataArea.Type.PORTABLE,
    )

    private lateinit var sieve: SystemCrawlerSieve

    override suspend fun initialize() {
        val config = SystemCrawlerSieve.Config(
            areaTypes = targetAreas(),
            nameCriteria = setOf(
                NameCriterium("._", mode = NameCriterium.Mode.Start()),
                NameCriterium(".Trashes", mode = NameCriterium.Mode.Equal()),
                NameCriterium("._.Trashes", mode = NameCriterium.Mode.Equal()),
                NameCriterium(".spotlight", mode = NameCriterium.Mode.Equal()),
                NameCriterium(".Spotlight-V100", mode = NameCriterium.Mode.Equal()),
                NameCriterium(".DS_Store", mode = NameCriterium.Mode.Equal()),
                NameCriterium(".fseventsd", mode = NameCriterium.Mode.Equal()),
                NameCriterium(".TemporaryItems", mode = NameCriterium.Mode.Equal()),
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
        private val filterProvider: Provider<MacFilesFilter>
    ) : SystemCleanerFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterMacFilesEnabled.value()
        override suspend fun create(): SystemCleanerFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): SystemCleanerFilter.Factory
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "Filter", "LostDir")
    }
}
