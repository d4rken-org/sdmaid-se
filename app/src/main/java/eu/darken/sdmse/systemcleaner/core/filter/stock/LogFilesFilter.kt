package eu.darken.sdmse.systemcleaner.core.filter.stock

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.ca.CaDrawable
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaDrawable
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.files.toSegs
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.BaseSystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve
import eu.darken.sdmse.systemcleaner.core.sieve.NameCriterium
import eu.darken.sdmse.systemcleaner.core.sieve.SegmentCriterium
import java.time.Duration
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class LogFilesFilter @Inject constructor(
    private val baseSieveFactory: BaseSieve.Factory,
    private val gatewaySwitch: GatewaySwitch,
) : BaseSystemCleanerFilter() {

    override suspend fun getIcon(): CaDrawable = R.drawable.ic_baseline_format_list_bulleted_24.toCaDrawable()

    override suspend fun getLabel(): CaString = R.string.systemcleaner_filter_logfiles_label.toCaString()

    override suspend fun getDescription(): CaString = R.string.systemcleaner_filter_logfiles_summary.toCaString()

    override suspend fun targetAreas(): Set<DataArea.Type> = sieves.map { it.config.areaTypes!! }.flatten().toSet()

    private lateinit var sieves: List<BaseSieve>

    override suspend fun initialize() {
        val toLoad = mutableListOf<BaseSieve.Config>()

        BaseSieve.Config(
            areaTypes = setOf(DataArea.Type.SDCARD),
            targetTypes = setOf(BaseSieve.TargetType.FILE),
            nameCriteria = setOf(NameCriterium(".log", mode = NameCriterium.Mode.End())),
            pathExclusions = setOf(
                SegmentCriterium(segs(".indexeddb.leveldb"), mode = SegmentCriterium.Mode.Contain(allowPartial = true)),
                SegmentCriterium(segs("t", "Paths"), mode = SegmentCriterium.Mode.Contain()),
                SegmentCriterium(segs("app_chrome"), mode = SegmentCriterium.Mode.Contain()),
                SegmentCriterium(segs("app_webview"), mode = SegmentCriterium.Mode.Contain()),
                SegmentCriterium(segs("leveldb"), mode = SegmentCriterium.Mode.Contain()),
                SegmentCriterium(segs("shared_proto_db"), mode = SegmentCriterium.Mode.Contain()),
            ),
        ).run { toLoad.add(this) }

        BaseSieve.Config(
            areaTypes = setOf(DataArea.Type.DOWNLOAD_CACHE),
            targetTypes = setOf(BaseSieve.TargetType.FILE),
            nameCriteria = setOf(NameCriterium(".log", mode = NameCriterium.Mode.End())),
        ).run { toLoad.add(this) }


        BaseSieve.Config(
            areaTypes = setOf(DataArea.Type.DATA_VENDOR),
            targetTypes = setOf(BaseSieve.TargetType.FILE),
            nameCriteria = setOf(NameCriterium(".txt.old", mode = NameCriterium.Mode.End())),
            pfpCriteria = setOf(
                SegmentCriterium("radio/extended_logs".toSegs(), mode = SegmentCriterium.Mode.Ancestor())
            )
        ).run { toLoad.add(this) }

        BaseSieve.Config(
            areaTypes = setOf(DataArea.Type.DATA_VENDOR),
            targetTypes = setOf(BaseSieve.TargetType.FILE),
            nameCriteria = setOf(NameCriterium("bt_activity_pkt.txt.last", mode = NameCriterium.Mode.Equal())),
            pfpCriteria = setOf(SegmentCriterium("bluetooth".toSegs(), mode = SegmentCriterium.Mode.Ancestor()))
        ).run { toLoad.add(this) }

        BaseSieve.Config(
            areaTypes = setOf(DataArea.Type.DATA_SYSTEM),
            targetTypes = setOf(BaseSieve.TargetType.FILE),
            nameCriteria = setOf(NameCriterium("checkpoints-", mode = NameCriterium.Mode.Start())),
            pfpCriteria = setOf(SegmentCriterium(segs("shutdown-checkpoints"), mode = SegmentCriterium.Mode.Ancestor()))
        ).run { toLoad.add(this) }

        BaseSieve.Config(
            areaTypes = setOf(DataArea.Type.DATA_MISC),
            targetTypes = setOf(BaseSieve.TargetType.FILE),
            nameCriteria = setOf(NameCriterium("update_engine.", mode = NameCriterium.Mode.Start())),
            pfpCriteria = setOf(
                SegmentCriterium("update_engine_log".toSegs(), mode = SegmentCriterium.Mode.Ancestor())
            ),
            minimumAge = Duration.ofDays(2),
        ).run { toLoad.add(this) }

        BaseSieve.Config(
            areaTypes = setOf(DataArea.Type.DATA_MISC),
            targetTypes = setOf(BaseSieve.TargetType.FILE),
            nameCriteria = setOf(NameCriterium("last_kmsg.", mode = NameCriterium.Mode.Start())),
            pfpCriteria = setOf(
                SegmentCriterium("recovery".toSegs(), mode = SegmentCriterium.Mode.Ancestor())
            ),
        ).run { toLoad.add(this) }

        BaseSieve.Config(
            areaTypes = setOf(DataArea.Type.DATA),
            targetTypes = setOf(BaseSieve.TargetType.FILE, BaseSieve.TargetType.DIRECTORY),
            pfpCriteria = setOf(
                SegmentCriterium("miuilog/stability/scout/app".toSegs(), mode = SegmentCriterium.Mode.Ancestor())
            ),
        ).run { toLoad.add(this) }

        log(TAG) { "initialized() with $toLoad" }
        sieves = toLoad.map { baseSieveFactory.create(it) }
    }

    override suspend fun match(item: APathLookup<*>): SystemCleanerFilter.Match? {
        val match = sieves.firstOrNull { it.match(item).matches }
        if (match == null) return null

        return SystemCleanerFilter.Match.Deletion(item)
    }

    override suspend fun process(matches: Collection<SystemCleanerFilter.Match>) {
        matches.deleteAll(gatewaySwitch)
    }

    override fun toString(): String = "${this::class.simpleName}(${hashCode()})"

    @Reusable
    class Factory @Inject constructor(
        private val settings: SystemCleanerSettings,
        private val filterProvider: Provider<LogFilesFilter>
    ) : SystemCleanerFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterLogFilesEnabled.value()
        override suspend fun create(): SystemCleanerFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): SystemCleanerFilter.Factory
    }

    companion object {
        private val EDGECASE_TELEGRAMX by lazy { Regex(".+/pmc/db/\\d+\\.log") }
        private val TAG = logTag("SystemCleaner", "Filter", "LogFiles")
    }
}