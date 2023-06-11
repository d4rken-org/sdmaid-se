package eu.darken.sdmse.systemcleaner.core.filter.specific

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.useRootNow
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import javax.inject.Inject
import javax.inject.Provider

class DownloadCacheFilter @Inject constructor(
    private val baseSieveFactory: BaseSieve.Factory,
    private val areaManager: DataAreaManager,
) : SystemCleanerFilter {

    override suspend fun targetAreas(): Set<DataArea.Type> = setOf(
        DataArea.Type.DOWNLOAD_CACHE,
    )

    private lateinit var sieve: BaseSieve

    override suspend fun initialize() {
        val config = BaseSieve.Config(
            targetType = BaseSieve.TargetType.FILE,
            areaTypes = targetAreas(),
            exclusions = setOf(
                BaseSieve.Exclusion(segs("dalvik-cache")),
                BaseSieve.Exclusion(segs("lost+found")),
                // Some apps use these logs to determine the type of recovery
                BaseSieve.Exclusion(segs("recovery", "last_log")),
                BaseSieve.Exclusion(segs("last_postrecovery")),
                BaseSieve.Exclusion(segs("last_data_partition_info")),
                BaseSieve.Exclusion(segs("last_dataresizing")),
            )
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
        private val filterProvider: Provider<DownloadCacheFilter>,
        private val rootManager: RootManager,
    ) : SystemCleanerFilter.Factory {

        override suspend fun isEnabled(): Boolean {
            val enabled = settings.filterDownloadCacheEnabled.value()
            val useRoot = rootManager.useRootNow()
            if (enabled && !useRoot) log(TAG, INFO) { "Filter is enabled, but requires root, which is unavailable." }
            return enabled && useRoot
        }

        override suspend fun create(): SystemCleanerFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): SystemCleanerFilter.Factory
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "Filter", "DownloadCache")
    }
}