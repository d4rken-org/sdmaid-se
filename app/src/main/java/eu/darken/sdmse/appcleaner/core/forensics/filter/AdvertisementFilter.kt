package eu.darken.sdmse.appcleaner.core.forensics.filter

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.appcleaner.core.forensics.BaseExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.sieves.JsonAppSieve
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.files.isAncestorOf
import eu.darken.sdmse.common.files.lowercase
import eu.darken.sdmse.common.files.toSegs
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.storage.StorageEnvironment
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class AdvertisementFilter @Inject constructor(
    private val jsonBasedSieveFactory: JsonAppSieve.Factory,
    environment: StorageEnvironment,
    private val gatewaySwitch: GatewaySwitch,
) : BaseExpendablesFilter() {

    private val cacheFolderPrefixes = environment.ourCacheDirs.map { it.name }

    private lateinit var sieve: JsonAppSieve

    override suspend fun initialize() {
        log(TAG) { "initialize()" }
        sieve = jsonBasedSieveFactory.create("expendables/db_advertisement_files.json")
    }

    override suspend fun match(
        pkgId: Pkg.Id,
        target: APathLookup<APath>,
        areaType: DataArea.Type,
        pfpSegs: Segments
    ): ExpendablesFilter.Match? {
        val lcsegments = pfpSegs.lowercase()

        // Default case, we don't handle that.
        // pkg/cache/file
        if (lcsegments.size >= 2
            && BLACKLIST_AREAS.contains(areaType)
            && pkgId.name == lcsegments[0]
            && cacheFolderPrefixes.contains(lcsegments[1])
        ) {
            return null
        }

        if (lcsegments.isNotEmpty() && IGNORED_FILES.contains(lcsegments[lcsegments.size - 1])) {
            return null
        }

        // topdir/cache.dat
        if (lcsegments.size == 2 && HIDDEN_CACHE_FILES.contains(lcsegments[1])) {
            return target.toDeletionMatch()
        }

        //    0       1      2
        // topdir/files/cache.dat
        if (lcsegments.size == 3 && HIDDEN_CACHE_FILES.contains(lcsegments[2])) {
            return target.toDeletionMatch()
        }

        //    0       1       2
        // topdir/adcache/...
        if (lcsegments.size >= 3 && HIDDEN_CACHE_FOLDERS.contains(lcsegments[1])) {
            return target.toDeletionMatch()
        }

        //    0      1      2      3
        // topdir/files/adcache/...
        if (lcsegments.size >= 4 && "files" == lcsegments[1] && HIDDEN_CACHE_FOLDERS.contains(lcsegments[2])) {
            return target.toDeletionMatch()
        }

        //  drop    0      1      2
        // topdir/files/adcache/...
        val subDir = lcsegments.drop(1)
        if (HIDDEN_CACHE_SEGS.any { it.isAncestorOf(subDir) }) {
            return target.toDeletionMatch()
        }

        return if (pfpSegs.isNotEmpty() && sieve.matches(pkgId, areaType, pfpSegs)) {
            target.toDeletionMatch()
        } else {
            null
        }
    }

    override suspend fun process(
        targets: Collection<ExpendablesFilter.Match>,
        allMatches: Collection<ExpendablesFilter.Match>
    ): ExpendablesFilter.ProcessResult {
        return deleteAll(
            targets.map { it as ExpendablesFilter.Match.Deletion },
            gatewaySwitch,
            allMatches
        )
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
        private val BLACKLIST_AREAS = setOf(
            DataArea.Type.PRIVATE_DATA,
            DataArea.Type.PUBLIC_DATA,
        )
        private val IGNORED_FILES: Collection<String> = listOf(
            ".nomedia",
        )
        private val HIDDEN_CACHE_FOLDERS: Collection<String> = listOf(
            "vast_rtb_cache",
            "GoAdSdk",
            "IFlyAdImgCache",
        ).lowercase()

        private val HIDDEN_CACHE_SEGS: Collection<Segments> = listOf(
            "files/mb/res/.mbridge".toSegs()
        )
        private val HIDDEN_CACHE_FILES: Collection<String> = listOf(

        )
    }
}