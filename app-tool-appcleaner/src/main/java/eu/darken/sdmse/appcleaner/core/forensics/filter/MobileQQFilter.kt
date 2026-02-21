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
import eu.darken.sdmse.common.sieve.SegmentCriterium.Mode
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class MobileQQFilter @Inject constructor(
    private val dynamicSieveFactory: DynamicAppSieve2.Factory,
    private val gatewaySwitch: GatewaySwitch,
) : BaseExpendablesFilter() {

    private lateinit var sieve: DynamicAppSieve2

    override suspend fun initialize() {
        log(TAG) { "initialize()" }
        val configOne = DynamicAppSieve2.MatchConfig(
            pkgNames = setOf("com.tencent.mobileqq".toPkgId()),
            areaTypes = setOf(DataArea.Type.SDCARD, DataArea.Type.PUBLIC_DATA),
            pfpCriteria = setOf(
                SegmentCriterium("Tencent/MobileQQ/chatpic", Mode.Ancestor()),
                SegmentCriterium("Tencent/MobileQQ/shortvideo", Mode.Ancestor()),
                SegmentCriterium("com.tencent.mobileqq/MobileQQ/chatpic", Mode.Ancestor()),
                SegmentCriterium("com.tencent.mobileqq/MobileQQ/shortvideo", Mode.Ancestor()),
            ),
            pfpExclusions = setOf(NameCriterium(".nomedia", mode = NameCriterium.Mode.Equal())),
        )
        val configTwo = DynamicAppSieve2.MatchConfig(
            pkgNames = setOf("com.tencent.mobileqq".toPkgId()),
            areaTypes = setOf(DataArea.Type.SDCARD, DataArea.Type.PUBLIC_DATA),
            pfpCriteria = setOf(
                CriteriaOperator.And(
                    SegmentCriterium("com.tencent.mobileqq/MobileQQ", Mode.Ancestor()),
                    SegmentCriterium("ptt", Mode.Specific(index = 1, backwards = true)),
                ),
                CriteriaOperator.And(
                    SegmentCriterium("Tencent/MobileQQ", Mode.Ancestor()),
                    SegmentCriterium("ptt", Mode.Specific(index = 1, backwards = true)),
                ),
            ),
            pfpExclusions = setOf(NameCriterium(".nomedia", mode = NameCriterium.Mode.Equal())),
        )

        sieve = dynamicSieveFactory.create(setOf(configOne, configTwo))
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
        private val filterProvider: Provider<MobileQQFilter>
    ) : ExpendablesFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterMobileQQEnabled.value()
        override suspend fun create(): ExpendablesFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): ExpendablesFilter.Factory
    }

    companion object {
        private val TAG = logTag("AppCleaner", "Scanner", "Filter", "MobileQQ")
    }
}