package eu.darken.sdmse.systemcleaner.core.filter.generic

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APathLookup
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import javax.inject.Inject
import javax.inject.Provider

class WindowsFilesFilter @Inject constructor(
    private val baseSieveFactory: BaseSieve.Factory,
    private val areaManager: DataAreaManager,
) : SystemCleanerFilter {

    override suspend fun targetAreas(): Set<DataArea.Type> = setOf(
        DataArea.Type.SDCARD,
        DataArea.Type.PUBLIC_DATA,
        DataArea.Type.PUBLIC_MEDIA,
        DataArea.Type.PUBLIC_OBB,
        DataArea.Type.PORTABLE,
    )

    private lateinit var sieve: BaseSieve

    override suspend fun initialize() {
        val regexes = setOf(
            Regex("^[\\W\\w]+/desktop\\.ini$", RegexOption.IGNORE_CASE),
            Regex("^[\\W\\w]+/thumbs\\.db$", RegexOption.IGNORE_CASE)
        )
        val config = BaseSieve.Config(
            targetType = BaseSieve.TargetType.FILE,
            areaTypes = targetAreas(),
            regexes = regexes,
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
        private val filterProvider: Provider<WindowsFilesFilter>
    ) : SystemCleanerFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterWindowsFilesEnabled.value()
        override suspend fun create(): SystemCleanerFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): SystemCleanerFilter.Factory
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "Filter", "WindowsFiles")
    }
}