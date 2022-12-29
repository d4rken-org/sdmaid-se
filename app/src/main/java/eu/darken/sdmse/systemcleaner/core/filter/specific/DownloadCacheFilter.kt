package eu.darken.sdmse.systemcleaner.core.filter.specific

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
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APathLookup
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import java.io.File
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
        val basePaths = areaManager.currentAreas()
            .filter { targetAreas().contains(it.type) }
            .map { it.path }
            .toSet()

        val config = BaseSieve.Config(
            targetType = BaseSieve.TargetType.FILE,
            areaTypes = targetAreas(),
            basePaths = basePaths,
            exclusions = setOf(
                "dalvik-cache",
                "lost+found",
                // Some apps use these logs to determine the type of recovery
                "recovery/last_log".replace("/", File.separator),
                "last_postrecovery",
                "last_data_partition_info",
                "last_dataresizing",
            )
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
        private val filterProvider: Provider<DownloadCacheFilter>,
        private val rootManager: RootManager,
    ) : SystemCleanerFilter.Factory {

        override suspend fun isEnabled(): Boolean {
            val enabled = settings.filterDownloadCacheEnabled.value()
            val isRooted = rootManager.isRooted()
            if (enabled && !isRooted) log(TAG, INFO) { "Filter is enabled, but requires root, which is unavailable." }
            return enabled && isRooted
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