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
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.APathLookup
import eu.darken.sdmse.common.files.core.isAncestorOf
import eu.darken.sdmse.common.files.core.segs
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import java.util.*
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class LogFilesFilter @Inject constructor(
    private val baseSieveFactory: BaseSieve.Factory,
    private val areaManager: DataAreaManager,
) : SystemCleanerFilter {

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
            targetType = BaseSieve.TargetType.FILE,
            nameSuffixes = setOf(".log"),
            exclusions = setOf(
                BaseSieve.Exclusion(segs(".indexeddb.leveldb"), allowPartial = true),
                BaseSieve.Exclusion(segs("t", "Paths")),
                BaseSieve.Exclusion(segs("app_chrome")),
                BaseSieve.Exclusion(segs("app_webview")),
                BaseSieve.Exclusion(segs("leveldb")),
                BaseSieve.Exclusion(segs("shared_proto_db")),
            )
        )
        sieve = baseSieveFactory.create(config)
        log(TAG) { "initialized()" }
    }


    override suspend fun sieve(item: APathLookup<*>): Boolean {
        val sieveResult = sieve.match(item)
        if (!sieveResult.matches) return false

        // TODO Support SAF path matching?
        // https://github.com/d4rken/sdmaid-public/issues/2147
        val badTelegramMatch = EDGECASE_TELEGRAMX.matcher(item.path).matches()

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
        private val EDGECASE_TELEGRAMX = Pattern.compile(".+/pmc/db/\\d+\\.log")
        private val TAG = logTag("SystemCleaner", "Filter", "LogFiles")
    }
}