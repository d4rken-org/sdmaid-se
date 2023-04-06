package eu.darken.sdmse.appcleaner.core.forensics.filter

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.isPublic
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.storage.StorageEnvironment
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class DefaultCachesPublicFilter @Inject constructor(
    environment: StorageEnvironment,
) : ExpendablesFilter {

    private val cacheFolderPrefixes = environment.ourExternalCacheDirs.map { it.name }

    override suspend fun initialize() {
        log(TAG) { "initialize()" }
    }

    override suspend fun isExpendable(
        pkgId: Pkg.Id,
        target: APathLookup<APath>,
        areaType: DataArea.Type,
        segments: Segments
    ): Boolean {
        if (segments.isNotEmpty() && IGNORED_FILES.contains(segments[segments.size - 1])) return false

        if (!areaType.isPublic) return false

        return segments.size >= 3 && cacheFolderPrefixes.contains(segments[1])
    }

    @Reusable
    class Factory @Inject constructor(
        private val settings: AppCleanerSettings,
        private val filterProvider: Provider<DefaultCachesPublicFilter>
    ) : ExpendablesFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterDefaultCachesPublicEnabled.value()
        override suspend fun create(): ExpendablesFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): ExpendablesFilter.Factory
    }

    companion object {
        private val TAG = logTag("AppCleaner", "Scanner", "Filter", "DefaultCaches", "Public")
        private val IGNORED_FILES: Collection<String> = listOf(
            ".nomedia"
        )
    }
}