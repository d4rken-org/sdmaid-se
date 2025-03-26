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
import eu.darken.sdmse.appcleaner.core.forensics.sieves.DynamicAppSieve2
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
import eu.darken.sdmse.common.sieve.CriteriaOperator
import eu.darken.sdmse.common.sieve.NameCriterium
import eu.darken.sdmse.common.sieve.SegmentCriterium
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class WeChatFilter @Inject constructor(
    private val dynamicSieveFactory: DynamicAppSieve2.Factory,
    private val gatewaySwitch: GatewaySwitch,
) : BaseExpendablesFilter() {

    private lateinit var sieve: DynamicAppSieve2

    override suspend fun initialize() {
        log(TAG) { "initialize()" }
        val configSD = DynamicAppSieve2.MatchConfig(
            pkgNames = setOf("com.tencent.mm".toPkgId()),
            areaTypes = setOf(DataArea.Type.SDCARD),
            pfpCriteria = setOf(
                CriteriaOperator.And(
                    SegmentCriterium("tencent/MicroMsg", SegmentCriterium.Mode.Ancestor()),
                    CriteriaOperator.Or(
                        SegmentCriterium("sns", SegmentCriterium.Mode.Specific(1, backwards = true)),
                        SegmentCriterium("video", SegmentCriterium.Mode.Specific(1, backwards = true)),
                        SegmentCriterium("image2", SegmentCriterium.Mode.Specific(1, backwards = true)),
                        SegmentCriterium("voice2", SegmentCriterium.Mode.Specific(1, backwards = true)),
                    )
                )
            ),
            pfpExclusions = setOf(NameCriterium(".nomedia", NameCriterium.Mode.Equal())),
        )
        val configPub = DynamicAppSieve2.MatchConfig(
            pkgNames = setOf("com.tencent.mm".toPkgId()),
            areaTypes = setOf(DataArea.Type.PUBLIC_DATA),
            pfpCriteria = setOf(
                CriteriaOperator.And(
                    SegmentCriterium("com.tencent.mm/MicroMsg", SegmentCriterium.Mode.Ancestor()),
                    CriteriaOperator.Or(
                        SegmentCriterium("sns", SegmentCriterium.Mode.Specific(1, backwards = true)),
                        SegmentCriterium("video", SegmentCriterium.Mode.Specific(1, backwards = true)),
                        SegmentCriterium("image2", SegmentCriterium.Mode.Specific(1, backwards = true)),
                        SegmentCriterium("voice2", SegmentCriterium.Mode.Specific(1, backwards = true)),
                    )
                )
            ),
            pfpExclusions = setOf(NameCriterium(".nomedia", NameCriterium.Mode.Equal())),
        )
        val configPriv = DynamicAppSieve2.MatchConfig(
            pkgNames = setOf("com.tencent.mm".toPkgId()),
            areaTypes = setOf(DataArea.Type.PRIVATE_DATA),
            pfpCriteria = setOf(
                CriteriaOperator.And(
                    SegmentCriterium("com.tencent.mm/MicroMsg", SegmentCriterium.Mode.Ancestor()),
                    CriteriaOperator.Or(
                        SegmentCriterium("sns", SegmentCriterium.Mode.Specific(1, backwards = true)),
                        SegmentCriterium("video", SegmentCriterium.Mode.Specific(1, backwards = true)),
                        SegmentCriterium("image2", SegmentCriterium.Mode.Specific(1, backwards = true)),
                        SegmentCriterium("voice2", SegmentCriterium.Mode.Specific(1, backwards = true)),
                    )
                )
            ),
            pfpExclusions = setOf(NameCriterium(".nomedia", NameCriterium.Mode.Equal())),
        )

        sieve = dynamicSieveFactory.create(setOf(configSD, configPub, configPriv))
    }

    override suspend fun match(
        pkgId: Pkg.Id,
        target: APathLookup<APath>,
        areaType: DataArea.Type,
        pfpSegs: Segments
    ): ExpendablesFilter.Match? = if (pfpSegs.isNotEmpty() && sieve.matches(pkgId, target, areaType, pfpSegs)) {
        target.toDeletionMatch()
    } else {
        null
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
        private val TAG = logTag("AppCleaner", "Scanner", "Filter", "WeChat")
    }
}