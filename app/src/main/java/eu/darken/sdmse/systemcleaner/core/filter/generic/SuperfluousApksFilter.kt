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
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APathLookup
import eu.darken.sdmse.common.files.core.segs
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import javax.inject.Inject
import javax.inject.Provider

class SuperfluousApksFilter @Inject constructor(
    private val baseSieveFactory: BaseSieve.Factory,
    private val pkgOps: PkgOps,
    private val pkgRepo: PkgRepo,
    private val areaManager: DataAreaManager,
) : SystemCleanerFilter {

    override suspend fun targetAreas(): Set<DataArea.Type> = setOf(DataArea.Type.SDCARD)

    private lateinit var sieve: BaseSieve

    override suspend fun initialize() {
        val config = BaseSieve.Config(
            targetType = BaseSieve.TargetType.FILE,
            areaTypes = targetAreas(),
            nameSuffixes = setOf(".apk"),
            exclusions = EXCLUSIONS
        )
        sieve = baseSieveFactory.create(config)
        log(TAG) { "initialized()" }
    }


    override suspend fun matches(item: APathLookup<*>): Boolean {
        val sieveResult = sieve.match(item)
        if (!sieveResult.matches) return false
        log(TAG, VERBOSE) { "Passed sieve, checking $item" }

        val apkInfo = pkgOps.viewArchive(item) ?: return false
        val installed = pkgRepo.getPkg(apkInfo.id) ?: return false

        return installed.versionCode >= apkInfo.versionCode
    }

    override fun toString(): String = "${this::class.simpleName}(${hashCode()})"

    @Reusable
    class Factory @Inject constructor(
        private val settings: SystemCleanerSettings,
        private val filterProvider: Provider<SuperfluousApksFilter>
    ) : SystemCleanerFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterSuperfluosApksEnabled.value()
        override suspend fun create(): SystemCleanerFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): SystemCleanerFilter.Factory
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "Filter", "SuperfluousApks")
        val EXCLUSIONS = setOf(
            BaseSieve.Exclusion(segs("Backup")),
            BaseSieve.Exclusion(segs("Backups")),
            BaseSieve.Exclusion(segs("Recover")),
            BaseSieve.Exclusion(segs("Recovery")),
            BaseSieve.Exclusion(segs("TWRP")),
        )
    }
}