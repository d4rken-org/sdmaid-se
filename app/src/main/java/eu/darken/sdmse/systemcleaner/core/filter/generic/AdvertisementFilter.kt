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
import eu.darken.sdmse.common.files.core.APathLookup
import eu.darken.sdmse.common.files.core.isDirectory
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

    override suspend fun targetTypes(): Collection<DataArea.Type> = setOf(
        DataArea.Type.SDCARD,
    )

    private lateinit var sieve: BaseSieve

    override suspend fun initialize() {
        val basePaths = mutableSetOf<String>()
        val rawRegexes = mutableSetOf<String>()

        // TODO this doesn't work on SAFPath files

        areaManager.currentAreas()
            .filter { it.type == DataArea.Type.SDCARD }
            .map { it.path }
            .forEach { toCheck ->
                basePaths.add(toCheck.child("ppy_cross").path)
                rawRegexes.add(
                    String.format(
                        "^(?:%s/ppy_cross)$".replace("/", "\\${File.separator}"),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                basePaths.add(toCheck.child("/.mologiq").path)
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:\\.mologiq|\\.mologiq/.+)$".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                basePaths.add(toCheck.child("/.Adcenix").path)
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:\\.Adcenix|\\.Adcenix/.+)$".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                basePaths.add(toCheck.child("/ApplifierVideoCache").path)
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:ApplifierVideoCache|ApplifierVideoCache/.+)".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                basePaths.add(toCheck.child("/burstlyVideoCache").path)
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:burstlyVideoCache|burstlyVideoCache/.+)".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                basePaths.add(toCheck.child("/UnityAdsVideoCache").path)
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:UnityAdsVideoCache|UnityAdsVideoCache/.+)".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                basePaths.add(toCheck.child("/ApplifierImageCache").path)
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:ApplifierImageCache|ApplifierImageCache/.+)".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                basePaths.add(toCheck.child("/burstlyImageCache").path)
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:burstlyImageCache|burstlyImageCache/.+)".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )

                )
                basePaths.add(toCheck.child("/UnityAdsImageCache").path)
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:UnityAdsImageCache|UnityAdsImageCache/.+)".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )

                )
                basePaths.add(toCheck.child("/__chartboost").path)
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:__chartboost|__chartboost/.+)$".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )

                )
                basePaths.add(toCheck.child("/.chartboost").path)
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:\\.chartboost|\\.chartboost/.+)$".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )

                )
                basePaths.add(toCheck.child("/adhub").path)
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:adhub|adhub/.+)$".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
                basePaths.add(toCheck.child("/.mobvista").path)
                rawRegexes.add(
                    String.format(
                        "^(?:%s/)(?:\\.mobvista\\d+|\\.mobvista\\d+/.+)$".replace("/", "\\" + File.separator),
                        toCheck.path.replace("\\", "\\\\")
                    )
                )
            }

        val config = BaseSieve.Config(
            areaTypes = setOf(DataArea.Type.SDCARD),
            basePaths = basePaths,
            regex = rawRegexes.map { Regex(it) }.toSet()
        )
        sieve = baseSieveFactory.create(config)
    }

    override suspend fun sieve(item: APathLookup<*>): Boolean {
        if (!sieve.match(item)) return false
        return !item.name.endsWith("chartboost") || item.isDirectory
    }

    @Reusable
    class Factory @Inject constructor(
        private val settings: SystemCleanerSettings,
        private val filterProvider: Provider<LogFilesFilter>
    ) : SystemCleanerFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterAdvertisementsEnabled.value()
        override suspend fun create(): SystemCleanerFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): SystemCleanerFilter.Factory
    }
}