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
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.pkgs.Pkg
import javax.inject.Inject
import javax.inject.Provider


@Reusable
class RecycleBinsFilter @Inject constructor(
    private val jsonBasedSieveFactory: JsonAppSieve.Factory,
    private val gatewaySwitch: GatewaySwitch,
) : BaseExpendablesFilter() {

    private lateinit var sieve: JsonAppSieve

    override suspend fun initialize() {
        log(TAG) { "initialize()" }
        sieve = jsonBasedSieveFactory.create("expendables/db_trash_files.json")
    }

    override suspend fun match(
        pkgId: Pkg.Id,
        target: APathLookup<APath>,
        areaType: DataArea.Type,
        pfpSegs: Segments
    ): ExpendablesFilter.Match? {
        val lcsegments = pfpSegs.lowercase()

        if (lcsegments.isNotEmpty() && IGNORED_FILES.contains(lcsegments[lcsegments.size - 1])) {
            return null
        }

        //    0      1     2
        // topdir/.trash/file
        if (lcsegments.size >= 3
            && AREAS.contains(areaType)
            && (areaType != DataArea.Type.SDCARD || !segs("Android").isAncestorOf(lcsegments, ignoreCase = true))
            && TRASH_FOLDERS.contains(lcsegments[1])
        ) {
            return target.toDeletionMatch()
        }

        //    0      1     2     3
        // topdir/files/.trash/file
        if (lcsegments.size >= 4
            && AREAS.contains(areaType)
            && (areaType != DataArea.Type.SDCARD || !segs("Android").isAncestorOf(lcsegments, ignoreCase = true))
            && "files" == lcsegments[1]
            && TRASH_FOLDERS.contains(lcsegments[2])
        ) {
            return target.toDeletionMatch()
        }

        //    0        1     2     3
        // Android/.Trash/<pkg>/file
        if (lcsegments.size >= 4
            && "android" == lcsegments[0]
            && ".trash" == lcsegments[1]
            && lcsegments[2] == pkgId.name
        ) {
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
        private val filterProvider: Provider<RecycleBinsFilter>
    ) : ExpendablesFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterRecycleBinsEnabled.value()
        override suspend fun create(): ExpendablesFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): ExpendablesFilter.Factory
    }

    companion object {
        private val AREAS = setOf(
            DataArea.Type.SDCARD,
            DataArea.Type.PRIVATE_DATA,
            DataArea.Type.PUBLIC_DATA,
            DataArea.Type.PUBLIC_MEDIA,
        )
        private val TRASH_FOLDERS: Collection<String> = listOf(
            ".trash",
            "trash",
            ".trashfiles",
            "trashfiles",
            ".trashbin",
            "trashbin",
            ".recycle",
            "recycle",
            ".recyclebin",
            "recyclebin",
            ".garbage"
        )
        private val IGNORED_FILES: Collection<String> = listOf(
            ".nomedia",
        )
        private val TAG = logTag("AppCleaner", "Scanner", "Filter", "RecycleBins")
    }
}