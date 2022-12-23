package eu.darken.sdmse.systemcleaner.core.filter

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
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.APathLookup
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class LogFilesFilter @Inject constructor(
    private val baseSieveFactory: BaseSieve.Factory,
    private val areaManager: DataAreaManager,
) : SystemCleanerFilter {
    
    override suspend fun targetTypes(): Collection<DataArea.Type> = setOf(
        DataArea.Type.DATA,
        DataArea.Type.SDCARD,
        DataArea.Type.DOWNLOAD_CACHE,
    )

    private val sieve by lazy {
        val config = BaseSieve.Config(
            targetType = BaseSieve.TargetType.FILE,
            possibleNameEndings = setOf(".log"),
            exclusions = setOf(
                ".indexeddb.leveldb/",
                "/t/Paths/",
                "/app_chrome/",
                "/app_webview/"
            )
        )
        baseSieveFactory.create(config)
    }
    private val cacheLock = Mutex()
    private var _mediaLocations: Set<APath>? = null

    private suspend fun getMediaLocations(): Set<APath> = cacheLock.withLock {
        if (_mediaLocations != null) return@withLock _mediaLocations!!
        areaManager.currentAreas()
            .map { it.path.child("media") }
            .toSet()
            .also { _mediaLocations = it }
    }

    override suspend fun sieve(item: APathLookup<*>): Boolean {
        if (!sieve.match(item)) return false

        // https://github.com/d4rken/sdmaid-public/issues/2147
        val badTelegramMatch = EDGECASE_TELEGRAMX.matcher(item.path).matches()

        // https://github.com/d4rken/sdmaid-public/issues/961
        val overlap = getMediaLocations().any { item.path.startsWith(it.path) }
        return !overlap && !badTelegramMatch
    }

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
    }
}