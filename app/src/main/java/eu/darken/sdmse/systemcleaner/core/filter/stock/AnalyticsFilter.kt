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
import eu.darken.sdmse.systemcleaner.core.filter.toDeletion
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve.*
import eu.darken.sdmse.systemcleaner.core.sieve.SegmentCriterium
import eu.darken.sdmse.systemcleaner.core.sieve.SegmentCriterium.Mode
import java.io.File
import javax.inject.Inject
import javax.inject.Provider

class AnalyticsFilter @Inject constructor(
    private val baseSieveFactory: BaseSieve.Factory,
    private val gatewaySwitch: GatewaySwitch,
) : BaseSystemCleanerFilter() {

    override suspend fun getIcon(): CaDrawable = R.drawable.ic_analytics_onsurface.toCaDrawable()

    override suspend fun getLabel(): CaString = R.string.systemcleaner_filter_analytics_label.toCaString()

    override suspend fun getDescription(): CaString = R.string.systemcleaner_filter_analytics_summary.toCaString()

    override suspend fun targetAreas(): Set<DataArea.Type> = setOf(
        DataArea.Type.SDCARD,
        DataArea.Type.PUBLIC_DATA,
        DataArea.Type.PRIVATE_DATA,
    )

    private lateinit var sieve: BaseSieve
    private lateinit var antiTrackingSieve: BaseSieve

    override suspend fun initialize() {
        val pathContains = setOf(
            SegmentCriterium(segs(".bugsense"), mode = Mode.Contain())
        )
        val regexes = setOf(
            Regex("^(?:[\\W\\w]+/\\.(?:bugsense))$".replace("/", "\\" + File.separator))
        )

        val config = Config(
            areaTypes = targetAreas(),
            targetTypes = setOf(TargetType.FILE),
            pathCriteria = pathContains,
            pathRegexes = regexes,
        )
        sieve = baseSieveFactory.create(config)
        log(TAG) { "initialized() with $config" }

        val antiTracking = Config(
            areaTypes = setOf(DataArea.Type.SDCARD, DataArea.Type.PUBLIC_DATA),
            pfpCriteria = setOf(
                SegmentCriterium(segs(".tlocalcookieid"), Mode.Equal()),
                SegmentCriterium(segs(".INSTALLATION"), Mode.Equal()),
                SegmentCriterium(segs(".wps_preloaded_2.txt"), Mode.Equal()),
                SegmentCriterium(".UTSystemConfig/Global/Alvin2.xml".toSegs(), Mode.Equal()),
                SegmentCriterium(".DataStorage/ContextData.xml".toSegs(), Mode.Equal()),
                SegmentCriterium("com.snssdk.api.embed/cache/clientudid.dat".toSegs(), Mode.Equal()),
                SegmentCriterium("Tencent/ams/cache/meta.dat".toSegs(), Mode.Equal()),
                SegmentCriterium("com.tencent.ams/cache/meta.dat".toSegs(), Mode.Equal()),
                SegmentCriterium("backups/.SystemConfig/.cuid".toSegs(), Mode.Equal()),
                SegmentCriterium("backups/.SystemConfig/.cuid2".toSegs(), Mode.Equal()),
                SegmentCriterium("backups/.adiu".toSegs(), Mode.Equal()),
                SegmentCriterium("Mob/comm/dbs/.duid".toSegs(), Mode.Equal()),
                SegmentCriterium(segs(".mn_1006862472"), Mode.Equal()),
                SegmentCriterium(segs(".imei.txt"), Mode.Equal()),
                SegmentCriterium(segs(".DC4278477faeb9.txt"), Mode.Equal()),
                SegmentCriterium("Android/obj/.um/sysid.dat".toSegs(), Mode.Equal()),
                SegmentCriterium(".um/sysid.dat".toSegs(), Mode.Equal()),
                SegmentCriterium(".pns/.uniqueId".toSegs(), Mode.Ancestor()),
                SegmentCriterium(segs(".oukdtft"), Mode.Equal()),
                SegmentCriterium("libs/com.igexin.sdk.deviceId.db".toSegs(), Mode.Equal()),
                SegmentCriterium("data/.push_deviceid".toSegs(), Mode.Equal()),
                SegmentCriterium("msc/.2F6E2C5B63F0F83B".toSegs(), Mode.Equal()),
                SegmentCriterium(".lm_device/.lm_device_id".toSegs(), Mode.Equal()),
                SegmentCriterium("LMDevice/lm_device_id".toSegs(), Mode.Equal()),
            ),
        )
        antiTrackingSieve = baseSieveFactory.create(antiTracking)
        log(TAG) { "initialized() anti tracking sieve with $antiTracking" }
    }


    override suspend fun match(item: APathLookup<*>): SystemCleanerFilter.Match? {
        var match = antiTrackingSieve.match(item)
        if (!match.matches) match = sieve.match(item)
        return match.toDeletion()
    }

    override suspend fun process(matches: Collection<SystemCleanerFilter.Match>) {
        matches.deleteAll(gatewaySwitch)
    }

    override fun toString(): String = "${this::class.simpleName}(${hashCode()})"

    @Reusable
    class Factory @Inject constructor(
        private val settings: SystemCleanerSettings,
        private val filterProvider: Provider<AnalyticsFilter>
    ) : SystemCleanerFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterAnalyticsEnabled.value()
        override suspend fun create(): SystemCleanerFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): SystemCleanerFilter.Factory
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "Filter", "AnalyticsFiles")
    }
}
