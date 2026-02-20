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
import eu.darken.sdmse.common.sieve.NameCriterium
import eu.darken.sdmse.common.sieve.SegmentCriterium
import eu.darken.sdmse.common.sieve.SegmentCriterium.Mode.Ancestor
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class TelegramFilter @Inject constructor(
    private val dynamicSieveFactory: DynamicAppSieve2.Factory,
    private val gatewaySwitch: GatewaySwitch,
) : BaseExpendablesFilter() {

    private lateinit var sieve: DynamicAppSieve2

    override suspend fun initialize() {
        log(TAG) { "initialize()" }
        val configs = mutableSetOf<DynamicAppSieve2.MatchConfig>()

        DynamicAppSieve2.MatchConfig(
            pkgNames = setOf("org.telegram.messenger".toPkgId()),
            areaTypes = setOf(DataArea.Type.SDCARD, DataArea.Type.PUBLIC_DATA),
            pfpCriteria = setOf(
                SegmentCriterium("Telegram/Telegram Audio", Ancestor()),
                SegmentCriterium("Telegram/Telegram Documents", Ancestor()),
                SegmentCriterium("Telegram/Telegram Images", Ancestor()),
                SegmentCriterium("Telegram/Telegram Video", Ancestor()),
                SegmentCriterium("Telegram/Telegram Stories", Ancestor()),
                SegmentCriterium("org.telegram.messenger/files/Telegram/Telegram Audio", Ancestor()),
                SegmentCriterium("org.telegram.messenger/files/Telegram/Telegram Documents", Ancestor()),
                SegmentCriterium("org.telegram.messenger/files/Telegram/Telegram Images", Ancestor()),
                SegmentCriterium("org.telegram.messenger/files/Telegram/Telegram Video", Ancestor()),
                SegmentCriterium("org.telegram.messenger/files/Telegram/Telegram Stories", Ancestor()),
            ),
            pfpExclusions = setOf(NameCriterium(".nomedia", mode = NameCriterium.Mode.Equal())),
        ).let { configs.add(it) }

        DynamicAppSieve2.MatchConfig(
            pkgNames = setOf("org.telegram.plus".toPkgId()),
            areaTypes = setOf(DataArea.Type.SDCARD),
            pfpCriteria = setOf(
                SegmentCriterium("Telegram/Telegram Audio", Ancestor()),
                SegmentCriterium("Telegram/Telegram Documents", Ancestor()),
                SegmentCriterium("Telegram/Telegram Images", Ancestor()),
                SegmentCriterium("Telegram/Telegram Video", Ancestor()),
                SegmentCriterium("Telegram/Telegram Stories", Ancestor()),
            ),
            pfpExclusions = setOf(NameCriterium(".nomedia", mode = NameCriterium.Mode.Equal())),
        ).let { configs.add(it) }

        DynamicAppSieve2.MatchConfig(
            pkgNames = setOf("org.thunderdog.challegram".toPkgId()),
            areaTypes = setOf(DataArea.Type.SDCARD, DataArea.Type.PUBLIC_DATA),
            pfpCriteria = setOf(
                SegmentCriterium("Telegram/Telegram Audio", Ancestor()),
                SegmentCriterium("Telegram/Telegram Documents", Ancestor()),
                SegmentCriterium("Telegram/Telegram Images", Ancestor()),
                SegmentCriterium("Telegram/Telegram Video", Ancestor()),
                SegmentCriterium("Telegram/Telegram Stories", Ancestor()),
                SegmentCriterium("org.thunderdog.challegram/files/documents", Ancestor()),
                SegmentCriterium("org.thunderdog.challegram/files/music", Ancestor()),
                SegmentCriterium("org.thunderdog.challegram/files/videos", Ancestor()),
                SegmentCriterium("org.thunderdog.challegram/files/video_notes", Ancestor()),
                SegmentCriterium("org.thunderdog.challegram/files/animations", Ancestor()),
                SegmentCriterium("org.thunderdog.challegram/files/voice", Ancestor()),
                SegmentCriterium("org.thunderdog.challegram/files/photos", Ancestor()),
                SegmentCriterium("org.thunderdog.challegram/files/stories", Ancestor()),
            ),
            pfpExclusions = setOf(NameCriterium(".nomedia", mode = NameCriterium.Mode.Equal())),
        ).let { configs.add(it) }

        DynamicAppSieve2.MatchConfig(
            pkgNames = setOf("ir.ilmili.telegraph".toPkgId()),
            areaTypes = setOf(DataArea.Type.SDCARD, DataArea.Type.PUBLIC_DATA),
            pfpCriteria = setOf(
                SegmentCriterium("Telegram/Telegram Audio", Ancestor()),
                SegmentCriterium("Telegram/Telegram Documents", Ancestor()),
                SegmentCriterium("Telegram/Telegram Images", Ancestor()),
                SegmentCriterium("Telegram/Telegram Video", Ancestor()),
                SegmentCriterium("Telegram/Telegram Stories", Ancestor()),
                SegmentCriterium("ir.ilmili.telegraph/files/Telegram/Telegram Audio", Ancestor()),
                SegmentCriterium("ir.ilmili.telegraph/files/Telegram/Telegram Documents", Ancestor()),
                SegmentCriterium("ir.ilmili.telegraph/files/Telegram/Telegram Images", Ancestor()),
                SegmentCriterium("ir.ilmili.telegraph/files/Telegram/Telegram Video", Ancestor()),
                SegmentCriterium("ir.ilmili.telegraph/files/Telegram/Telegram Stories", Ancestor()),
            ),
            pfpExclusions = setOf(NameCriterium(".nomedia", mode = NameCriterium.Mode.Equal())),
        ).let { configs.add(it) }

        DynamicAppSieve2.MatchConfig(
            pkgNames = setOf("org.telegram.messenger.web".toPkgId()),
            areaTypes = setOf(DataArea.Type.SDCARD, DataArea.Type.PUBLIC_DATA),
            pfpCriteria = setOf(
                SegmentCriterium("Telegram/Telegram Audio", Ancestor()),
                SegmentCriterium("Telegram/Telegram Documents", Ancestor()),
                SegmentCriterium("Telegram/Telegram Images", Ancestor()),
                SegmentCriterium("Telegram/Telegram Video", Ancestor()),
                SegmentCriterium("Telegram/Telegram Stories", Ancestor()),
                SegmentCriterium("org.telegram.messenger.web/files/Telegram/Telegram Audio", Ancestor()),
                SegmentCriterium("org.telegram.messenger.web/files/Telegram/Telegram Documents", Ancestor()),
                SegmentCriterium("org.telegram.messenger.web/files/Telegram/Telegram Images", Ancestor()),
                SegmentCriterium("org.telegram.messenger.web/files/Telegram/Telegram Video", Ancestor()),
                SegmentCriterium("org.telegram.messenger.web/files/Telegram/Telegram Stories", Ancestor()),
            ),
            pfpExclusions = setOf(NameCriterium(".nomedia", mode = NameCriterium.Mode.Equal())),
        ).let { configs.add(it) }

        sieve = dynamicSieveFactory.create(configs)
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
        private val filterProvider: Provider<TelegramFilter>
    ) : ExpendablesFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterTelegramEnabled.value()
        override suspend fun create(): ExpendablesFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): ExpendablesFilter.Factory
    }

    companion object {
        private val TAG = logTag("AppCleaner", "Scanner", "Filter", "Telegram")
    }
}