package eu.darken.sdmse.appcleaner.core.forensics.filter

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.appcleaner.core.forensics.BaseExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.sieves.json.JsonBasedSieve
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.files.lowercase
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.storage.StorageEnvironment
import javax.inject.Inject
import javax.inject.Provider


@Reusable
class BugReportingFilter @Inject constructor(
    private val jsonBasedSieveFactory: JsonBasedSieve.Factory,
    environment: StorageEnvironment,
    private val gatewaySwitch: GatewaySwitch,
) : BaseExpendablesFilter() {

    private val cacheFolderPrefixes = environment.ourCacheDirs.map { it.name }

    private lateinit var sieve: JsonBasedSieve

    override suspend fun initialize() {
        log(TAG) { "initialize()" }
        sieve = jsonBasedSieveFactory.create("expendables/db_bug_reporting_files.json")
    }

    override suspend fun match(
        pkgId: Pkg.Id,
        target: APathLookup<APath>,
        areaType: DataArea.Type,
        segments: Segments
    ): ExpendablesFilter.Match? {
        val lcsegments = segments.lowercase()

        // Default cachefolder, we don't handle that in this filter.
        // pkg/cache/file
        if (lcsegments.size >= 2
            && BLACKLIST_AREAS.contains(areaType)
            && pkgId.name == lcsegments[0]
            && cacheFolderPrefixes.contains(lcsegments[1])
        ) {
            return null
        }

        if (segments.isNotEmpty() && IGNORED_FILES.contains(lcsegments[lcsegments.size - 1])) {
            return null
        }

        //    0      1
        // basedir/update_component_log
        if (lcsegments.size >= 2 && LOGFILE_PATTERNS.any { it.matches(lcsegments[1]) }) {
            return target.toDeletionMatch()
        }

        //    0      1
        // basedir/update_component_log
        if (lcsegments.size >= 2 && FILES.contains(lcsegments[1])) {
            return target.toDeletionMatch()
        }

        //    0      1     2
        // basedir/Logfiles/file
        if (lcsegments.size >= 3 && FOLDERS.contains(lcsegments[1])) {
            return target.toDeletionMatch()
        }

        //    0      1     2
        // basedir/files/log.txt
        if (lcsegments.size >= 3 && FILES.contains(lcsegments[2])) {
            return target.toDeletionMatch()
        }

        //    0      1     2     3
        // package/files/.cache/file
        if (
            lcsegments.size >= 4
            && (areaType == DataArea.Type.PUBLIC_DATA || areaType == DataArea.Type.PRIVATE_DATA)
            && "files" == lcsegments[1]
            && FOLDERS.contains(lcsegments[2])
        ) {
            return target.toDeletionMatch()
        }

        return if (sieve.matches(pkgId, areaType, segments)) {
            target.toDeletionMatch()
        } else {
            null
        }
    }

    override suspend fun process(matches: Collection<ExpendablesFilter.Match>): ExpendablesFilter.ProcessResult {
        return matches.deleteAll(gatewaySwitch)
    }

    @Reusable
    class Factory @Inject constructor(
        private val settings: AppCleanerSettings,
        private val filterProvider: Provider<BugReportingFilter>
    ) : ExpendablesFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterBugreportingEnabled.value()
        override suspend fun create(): ExpendablesFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): ExpendablesFilter.Factory
    }

    companion object {
        private val BLACKLIST_AREAS = setOf(
            DataArea.Type.PRIVATE_DATA,
            DataArea.Type.PUBLIC_DATA,
        )
        private val FOLDERS: Collection<String> = listOf(
            "logs",
            ".logs",
            "logfiles",
            ".logfiles",
            "log",
            ".log",
            "logtmp",
            ".logtmp",
            "gslb_sdk_log",
            "klog",
            "mipushlog",
            "xlog",
            "tlog_v9"
        ).map { it.lowercase() }

        private val FILES: Collection<String> = listOf(
            "log.txt",
            "usage_logs_v2.txt",
            "gslb_sdk_log",
            "update_component_log",
            "update_component_plugin_log",
            "gslb_log.txt",
            "usage_logs_v2.txt",
            "app_upgrade_log"
        ).map { it.lowercase() }

        internal val LOGFILE_PATTERNS by lazy {
            listOf("\\d{4}-\\d{2}-\\d{2}\\.log\\.txt").map { it.toRegex() }
        }

        private val IGNORED_FILES: Collection<String> = listOf(".nomedia")

        private val TAG = logTag("AppCleaner", "Scanner", "Filter", "BugReporting")
    }
}