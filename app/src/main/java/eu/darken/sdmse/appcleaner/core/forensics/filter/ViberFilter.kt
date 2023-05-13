package eu.darken.sdmse.appcleaner.core.forensics.filter

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.sieves.dynamic.DynamicSieve
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class ViberFilter @Inject constructor(
    private val dynamicSieveFactory: DynamicSieve.Factory,
) : ExpendablesFilter {

    private lateinit var sieve: DynamicSieve

    override suspend fun initialize() {
        log(TAG) { "initialize()" }
        val config = DynamicSieve.MatchConfig(
            pkgNames = setOf("com.viber.voip".toPkgId()),
            areaTypes = setOf(DataArea.Type.PUBLIC_DATA),
            ancestors = setOf(
                "com.viber.voip/files/.converted_gifs",
                "com.viber.voip/files/.converted_videos",
                "com.viber.voip/files/.import",
                "com.viber.voip/files/.image",
                "com.viber.voip/files/.video",
                "com.viber.voip/files/.gif",
                "com.viber.voip/files/.ptt",
                "com.viber.voip/files/.vptt",
            ),
            exclusions = setOf(".nomedia"),
        )

        sieve = dynamicSieveFactory.create(setOf(config))
    }

    override suspend fun isExpendable(
        pkgId: Pkg.Id,
        target: APathLookup<APath>,
        areaType: DataArea.Type,
        segments: Segments
    ): Boolean {
        if (segments.isNotEmpty() && IGNORED_FILES.contains(segments[segments.size - 1])) return false

        return segments.isNotEmpty() && sieve.matches(pkgId, areaType, segments)
    }

    @Reusable
    class Factory @Inject constructor(
        private val settings: AppCleanerSettings,
        private val filterProvider: Provider<ViberFilter>
    ) : ExpendablesFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterViberEnabled.value()
        override suspend fun create(): ExpendablesFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): ExpendablesFilter.Factory
    }

    companion object {
        private val IGNORED_FILES: Collection<String> = listOf(
            ".nomedia",
        )
        private val TAG = logTag("AppCleaner", "Scanner", "Filter", "Viber")
    }
}