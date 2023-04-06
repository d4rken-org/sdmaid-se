package eu.darken.sdmse.systemcleaner.core.filter.generic

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.modules.pubdata.SdcardsModule
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import java.io.File
import javax.inject.Inject
import javax.inject.Provider

class TempFilesFilter @Inject constructor(
    private val baseSieveFactory: BaseSieve.Factory,
    private val areaManager: DataAreaManager,
) : SystemCleanerFilter {

    override suspend fun targetAreas(): Set<DataArea.Type> = setOf(
        DataArea.Type.SDCARD,
        DataArea.Type.PUBLIC_DATA,
        DataArea.Type.PUBLIC_MEDIA,
        DataArea.Type.DATA,
        DataArea.Type.PRIVATE_DATA,
        DataArea.Type.DATA_SYSTEM,
        DataArea.Type.DATA_SYSTEM_DE,
    )

    private lateinit var sieve: BaseSieve

    override suspend fun initialize() {
        val config = BaseSieve.Config(
            targetType = BaseSieve.TargetType.FILE,
            areaTypes = targetAreas(),
            exclusions = setOf(
                BaseSieve.Exclusion(segs("backup", "pending")),
                BaseSieve.Exclusion(segs("cache", "recovery")),
                BaseSieve.Exclusion(
                    segs(
                        "com.drweb.pro.market",
                        "files",
                        "pro_settings"
                    )
                ), // TODO move to exclusion manager?
            )
        )


        sieve = baseSieveFactory.create(config)
        log(TAG) { "initialized()" }
    }

    private val tempSuffixes = setOf(
        ".tmp",
        ".temp",
    )
    private val sdmTempFileRegex = Regex(
        "(?:sdm_write_test-[0-9a-f-]+)".replace("/", "\\" + File.separator)
    )

    override suspend fun matches(item: APathLookup<*>): Boolean {
        val sieveResult = sieve.match(item)
        if (!sieveResult.matches) return false

        return when {
            tempSuffixes.any { item.name.endsWith(it) } -> true
            item.name == ".mmsyscache" -> true
            item.name.startsWith("sdm_write_test-") && sdmTempFileRegex.matchEntire(item.name) != null -> true
            item.name.startsWith(SdcardsModule.TEST_PREFIX) -> true
            else -> false
        }
    }

    override fun toString(): String = "${this::class.simpleName}(${hashCode()})"

    @Reusable
    class Factory @Inject constructor(
        private val settings: SystemCleanerSettings,
        private val filterProvider: Provider<TempFilesFilter>
    ) : SystemCleanerFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterTempFilesEnabled.value()
        override suspend fun create(): SystemCleanerFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): SystemCleanerFilter.Factory
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "Filter", "TempFiles")
    }
}
