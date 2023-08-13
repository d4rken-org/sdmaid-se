package eu.darken.sdmse.systemcleaner.core.filter.stock

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.ca.CaDrawable
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaDrawable
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import javax.inject.Inject
import javax.inject.Provider

class MacFilesFilter @Inject constructor(
    private val baseSieveFactory: BaseSieve.Factory,
    private val areaManager: DataAreaManager,
) : SystemCleanerFilter {

    override suspend fun getIcon(): CaDrawable = R.drawable.ic_os_mac.toCaDrawable()

    override suspend fun getLabel(): CaString = R.string.systemcleaner_filter_macfiles_label.toCaString()

    override suspend fun getDescription(): CaString = R.string.systemcleaner_filter_macfiles_summary.toCaString()

    override suspend fun targetAreas(): Set<DataArea.Type> = setOf(
        DataArea.Type.SDCARD,
        DataArea.Type.PUBLIC_MEDIA,
        DataArea.Type.PORTABLE,
    )

    private lateinit var sieve: BaseSieve

    override suspend fun initialize() {
        val config = BaseSieve.Config(
            areaTypes = targetAreas(),
            nameCriteria = setOf(
                BaseSieve.NameCriterium("._", mode = BaseSieve.Criterium.Mode.START),
                BaseSieve.NameCriterium(".Trashes", mode = BaseSieve.Criterium.Mode.MATCH),
                BaseSieve.NameCriterium("._.Trashes", mode = BaseSieve.Criterium.Mode.MATCH),
                BaseSieve.NameCriterium(".spotlight", mode = BaseSieve.Criterium.Mode.MATCH),
                BaseSieve.NameCriterium(".Spotlight-V100", mode = BaseSieve.Criterium.Mode.MATCH),
                BaseSieve.NameCriterium(".DS_Store", mode = BaseSieve.Criterium.Mode.MATCH),
                BaseSieve.NameCriterium(".fseventsd", mode = BaseSieve.Criterium.Mode.MATCH),
                BaseSieve.NameCriterium(".TemporaryItems", mode = BaseSieve.Criterium.Mode.MATCH),
            ),
        )
        sieve = baseSieveFactory.create(config)
        log(TAG) { "initialized()" }
    }


    override suspend fun matches(item: APathLookup<*>): Boolean {
        return sieve.match(item).matches
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
