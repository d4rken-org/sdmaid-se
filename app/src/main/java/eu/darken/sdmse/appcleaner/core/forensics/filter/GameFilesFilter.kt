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
import eu.darken.sdmse.common.pkgs.Pkg
import javax.inject.Inject
import javax.inject.Provider


@Reusable
class GameFilesFilter @Inject constructor(
    private val jsonBasedSieveFactory: JsonBasedSieve.Factory
) : ExpendablesFilter {

    private lateinit var sieve: JsonBasedSieve

    override suspend fun initialize() {
        log(TAG) { "initialize()" }
        sieve = jsonBasedSieveFactory.create("expendables/db_downloaded_game_files.json")
    }

    override suspend fun isExpendable(
        pkgId: Pkg.Id,
        target: APathLookup<APath>,
        areaType: DataArea.Type,
        segments: Segments
    ): Boolean {
        if (segments.isNotEmpty() && IGNORED_FILES.contains(segments[segments.size - 1].lowercase())) {
            return false
        }

        //    0      1     2
        // basedir/offlinecache/file
        if (segments.size >= 3 && TARGET_FOLDERS.contains(segments[1].lowercase())) {
            return true
        }

        //    0      1     2     3
        // package/files/offlinecache/file
        if (segments.size >= 4
            && "files" == segments[1].lowercase()
            && TARGET_FOLDERS.contains(segments[2].lowercase())
        ) {
            return true
        }

        return segments.isNotEmpty() && sieve.matches(pkgId, areaType, segments)
    }

    @Reusable
    class Factory @Inject constructor(
        private val settings: AppCleanerSettings,
        private val filterProvider: Provider<GameFilesFilter>
    ) : ExpendablesFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterGameFilesEnabled.value()
        override suspend fun create(): ExpendablesFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): ExpendablesFilter.Factory
    }

    companion object {
        private val TAG = logTag("AppCleaner", "Scanner", "Filter", "GameFiles")
        private val TARGET_FOLDERS: Collection<String> = listOf(
            "unitycache"
        )
        private val IGNORED_FILES: Collection<String> = listOf(
            ".nomedia",
        )
    }
}