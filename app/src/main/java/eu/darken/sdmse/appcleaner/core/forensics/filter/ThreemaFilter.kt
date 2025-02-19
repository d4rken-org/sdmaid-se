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
import eu.darken.sdmse.appcleaner.core.forensics.sieves.DynamicAppSieve
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class ThreemaFilter @Inject constructor(
    private val dynamicSieveFactory: DynamicAppSieve.Factory,
    private val gatewaySwitch: GatewaySwitch,
) : BaseExpendablesFilter() {

    private lateinit var sieve: DynamicAppSieve

    override suspend fun initialize() {
        log(TAG) { "initialize()" }
        val config = DynamicAppSieve.MatchConfig(
            pkgNames = setOf("ch.threema.app".toPkgId()),
            areaTypes = setOf(DataArea.Type.SDCARD),
            contains = setOf("Threema/Threema "),
            startsWith = setOf(
                "Threema/Threema Audio",
                "Threema/Threema Pictures",
                "Threema/Threema Videos",
            ),
            exclusions = setOf(".nomedia"),
        )
        sieve = dynamicSieveFactory.create(setOf(config))
    }

    override suspend fun match(
        pkgId: Pkg.Id,
        target: APathLookup<APath>,
        areaType: DataArea.Type,
        segments: Segments
    ): ExpendablesFilter.Match? {
        if (segments.isNotEmpty() && IGNORED_FILES.contains(segments[segments.size - 1])) return null

        return if (segments.isNotEmpty() && sieve.matches(pkgId, areaType, segments)) {
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
        private val filterProvider: Provider<ThreemaFilter>
    ) : ExpendablesFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterThreemaEnabled.value()
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
        private val TAG = logTag("AppCleaner", "Scanner", "Filter", "Threema")
    }
}