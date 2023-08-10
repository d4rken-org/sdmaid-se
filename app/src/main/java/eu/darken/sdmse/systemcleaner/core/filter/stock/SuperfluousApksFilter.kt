package eu.darken.sdmse.systemcleaner.core.filter.stock

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.ca.CaDrawable
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaDrawable
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.getPkg
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.BaseSieve.NameCriterium
import eu.darken.sdmse.systemcleaner.core.BaseSieve.SegmentCriterium
import eu.darken.sdmse.systemcleaner.core.BaseSieve.SegmentCriterium.*
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

    override suspend fun getIcon(): CaDrawable = R.drawable.ic_app_extra_24.toCaDrawable()

    override suspend fun getLabel(): CaString = R.string.systemcleaner_filter_superfluosapks_label.toCaString()

    override suspend fun getDescription(): CaString = R.string.systemcleaner_filter_superfluosapks_summary.toCaString()

    override suspend fun targetAreas(): Set<DataArea.Type> = setOf(DataArea.Type.SDCARD)

    private lateinit var sieve: BaseSieve

    override suspend fun initialize() {
        val config = BaseSieve.Config(
            targetTypes = setOf(BaseSieve.TargetType.FILE),
            areaTypes = targetAreas(),
            nameCriteria = setOf(NameCriterium(".apk", type = NameCriterium.Type.ENDS_WITH)),
            exclusions = EXCLUSIONS
        )
        sieve = baseSieveFactory.create(config)
        log(TAG) { "initialized()" }
    }


    override suspend fun matches(item: APathLookup<*>): Boolean {
        val sieveResult = sieve.match(item)
        if (!sieveResult.matches) return false
        log(TAG, VERBOSE) { "Passed sieve, checking $item" }

        val apkInfo = pkgOps.viewArchive(item.lookedUp) ?: return false
        // TODO Multiple profiles can't have different versions of the same APK, right?
        val installed = pkgRepo.getPkg(apkInfo.id).firstOrNull() ?: return false

        val superfluos = installed.versionCode >= apkInfo.versionCode
        if (superfluos) {
            log(TAG, VERBOSE) {
                "Superfluos: ${installed.packageName} installed=${installed.versionCode}, archive=${apkInfo.versionCode}"
            }
        }
        return superfluos
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
            SegmentCriterium(segs("Backup"), type = Type.CONTAINS),
            SegmentCriterium(segs("Backups"), type = Type.CONTAINS),
            SegmentCriterium(segs("Recover"), type = Type.CONTAINS),
            SegmentCriterium(segs("Recovery"), type = Type.CONTAINS),
            SegmentCriterium(segs("TWRP"), type = Type.CONTAINS),
        )
    }
}