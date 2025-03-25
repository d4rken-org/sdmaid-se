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
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.lookupFiles
import eu.darken.sdmse.common.files.matches
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.sieve.SegmentCriterium
import eu.darken.sdmse.common.sieve.SegmentCriterium.Mode
import eu.darken.sdmse.common.sieve.TypeCriterium
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.BaseSystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.sieve.SystemCrawlerSieve
import javax.inject.Inject
import javax.inject.Provider


class EmptyDirectoryFilter @Inject constructor(
    private val sieveFactory: SystemCrawlerSieve.Factory,
    private val gatewaySwitch: GatewaySwitch,
) : BaseSystemCleanerFilter() {

    override suspend fun getIcon(): CaDrawable = R.drawable.ic_baseline_folder_open_24.toCaDrawable()

    override suspend fun getLabel(): CaString = R.string.systemcleaner_filter_emptydirectories_label.toCaString()

    override suspend fun getDescription(): CaString =
        R.string.systemcleaner_filter_emptydirectories_summary.toCaString()

    override suspend fun targetAreas(): Set<DataArea.Type> = setOf(
        DataArea.Type.SDCARD,
        DataArea.Type.PUBLIC_DATA,
        DataArea.Type.PUBLIC_MEDIA,
        DataArea.Type.PORTABLE,
    )

    private lateinit var sieve: SystemCrawlerSieve
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
        val config = SystemCrawlerSieve.Config(
            targetTypes = setOf(TypeCriterium.DIRECTORY),
            areaTypes = targetAreas(),
            pathExclusions = setOf(
                SegmentCriterium(segs("mnt", "asec"), mode = Mode.Contain()),
                SegmentCriterium(segs("mnt", "obb"), mode = Mode.Contain()),
                SegmentCriterium(segs("mnt", "secure"), mode = Mode.Contain()),
                SegmentCriterium(segs("mnt", "shell"), mode = Mode.Contain()),
                SegmentCriterium(segs("Android", "obb"), mode = Mode.Contain()),
                SegmentCriterium(segs(".stfolder"), mode = Mode.Contain()),
            ),
        )
        sieve = sieveFactory.create(config)

        log(TAG) { "initialized() with $config" }
    }

    override suspend fun match(item: APathLookup<*>): SystemCleanerFilter.Match? {
        val sieveResult = sieve.match(item)
        if (!sieveResult.matches) return null

        if (Bugs.isTrace) log(TAG, VERBOSE) { "Sieve match: ${item.path}" }

        val areaInfo = sieveResult.areaInfo!!
        val prefixFreePath = areaInfo.prefixFreeSegments
        if (prefixFreePath.isEmpty()) return null

        if (protectedBaseDirs.any { it.matches(prefixFreePath, ignoreCase = true) }) return null

        // Exclude toplvl package folders in Android/data
        if (pkgAreas.contains(areaInfo.type) && prefixFreePath.size == 1) return null

        // Exclude Android/.../<pkg>/files
        if (pkgAreas.contains(areaInfo.type) && prefixFreePath.size == 2 && protectedSubDirs.contains(prefixFreePath[1])) {
            return null
        }

        // Check for nested empty directories
        val content = item.lookupFiles(gatewaySwitch)
        return when {
            content.isEmpty() -> SystemCleanerFilter.Match.Deletion(item)
            content.any { it.fileType != FileType.DIRECTORY } -> null
            else -> if (content.all { match(it) != null }) SystemCleanerFilter.Match.Deletion(item) else null
        }
    }

    override suspend fun process(matches: Collection<SystemCleanerFilter.Match>) {
        matches.deleteAll(gatewaySwitch)
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
