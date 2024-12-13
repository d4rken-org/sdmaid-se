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
import eu.darken.sdmse.common.cache.CacheRepo
import eu.darken.sdmse.common.compression.entries
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.copyToAutoClose
import eu.darken.sdmse.common.files.core.local.deleteAll
import eu.darken.sdmse.common.files.file
import eu.darken.sdmse.common.files.inputStream
import eu.darken.sdmse.common.files.local.toLocalPath
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.hashing.Hasher
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.get
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.BaseSystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve
import eu.darken.sdmse.systemcleaner.core.sieve.NameCriterium
import eu.darken.sdmse.systemcleaner.core.sieve.SegmentCriterium
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Provider

class SuperfluousApksFilter @Inject constructor(
    private val baseSieveFactory: BaseSieve.Factory,
    private val pkgOps: PkgOps,
    private val pkgRepo: PkgRepo,
    private val gatewaySwitch: GatewaySwitch,
    private val cacheRepo: CacheRepo,
) : BaseSystemCleanerFilter() {

    override suspend fun getIcon(): CaDrawable = R.drawable.ic_app_extra_24.toCaDrawable()

    override suspend fun getLabel(): CaString = R.string.systemcleaner_filter_superfluosapks_label.toCaString()

    override suspend fun getDescription(): CaString = R.string.systemcleaner_filter_superfluosapks_summary.toCaString()

    override suspend fun targetAreas(): Set<DataArea.Type> = setOf(
        DataArea.Type.SDCARD,
        DataArea.Type.PORTABLE,
    )

    private val cacheDir by lazy {
        File(cacheRepo.baseCacheDir, "systemcleaner/filter/superfluousapks").also { dir ->
            dir.mkdirs()
            dir.let { it.listFiles()?.toList() }?.forEach {
                log(TAG, WARN) { "Deleting stale cache data: $it" }
                it.deleteAll()
            }
        }
    }

    private lateinit var sieve: BaseSieve

    override suspend fun initialize() {
        val config = BaseSieve.Config(
            targetTypes = setOf(BaseSieve.TargetType.FILE),
            areaTypes = targetAreas(),
            nameCriteria = setOf(
                NameCriterium(".apk", mode = NameCriterium.Mode.End()),
                NameCriterium(".apks", mode = NameCriterium.Mode.End()),
            ),
            pathExclusions = EXCLUSIONS
        )
        sieve = baseSieveFactory.create(config)
        log(TAG) { "initialized() with $config" }
    }

    override suspend fun match(item: APathLookup<*>): SystemCleanerFilter.Match? {
        val sieveResult = sieve.match(item)
        if (!sieveResult.matches) return null
        log(TAG, VERBOSE) { "Passed sieve, checking $item" }

        val apkInfo = when {
            item.name.endsWith(".apk") -> {
                pkgOps.viewArchive(item.lookedUp)
            }

            item.name.endsWith(".apks") -> withContext(NonCancellable) {
                val checksum = item.file(gatewaySwitch, readWrite = false).source().use {
                    Hasher(Hasher.Type.MD5).calc(it)
                }.format()
                log(TAG, VERBOSE) { "Checksum is $checksum for ${item.path}" }

                val baseNames = setOf("base.apk")

                val extractedDir = File(cacheDir, checksum).also { it.mkdirs() }
                try {
                    val extractedBase = File(extractedDir, "base.apk")

                    if (extractedBase.exists()) {
                        log(TAG, WARN) { "Why do we already have extracted this?: $extractedBase" }
                    } else if (cacheRepo.canSpare(item.size)) {
                        item.file(gatewaySwitch, readWrite = false).source().use { apksSource ->
                            ZipInputStream(apksSource.inputStream()).use { zis ->
                                zis.entries
                                    .find { (_, entry) ->
                                        log(TAG, VERBOSE) { "Checking archive entry: ${item.path}/${entry.name}" }
                                        baseNames.contains(entry.name)
                                    }
                                    ?.let { (stream, _) ->
                                        log(TAG, VERBOSE) { "Extracting to $extractedBase" }
                                        stream.source().buffer().use { it.copyToAutoClose(extractedBase) }
                                    }
                            }
                        }
                        log(TAG, VERBOSE) { "$extractedBase is ${extractedBase.length()}" }
                    } else {
                        log(TAG, WARN) { "Don't have enough cache space to extract $item" }
                    }

                    if (extractedBase.exists()) pkgOps.viewArchive(extractedBase.toLocalPath()) else null
                } finally {
                    extractedDir.deleteAll()
                }
            }

            else -> null
        }

        if (apkInfo == null) {
            log(TAG, WARN) { "Failed to read archive: $item" }
            return null
        }

        log(TAG) { "Checking status for ${apkInfo.packageName} (${apkInfo.versionCode})" }

        // TODO Multiple profiles can't have different versions of the same APK, right?
        val installed = pkgRepo.get(apkInfo.id).firstOrNull() ?: return null

        val superfluos = installed.versionCode >= apkInfo.versionCode
        if (superfluos) {
            log(TAG, VERBOSE) {
                "Superfluos: ${installed.packageName} installed=${installed.versionCode}, archive=${apkInfo.versionCode}"
            }
        }
        return if (superfluos) SystemCleanerFilter.Match.Deletion(item) else null
    }

    override suspend fun process(matches: Collection<SystemCleanerFilter.Match>) {
        matches.deleteAll(gatewaySwitch)
    }

    override fun toString(): String = "${this::class.simpleName}(${hashCode()})"

    @Reusable
    class Factory @Inject constructor(
        private val settings: SystemCleanerSettings,
        private val filterProvider: Provider<SuperfluousApksFilter>
    ) : SystemCleanerFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterSuperfluosApksEnabled.value()
        override suspend fun create(): SystemCleanerFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): SystemCleanerFilter.Factory
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "Filter", "SuperfluousApks")
        val EXCLUSIONS = setOf(
            SegmentCriterium(segs("Backup"), mode = SegmentCriterium.Mode.Contain()),
            SegmentCriterium(segs("Backups"), mode = SegmentCriterium.Mode.Contain()),
            SegmentCriterium(segs("Recover"), mode = SegmentCriterium.Mode.Contain()),
            SegmentCriterium(segs("Recovery"), mode = SegmentCriterium.Mode.Contain()),
            SegmentCriterium(segs("TWRP"), mode = SegmentCriterium.Mode.Contain()),
        )
    }
}