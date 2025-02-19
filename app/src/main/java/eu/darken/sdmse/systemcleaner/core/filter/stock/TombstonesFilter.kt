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
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.BaseSystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve
import eu.darken.sdmse.systemcleaner.core.sieve.SegmentCriterium
import javax.inject.Inject
import javax.inject.Provider

class TombstonesFilter @Inject constructor(
    private val baseSieveFactory: BaseSieve.Factory,
    private val gatewaySwitch: GatewaySwitch,
) : BaseSystemCleanerFilter() {

    override suspend fun getIcon(): CaDrawable = R.drawable.ic_tombstone.toCaDrawable()

    override suspend fun getLabel(): CaString = R.string.systemcleaner_filter_tombstones_label.toCaString()

    override suspend fun getDescription(): CaString = R.string.systemcleaner_filter_tombstones_summary.toCaString()

    override suspend fun targetAreas(): Set<DataArea.Type> = sieves.map { it.config.areaTypes!! }.flatten().toSet()

    private lateinit var sieves: List<BaseSieve>

    override suspend fun initialize() {
        val toLoad = mutableListOf<BaseSieve.Config>()

        BaseSieve.Config(
            targetTypes = setOf(BaseSieve.TargetType.FILE),
            areaTypes = setOf(DataArea.Type.DATA),
            pfpCriteria = setOf(SegmentCriterium(segs("tombstones"), mode = SegmentCriterium.Mode.Ancestor())),
        ).run { toLoad.add(this) }
        BaseSieve.Config(
            targetTypes = setOf(BaseSieve.TargetType.FILE),
            areaTypes = setOf(DataArea.Type.DATA_VENDOR),
            pfpCriteria = setOf(SegmentCriterium(segs("tombstones"), mode = SegmentCriterium.Mode.Ancestor())),
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
        private val filterProvider: Provider<TombstonesFilter>,
        private val rootManager: RootManager,
    ) : SystemCleanerFilter.Factory {

        override suspend fun isEnabled(): Boolean {
            val enabled = settings.filterTombstonesEnabled.value()
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
        private val TAG = logTag("SystemCleaner", "Filter", "Tombstones")
    }
}
