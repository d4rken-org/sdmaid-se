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
import eu.darken.sdmse.common.files.*
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve
import eu.darken.sdmse.systemcleaner.core.sieve.SegmentCriterium
import eu.darken.sdmse.systemcleaner.core.sieve.SegmentCriterium.*
import java.io.File
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class AdvertisementFilter @Inject constructor(
    private val baseSieveFactory: BaseSieve.Factory,
    private val areaManager: DataAreaManager,
) : SystemCleanerFilter {

    override suspend fun getIcon(): CaDrawable = R.drawable.ic_baseline_ads_click_24.toCaDrawable()

    override suspend fun getLabel(): CaString = R.string.systemcleaner_filter_advertisements_label.toCaString()

    override suspend fun getDescription(): CaString = R.string.systemcleaner_filter_advertisements_summary.toCaString()

    override suspend fun targetAreas(): Set<DataArea.Type> = setOf(
        DataArea.Type.SDCARD,
    )

    private lateinit var sieve: BaseSieve

    override suspend fun initialize() {
        val pfpCriteria = mutableSetOf<SegmentCriterium>()
        val rawRegexes = mutableSetOf<String>()

        // TODO this doesn't work on SAFPath files

        areaManager.currentAreas()
            .filter { targetAreas().contains(it.type) }
            .map { it.path }
            .forEach { toCheck ->
                pfpCriteria.add(SegmentCriterium(segs("ppy_cross"), mode = Mode.Equal()))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/ppy_cross)$".replace("/", "\\${File.separator}"),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                pfpCriteria.add(SegmentCriterium(segs(".mologiq"), mode = Mode.Start(allowPartial = true)))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:\\.mologiq|\\.mologiq/.+)$".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                pfpCriteria.add(SegmentCriterium(segs(".Adcenix"), mode = Mode.Start()))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:\\.Adcenix|\\.Adcenix/.+)$".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                pfpCriteria.add(SegmentCriterium(segs("ApplifierVideoCache"), mode = Mode.Start()))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:ApplifierVideoCache|ApplifierVideoCache/.+)".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                pfpCriteria.add(SegmentCriterium(segs("burstlyVideoCache"), mode = Mode.Start()))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:burstlyVideoCache|burstlyVideoCache/.+)".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                pfpCriteria.add(SegmentCriterium(segs("UnityAdsVideoCache"), mode = Mode.Start()))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:UnityAdsVideoCache|UnityAdsVideoCache/.+)".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                pfpCriteria.add(SegmentCriterium(segs("ApplifierImageCache"), mode = Mode.Start()))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:ApplifierImageCache|ApplifierImageCache/.+)".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                pfpCriteria.add(SegmentCriterium(segs("burstlyImageCache"), mode = Mode.Start()))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:burstlyImageCache|burstlyImageCache/.+)".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )

                )
                pfpCriteria.add(SegmentCriterium(segs("UnityAdsImageCache"), mode = Mode.Start()))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:UnityAdsImageCache|UnityAdsImageCache/.+)".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )

                )
                pfpCriteria.add(SegmentCriterium(segs("__chartboost"), mode = Mode.Start()))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:__chartboost|__chartboost/.+)$".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )

                )
                pfpCriteria.add(SegmentCriterium(segs(".chartboost"), mode = Mode.Start()))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:\\.chartboost|\\.chartboost/.+)$".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )

                )
                pfpCriteria.add(SegmentCriterium(segs("adhub"), mode = Mode.Start()))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:adhub|adhub/.+)$".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                pfpCriteria.add(SegmentCriterium(segs(".mobvista"), mode = Mode.Start(allowPartial = true)))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:\\.mobvista\\d+|\\.mobvista\\d+/.+)$".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                pfpCriteria.add(SegmentCriterium(segs(".goadsdk"), mode = Mode.Start()))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:\\.goadsdk|\\.goadsdk/.+)$".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                pfpCriteria.add(SegmentCriterium(segs(".goproduct"), mode = Mode.Start()))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:\\.goproduct|\\.goproduct/.+)$".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
            }

        val config = BaseSieve.Config(
            areaTypes = targetAreas(),
            pfpCriteria = pfpCriteria,
            pathRegexes = rawRegexes.map { Regex(it) }.toSet()
        )
        sieve = baseSieveFactory.create(config)
        log(TAG) { "initialized()" }
    }

    override suspend fun matches(item: APathLookup<*>): Boolean {
        val sieveResult = sieve.match(item)
        if (!sieveResult.matches) return false
        return !item.name.endsWith("chartboost") || item.isDirectory
    }

    override fun toString(): String = "${this::class.simpleName}(${hashCode()})"

    @Reusable
    class Factory @Inject constructor(
        private val settings: SystemCleanerSettings,
        private val filterProvider: Provider<AdvertisementFilter>
    ) : SystemCleanerFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterAdvertisementsEnabled.value()
        override suspend fun create(): SystemCleanerFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): SystemCleanerFilter.Factory
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "Filter", "Advertisements")
    }
}