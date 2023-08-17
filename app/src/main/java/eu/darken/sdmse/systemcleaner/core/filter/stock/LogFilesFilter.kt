package eu.darken.sdmse.systemcleaner.core.filter.stock

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.ca.CaDrawable
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaDrawable
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.isAncestorOf
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve
import eu.darken.sdmse.systemcleaner.core.sieve.NameCriterium
import eu.darken.sdmse.systemcleaner.core.sieve.SegmentCriterium
import eu.darken.sdmse.systemcleaner.core.sieve.SieveCriterium.*
import java.util.*
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class LogFilesFilter @Inject constructor(
    private val baseSieveFactory: BaseSieve.Factory,
    private val areaManager: DataAreaManager,
) : SystemCleanerFilter {

    override suspend fun getIcon(): CaDrawable = R.drawable.ic_baseline_format_list_bulleted_24.toCaDrawable()

    override suspend fun getLabel(): CaString = R.string.systemcleaner_filter_logfiles_label.toCaString()

    override suspend fun getDescription(): CaString = R.string.systemcleaner_filter_logfiles_summary.toCaString()

    override suspend fun targetAreas(): Set<DataArea.Type> = setOf(
        DataArea.Type.PRIVATE_DATA,
        DataArea.Type.DATA_SYSTEM,
        DataArea.Type.DATA_SYSTEM_CE,
        DataArea.Type.DATA_SYSTEM_DE,
        DataArea.Type.DOWNLOAD_CACHE,
        DataArea.Type.SDCARD,
        DataArea.Type.PUBLIC_DATA,
    )

    private lateinit var sieve: BaseSieve
    private lateinit var mediaLocations: Set<APath>

    override suspend fun initialize() {
        mediaLocations = areaManager.currentAreas()
            .map { it.path.child("media") }
            .toSet()

        val config = BaseSieve.Config(
            areaTypes = targetAreas(),
            targetTypes = setOf(BaseSieve.TargetType.FILE),
            nameCriteria = setOf(NameCriterium(".log", mode = NameCriterium.Mode.End())),
            pathExclusions = setOf(
                SegmentCriterium(segs(".indexeddb.leveldb"), mode = SegmentCriterium.Mode.Contain(allowPartial = true)),
                SegmentCriterium(segs("t", "Paths"), mode = SegmentCriterium.Mode.Contain()),
                SegmentCriterium(segs("app_chrome"), mode = SegmentCriterium.Mode.Contain()),
                SegmentCriterium(segs("app_webview"), mode = SegmentCriterium.Mode.Contain()),
                SegmentCriterium(segs("leveldb"), mode = SegmentCriterium.Mode.Contain()),
                SegmentCriterium(segs("shared_proto_db"), mode = SegmentCriterium.Mode.Contain()),
            )
        )
        sieve = baseSieveFactory.create(config)
        log(TAG) { "initialized()" }
    }


    override suspend fun matches(item: APathLookup<*>): Boolean {
        val sieveResult = sieve.match(item)
        if (!sieveResult.matches) return false

        // TODO Support SAF path matching?
        // https://github.com/d4rken/sdmaid-public/issues/2147
        val badTelegramMatch = EDGECASE_TELEGRAMX.matches(item.path)

        // https://github.com/d4rken/sdmaid-public/issues/961
        val overlap = mediaLocations.any { it.isAncestorOf(item) }
        return !overlap && !badTelegramMatch
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