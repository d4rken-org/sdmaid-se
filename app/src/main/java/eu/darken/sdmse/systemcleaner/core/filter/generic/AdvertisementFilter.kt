package eu.darken.sdmse.systemcleaner.core.filter.generic

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.*
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import java.io.File
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class AdvertisementFilter @Inject constructor(
    private val baseSieveFactory: BaseSieve.Factory,
    private val areaManager: DataAreaManager,
) : SystemCleanerFilter {

    override suspend fun targetAreas(): Set<DataArea.Type> = setOf(
        DataArea.Type.SDCARD,
    )

    private lateinit var sieve: BaseSieve

    override suspend fun initialize() {
        val startsWith = mutableSetOf<Segments>()
        val rawRegexes = mutableSetOf<String>()

        // TODO this doesn't work on SAFPath files

        areaManager.currentAreas()
            .filter { targetAreas().contains(it.type) }
            .map { it.path }
            .forEach { toCheck ->
                startsWith.add(segs("ppy_cross"))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/ppy_cross)$".replace("/", "\\${File.separator}"),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                startsWith.add(segs(".mologiq"))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:\\.mologiq|\\.mologiq/.+)$".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                startsWith.add(segs(".Adcenix"))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:\\.Adcenix|\\.Adcenix/.+)$".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                startsWith.add(segs("ApplifierVideoCache"))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:ApplifierVideoCache|ApplifierVideoCache/.+)".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                startsWith.add(segs("burstlyVideoCache"))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:burstlyVideoCache|burstlyVideoCache/.+)".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                startsWith.add(segs("UnityAdsVideoCache"))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:UnityAdsVideoCache|UnityAdsVideoCache/.+)".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                startsWith.add(segs("ApplifierImageCache"))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:ApplifierImageCache|ApplifierImageCache/.+)".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                startsWith.add(segs("burstlyImageCache"))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:burstlyImageCache|burstlyImageCache/.+)".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )

                )
                startsWith.add(segs("UnityAdsImageCache"))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:UnityAdsImageCache|UnityAdsImageCache/.+)".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )

                )
                startsWith.add(segs("__chartboost"))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:__chartboost|__chartboost/.+)$".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )

                )
                startsWith.add(segs(".chartboost"))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:\\.chartboost|\\.chartboost/.+)$".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )

                )
                startsWith.add(segs("adhub"))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:adhub|adhub/.+)$".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                startsWith.add(segs(".mobvista"))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:\\.mobvista\\d+|\\.mobvista\\d+/.+)$".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                startsWith.add(segs(".goadsdk"))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:\\.goadsdk|\\.goadsdk/.+)$".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                startsWith.add(segs(".goproduct"))
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:\\.goproduct|\\.goproduct/.+)$".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
            }

        val config = BaseSieve.Config(
            areaTypes = targetAreas(),
            pathPrefixes = startsWith,
            regexes = rawRegexes.map { Regex(it) }.toSet()
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