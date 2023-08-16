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
import eu.darken.sdmse.common.areas.modules.pub.SdcardsModule
import eu.darken.sdmse.common.ca.CaDrawable
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaDrawable
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve
import eu.darken.sdmse.systemcleaner.core.sieve.NameCriterium
import eu.darken.sdmse.systemcleaner.core.sieve.SegmentCriterium
import java.io.File
import javax.inject.Inject
import javax.inject.Provider

class TempFilesFilter @Inject constructor(
    private val baseSieveFactory: BaseSieve.Factory,
    private val areaManager: DataAreaManager,
) : SystemCleanerFilter {

    override suspend fun getIcon(): CaDrawable = R.drawable.ic_baseline_access_time_filled_24.toCaDrawable()

    override suspend fun getLabel(): CaString = R.string.systemcleaner_filter_tempfiles_label.toCaString()

    override suspend fun getDescription(): CaString = R.string.systemcleaner_filter_tempfiles_summary.toCaString()

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
            targetTypes = setOf(BaseSieve.TargetType.FILE),
            areaTypes = targetAreas(),
            nameCriteria = setOf(
                NameCriterium(".tmp", mode = NameCriterium.Mode.End()),
                NameCriterium(".temp", mode = NameCriterium.Mode.End()),
                NameCriterium(".mmsyscache", mode = NameCriterium.Mode.Equal()),
                NameCriterium("sdm_write_test-", mode = NameCriterium.Mode.Start()),
                NameCriterium(SdcardsModule.TEST_PREFIX, mode = NameCriterium.Mode.Start()),
            ),
            pathExclusions = setOf(
                SegmentCriterium(segs("backup", "pending"), mode = SegmentCriterium.Mode.Ancestor()),
                SegmentCriterium(segs("cache", "recovery"), mode = SegmentCriterium.Mode.Ancestor()),
            ),
            pfpExclusions = setOf(
                SegmentCriterium(
                    segs("com.drweb.pro.market", "files", "pro_settings"),
                    mode = SegmentCriterium.Mode.Ancestor()
                ), // TODO move to exclusion manager?
            )
        )


        sieve = baseSieveFactory.create(config)
        log(TAG) { "initialized()" }
    }

    private val sdmTempFileRegex = Regex(
        "(?:sdm_write_test-[0-9a-f-]+)".replace("/", "\\" + File.separator)
    )

    override suspend fun matches(item: APathLookup<*>): Boolean {
        return sieve.match(item).matches
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
