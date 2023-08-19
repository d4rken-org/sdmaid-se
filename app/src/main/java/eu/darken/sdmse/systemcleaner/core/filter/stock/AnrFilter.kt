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
import eu.darken.sdmse.common.areas.hasFlags
import eu.darken.sdmse.common.ca.CaDrawable
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaDrawable
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve
import eu.darken.sdmse.systemcleaner.core.sieve.SegmentCriterium
import java.lang.String
import javax.inject.Inject
import javax.inject.Provider

class AnrFilter @Inject constructor(
    private val baseSieveFactory: BaseSieve.Factory,
    private val areaManager: DataAreaManager,
) : SystemCleanerFilter {

    override suspend fun getIcon(): CaDrawable = R.drawable.ic_baseline_running_with_errors_24.toCaDrawable()

    override suspend fun getLabel(): CaString = R.string.systemcleaner_filter_anr_label.toCaString()

    override suspend fun getDescription(): CaString = R.string.systemcleaner_filter_anr_summary.toCaString()

    override suspend fun targetAreas(): Set<DataArea.Type> = setOf(
        DataArea.Type.DATA,
        DataArea.Type.DOWNLOAD_CACHE,
    )

    private lateinit var sieve: BaseSieve

    override suspend fun initialize() {
        val regexPairs = areaManager.currentAreas()
            .filter { targetAreas().contains(it.type) }
            .filter { it.hasFlags(DataArea.Flag.PRIMARY) }
            .map { area ->
                val path = area.path.child("anr")
                val regex = String.format(
                    "^(?:%s/anr/[\\W\\w]+)$".replace("/", "\\/"),
                    area.path.path.replace("\\", "\\\\")
                )
                path to regex
            }

        require(regexPairs.isNotEmpty()) { "Filter underdefined" }

        val config = BaseSieve.Config(
            targetTypes = setOf(BaseSieve.TargetType.FILE),
            areaTypes = targetAreas(),
            pfpCriteria = setOf(
                SegmentCriterium(segs("anr"), mode = SegmentCriterium.Mode.Ancestor())
            ),
            pathRegexes = regexPairs.map { Regex(it.second) }.toSet(),
        )

        sieve = baseSieveFactory.create(config)
        log(TAG) { "initialized()" }
    }


    override suspend fun matches(item: APathLookup<*>): Boolean {
        return sieve.match(item).matches
    }

    override fun toString(): kotlin.String = "${this::class.simpleName}(${hashCode()})"

    @Reusable
    class Factory @Inject constructor(
        private val settings: SystemCleanerSettings,
        private val filterProvider: Provider<AnrFilter>,
        private val rootManager: RootManager,
    ) : SystemCleanerFilter.Factory {

        override suspend fun isEnabled(): Boolean {
            val enabled = settings.filterAnrEnabled.value()
            val useRoot = rootManager.canUseRootNow()
            if (enabled && !useRoot) log(TAG, INFO) { "Filter is enabled, but requires root, which is unavailable." }
            return enabled && useRoot
        }

        override suspend fun create(): SystemCleanerFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): SystemCleanerFilter.Factory
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "Filter", "Anr")
    }
}