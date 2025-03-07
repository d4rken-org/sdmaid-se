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
class WeChatFilter @Inject constructor(
    private val dynamicSieveFactory: DynamicAppSieve.Factory,
    private val gatewaySwitch: GatewaySwitch,
) : BaseExpendablesFilter() {

    private lateinit var sieve: DynamicAppSieve

    override suspend fun initialize() {
        log(TAG) { "initialize()" }
        val configSD = DynamicAppSieve.MatchConfig(
            pkgNames = setOf("com.tencent.mm".toPkgId()),
            areaTypes = setOf(DataArea.Type.SDCARD),
            startsWith = setOf(
                "tencent/MicroMsg",
            ),
            patterns = setOf(
                "^(?>tencent\\/MicroMsg\\/[0-9a-z-]{32}\\/sns\\/)(?>.+)$",
                "^(?>tencent\\/MicroMsg\\/[0-9a-z-]{32}\\/video\\/)(?>.+)$",
                "^(?>tencent\\/MicroMsg\\/[0-9a-z-]{32}\\/image2\\/)(?>.+)$",
                "^(?>tencent\\/MicroMsg\\/[0-9a-z-]{32}\\/voice2\\/)(?>.+)$",
            ),
            exclusions = setOf(".nomedia"),
        )
        val configPub = DynamicAppSieve.MatchConfig(
            pkgNames = setOf("com.tencent.mm".toPkgId()),
            areaTypes = setOf(DataArea.Type.PUBLIC_DATA),
            startsWith = setOf(
                "com.tencent.mm/MicroMsg",
            ),
            patterns = setOf(
                "^(?>com.tencent.mm\\/MicroMsg\\/[0-9a-z-]{32}\\/sns\\/)(?>.+)$",
                "^(?>com.tencent.mm\\/MicroMsg\\/[0-9a-z-]{32}\\/video\\/)(?>.+)$",
                "^(?>com.tencent.mm\\/MicroMsg\\/[0-9a-z-]{32}\\/image2\\/)(?>.+)$",
                "^(?>com.tencent.mm\\/MicroMsg\\/[0-9a-z-]{32}\\/voice2\\/)(?>.+)$",
            ),
            exclusions = setOf(".nomedia"),
        )
        val configPriv = DynamicAppSieve.MatchConfig(
            pkgNames = setOf("com.tencent.mm".toPkgId()),
            areaTypes = setOf(DataArea.Type.PRIVATE_DATA),
            startsWith = setOf(
                "com.tencent.mm/MicroMsg",
            ),
            patterns = setOf(
                "^(?>com.tencent.mm\\/MicroMsg\\/[0-9a-z-]{32}\\/sns\\/)(?>.+)$",
                "^(?>com.tencent.mm\\/MicroMsg\\/[0-9a-z-]{32}\\/video\\/)(?>.+)$",
                "^(?>com.tencent.mm\\/MicroMsg\\/[0-9a-z-]{32}\\/image2\\/)(?>.+)$",
                "^(?>com.tencent.mm\\/MicroMsg\\/[0-9a-z-]{32}\\/voice2\\/)(?>.+)$",
            ),
            exclusions = setOf(".nomedia"),
        )

        sieve = dynamicSieveFactory.create(setOf(configSD, configPub, configPriv))
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
        private val filterProvider: Provider<WeChatFilter>
    ) : ExpendablesFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterWeChatEnabled.value()
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
        private val TAG = logTag("AppCleaner", "Scanner", "Filter", "WeChat")
    }
}