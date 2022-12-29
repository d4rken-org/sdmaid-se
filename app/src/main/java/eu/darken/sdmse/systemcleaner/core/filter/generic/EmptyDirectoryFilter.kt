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
import eu.darken.sdmse.common.files.core.*
import eu.darken.sdmse.common.files.core.local.fixSlass
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import javax.inject.Inject
import javax.inject.Provider


class EmptyDirectoryFilter @Inject constructor(
    private val baseSieveFactory: BaseSieve.Factory,
    private val areaManager: DataAreaManager,
    private val gatewaySwitch: GatewaySwitch,
) : SystemCleanerFilter {

    override suspend fun targetAreas(): Set<DataArea.Type> = setOf(
        DataArea.Type.SDCARD,
        DataArea.Type.PUBLIC_DATA,
        DataArea.Type.PUBLIC_MEDIA,
    )

    private lateinit var sieve: BaseSieve
    private lateinit var protectedAreaPaths: Set<APath>
    private lateinit var protectedDefaults: Set<APath>

    override suspend fun initialize() {
        val areas = areaManager.currentAreas()

        protectedDefaults = areas
            .map {
                setOf(
                    it.path.child("Camera"),
                    it.path.child("Photos"),
                    it.path.child("Music"),
                    it.path.child("DCIM"),
                    it.path.child("Pictures"),
                )
            }
            .flatten()
            .toSet()

        val config = BaseSieve.Config(
            targetType = BaseSieve.TargetType.DIRECTORY,
            areaTypes = targetAreas(),
            exclusions = setOf(
                "/mnt/asec".fixSlass(),
                "/mnt/obb".fixSlass(),
                "/mnt/secure".fixSlass(),
                "/mnt/shell".fixSlass(),
                "/Android/obb".fixSlass(),
                "/.stfolder".fixSlass(),
            ),
        )
        sieve = baseSieveFactory.create(config)

        protectedAreaPaths = areas
            .filter {
                setOf(
                    DataArea.Type.PUBLIC_MEDIA,
                    DataArea.Type.PUBLIC_DATA,
                ).contains(it.type)
            }
            .map { it.path }
            .toSet()


        log(TAG) { "initialized()" }
    }

    override suspend fun sieve(item: APathLookup<*>): Boolean {
        if (!sieve.match(item)) return false
        log(TAG) { "Sieve match: ${item.path}" }
        if (protectedAreaPaths.any { it.matches(item) }) return false

        if (protectedDefaults.any { it.matches(item) }) return false

        // Exclude toplvl package folders in Android/data
        if (protectedAreaPaths.any { it.matches(item) || it.isParentOf(item) }) return false

        // Exclude Android/data/<pkg>/files
        if (item.name == "cache" || item.name == "files") {
            if (protectedAreaPaths.any { it.segments == item.segments.drop(2) }) return false
        }

        if (item.size > 4096) return false

        // Check for nested empty directories
        val content = item.lookupFiles(gatewaySwitch)
        if (content.size > 2) return false
        if (content.any {
                val match = sieve(it)
                if (!match) log(TAG) { "Failed sub sieve match: $it" }
                !match
            }) return false

        return content.isEmpty()
    }

    override fun toString(): String = "${this::class.simpleName}(${hashCode()})"

    @Reusable
    class Factory @Inject constructor(
        private val settings: SystemCleanerSettings,
        private val filterProvider: Provider<EmptyDirectoryFilter>
    ) : SystemCleanerFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterEmptyDirectoriesEnabled.value()
        override suspend fun create(): SystemCleanerFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): SystemCleanerFilter.Factory
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "Filter", "EmptyDirectories")
    }
}
