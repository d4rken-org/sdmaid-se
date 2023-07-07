package eu.darken.sdmse.appcleaner.core.forensics.filter

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.sieves.json.JsonBasedSieve
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.files.lowercase
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.storage.StorageEnvironment
import java.util.*
import javax.inject.Inject
import javax.inject.Provider


@Reusable
class HiddenFilter @Inject constructor(
    private val jsonBasedSieveFactory: JsonBasedSieve.Factory,
    environment: StorageEnvironment,
) : ExpendablesFilter {

    private val cacheFolderPrefixes = environment.ourCacheDirs.map { it.name }

    private lateinit var sieve: JsonBasedSieve

    override suspend fun initialize() {
        log(TAG) { "initialize()" }
        sieve = jsonBasedSieveFactory.create("expendables/db_hidden_caches_files.json")
    }

    override suspend fun isExpendable(
        pkgId: Pkg.Id,
        target: APathLookup<APath>,
        areaType: DataArea.Type,
        segments: Segments
    ): Boolean {
        if (segments.isNotEmpty() && IGNORED_FILES.contains(segments[segments.size - 1])) {
            return false
        }

        // Default case, we don't handle that.
        // package/cache/file
        if (segments.size >= 2 && pkgId.name == segments[0] && cacheFolderPrefixes.contains(segments[1])) {
            // Case matching is important here as all paths that differ in casing are hidden caches (e.g. not system made)
            return false
        }

        val lcsegments = segments.lowercase()

        // package/cache.dat
        if (lcsegments.size == 2 && HIDDEN_CACHE_FILES.contains(lcsegments[1])) {
            return true
        }

        // package/files/cache.dat
        if (lcsegments.size == 3 && HIDDEN_CACHE_FILES.contains(lcsegments[2])) {
            return true
        }

        //    0      1     2
        // package/.cache/file
        if (lcsegments.size >= 3 && HIDDEN_CACHE_FOLDERS.contains(lcsegments[1])) { // weird name cache folder
            return true
        }
        if (isException(segments)) return false

        //    0      1     2     3
        // package/files/.cache/file
        if (lcsegments.size >= 4
            && "files" == lcsegments[1]
            && (HIDDEN_CACHE_FOLDERS.contains(lcsegments[2]) || "cache" == lcsegments[2])
        ) {
            // cache hidden in files
            return true
        }

        //    -1     0      1     2     3
        // sdcard/Huawei/Themes/.cache/file
        if (lcsegments.size >= 4
            && areaType == DataArea.Type.SDCARD
            && HIDDEN_CACHE_FOLDERS.contains(lcsegments[2])
        ) {
            // cache hidden in files
            return true
        }

        return segments.isNotEmpty() && sieve.matches(pkgId, areaType, segments)
    }

    private fun isException(dirs: Segments): Boolean {
        return dirs.size >= 4 && dirs[2] == "Cache" && dirs[3].contains(".unity3d&")
    }

    @Reusable
    class Factory @Inject constructor(
        private val settings: AppCleanerSettings,
        private val filterProvider: Provider<HiddenFilter>
    ) : ExpendablesFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterHiddenCachesEnabled.value()
        override suspend fun create(): ExpendablesFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): ExpendablesFilter.Factory
    }

    companion object {
        private val HIDDEN_CACHE_FOLDERS: Collection<String> = listOf(
            ".cache",
            "tmp", ".tmp",
            "tmpdata", "tmp-data", "tmp_data",
            ".tmpdata", ".tmp-data", ".tmp_data",
            ".temp", "temp",
            "tempdata", "temp-data", "temp_data",
            ".tempdata", ".temp-data", ".temp_data",
            "cache", "_cache", "-cache",
            "imagecache", "image-cache", "image_cache",
            ".imagecache", ".image-cache", ".image_cache",
            "videocache", "video-cache", "video_cache",
            ".videocache", ".video-cache", ".video_cache",
            "mediacache", "media-cache", "media_cache",
            ".mediacache", ".mediacache", ".media-cache",
            "diskcache", "disk-cache", "disk_cache",
            ".diskcache", ".disk-cache", ".disk_cache",
            "filescache",
            "AVFSCache"
        ).map { it.lowercase() }

        private val HIDDEN_CACHE_FILES: Collection<String> = listOf(
            "cache.dat",
            "tmp.dat",
            "temp.dat",
            ".temp.jpg"
        ).map { it.lowercase() }

        private val IGNORED_FILES: Collection<String> = listOf(
            ".nomedia"
        ).map { it.lowercase() }

        private val TAG = logTag("AppCleaner", "Scanner", "Filter", "HiddenCaches")
    }
}