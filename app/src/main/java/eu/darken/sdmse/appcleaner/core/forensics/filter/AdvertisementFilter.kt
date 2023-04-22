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
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class AdvertisementFilter @Inject constructor(
    private val jsonBasedSieveFactory: JsonBasedSieve.Factory,
    environment: StorageEnvironment,
) : ExpendablesFilter {

    private val cacheFolderPrefixes = environment.ourCacheDirs.map { it.name }

    private lateinit var sieve: JsonBasedSieve

    override suspend fun initialize() {
        log(TAG) { "initialize()" }
        sieve = jsonBasedSieveFactory.create("expendables/db_advertisement_files.json")
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

        //    0       1      2
        // package/files/cache.dat
        if (lcsegments.size == 3 && HIDDEN_CACHE_FILES.contains(lcsegments[2])) {
            return true
        }

        //    0       1       2
        // package/adcache/...
        if (lcsegments.size >= 3 && HIDDEN_CACHE_FOLDERS.contains(lcsegments[1])) {
            return true
        }

        //    0      1      2      3
        // package/files/adcache/...
        if (lcsegments.size >= 4 && "files" == lcsegments[1] && HIDDEN_CACHE_FOLDERS.contains(lcsegments[2])) {
            return true
        }

        return segments.isNotEmpty() && sieve.matches(pkgId, areaType, segments)
    }

    @Reusable
    class Factory @Inject constructor(
        private val settings: AppCleanerSettings,
        private val filterProvider: Provider<AdvertisementFilter>
    ) : ExpendablesFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterAdvertisementEnabled.value()
        override suspend fun create(): ExpendablesFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): ExpendablesFilter.Factory
    }

    companion object {
        private val TAG = logTag("AppCleaner", "Scanner", "Filter", "Advertisements")
        private val IGNORED_FILES: Collection<String> = listOf(
            ".nomedia",
        )
        private val HIDDEN_CACHE_FOLDERS: Collection<String> = listOf(
            "vast_rtb_cache",
            "GoAdSdk",
        ).lowercase()
        private val HIDDEN_CACHE_FILES: Collection<String> = listOf(

        )
    }
}