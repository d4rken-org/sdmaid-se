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
import eu.darken.sdmse.common.files.*
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
    private val protectedBaseDirs = setOf(
        segs("Camera"),
        segs("Photos"),
        segs("Music"),
        segs("DCIM"),
        segs("Pictures"),
        segs("Movies"),
        segs("Recordings"),
        segs("Video"),
        segs("Download"),
        segs("Audiobooks"),
        segs("Documents"),
        segs("Alarms"),
        segs("Ringtones"),
        segs("Notifications"),
        segs("Podcasts"),
        segs("Android", "data"),
        segs("Android", "media"),
        segs("Android", "obb"),
    )

    private val pkgAreas = setOf(
        DataArea.Type.PUBLIC_DATA,
        DataArea.Type.PUBLIC_MEDIA,
        DataArea.Type.PUBLIC_OBB,
    )

    private val protectedSubDirs = setOf(
        "files",
        "cache",
    )

    override suspend fun initialize() {
        val config = BaseSieve.Config(
            targetType = BaseSieve.TargetType.DIRECTORY,
            areaTypes = targetAreas(),
            exclusions = setOf(
                BaseSieve.Exclusion(segs("mnt", "asec")),
                BaseSieve.Exclusion(segs("mnt", "obb")),
                BaseSieve.Exclusion(segs("mnt", "secure")),
                BaseSieve.Exclusion(segs("mnt", "shell")),
                BaseSieve.Exclusion(segs("Android", "obb")),
                BaseSieve.Exclusion(segs(".stfolder")),
            ),
        )
        sieve = baseSieveFactory.create(config)

        log(TAG) { "initialized()" }
    }

    override suspend fun matches(item: APathLookup<*>): Boolean {
        val sieveResult = sieve.match(item)
        if (!sieveResult.matches) return false

        log(TAG) { "Sieve match: ${item.path}" }

        val areaInfo = sieveResult.areaInfo!!
        val prefixFreePath = areaInfo.prefixFreePath
        if (prefixFreePath.isEmpty()) return false

        if (protectedBaseDirs.any { it.matches(prefixFreePath) }) return false

        // Exclude toplvl package folders in Android/data
        if (pkgAreas.contains(areaInfo.type) && prefixFreePath.size == 1) return false

        // Exclude Android/.../<pkg>/files
        if (pkgAreas.contains(areaInfo.type) && prefixFreePath.size == 2 && protectedSubDirs.contains(prefixFreePath[1])) {
            return false
        }

        if (item.size > 4096) return false

        // Check for nested empty directories
        val content = item.lookupFiles(gatewaySwitch)
        if (content.size > 2) return false
        if (content.any {
                val match = matches(it)
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
