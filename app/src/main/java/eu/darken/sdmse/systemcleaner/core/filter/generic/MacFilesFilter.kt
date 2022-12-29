package eu.darken.sdmse.systemcleaner.core.filter.generic

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APathLookup
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import java.io.File
import javax.inject.Inject
import javax.inject.Provider

class MacFilesFilter @Inject constructor(
    private val baseSieveFactory: BaseSieve.Factory,
    private val areaManager: DataAreaManager,
) : SystemCleanerFilter {

    override suspend fun targetAreas(): Set<DataArea.Type> = setOf(
        DataArea.Type.SDCARD,
        DataArea.Type.PUBLIC_MEDIA,
        DataArea.Type.PORTABLE,
    )

    private lateinit var sieve: BaseSieve

    override suspend fun initialize() {
        val basePaths = areaManager.currentAreas()
            .filter { targetAreas().contains(it.type) }
            .map { it.path }
            .toSet()
        val regexes = setOf(
            Regex("^(?:[\\W\\w]+/\\._[^/]+)$".replace("/", "\\" + File.separator)),
            Regex("^(?:[\\W\\w]+/\\.Trashes)$".replace("/", "\\" + File.separator)),
            Regex("^(?:[\\W\\w]+/\\._\\.Trashes)$".replace("/", "\\" + File.separator)),
            Regex("^(?:[\\W\\w]+/\\.spotlight)$".replace("/", "\\" + File.separator)),
            Regex("^(?:[\\W\\w]+/\\.Spotlight-V100)$".replace("/", "\\" + File.separator)),
            Regex("^(?:[\\W\\w]+/\\.DS_Store)$".replace("/", "\\" + File.separator)),
            Regex("^(?:[\\W\\w]+/\\.fseventsd)$".replace("/", "\\" + File.separator)),
            Regex("^(?:[\\W\\w]+/\\.TemporaryItems)$".replace("/", "\\" + File.separator)),
        )
        val config = BaseSieve.Config(
            areaTypes = targetAreas(),
            basePaths = basePaths,
            regexes = regexes,
        )
        sieve = baseSieveFactory.create(config)
        log(TAG) { "initialized()" }
    }


    override suspend fun sieve(item: APathLookup<*>): Boolean {
        return sieve.match(item)
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
