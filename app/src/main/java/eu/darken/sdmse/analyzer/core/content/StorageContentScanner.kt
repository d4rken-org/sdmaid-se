package eu.darken.sdmse.analyzer.core.content

import android.app.usage.StorageStatsManager
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.content.types.AppContent
import eu.darken.sdmse.analyzer.core.content.types.MediaContent
import eu.darken.sdmse.analyzer.core.content.types.StorageContent
import eu.darken.sdmse.analyzer.core.content.types.SystemContent
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.RawPath
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.currentPkgs
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.common.storage.StorageManager2
import javax.inject.Inject

class StorageContentScanner @Inject constructor(
    private val storageEnvironment: StorageEnvironment,
    private val storageManager2: StorageManager2,
    private val statsManager: StorageStatsManager,
    private val pkgRepo: PkgRepo,
    private val rootManager: RootManager,
) {


    suspend fun scan(storageId: DeviceStorage.Id): Collection<StorageContent> {
        log(TAG) { "scan($storageId)" }
//        val storage = storageManager2.volumes.first { it.id }

        val useRoot = rootManager.useRoot()

        val pkgStats = pkgRepo.currentPkgs()
            .filter { it.packageInfo.applicationInfo != null }
            .map {
                val storageStats = statsManager.queryStatsForUid(storageId.asUUID, it.packageInfo.applicationInfo.uid)

                val appCode = if (useRoot) {
                    emptySet<ContentItem>()
                } else {
                    setOf(
                        ContentItem(
                            label = R.string.analyzer_storage_content_app_code_label.toCaString(),
                            path = RawPath.build(it.packageInfo.applicationInfo.sourceDir),
                            size = storageStats.appBytes,
                        )
                    )
                }

                val privateAppData = if (useRoot) {
                    emptySet<ContentItem>()
                } else {
                    setOf(
                        ContentItem(
                            label = R.string.analyzer_storage_content_app_data_private_label.toCaString(),
                            path = RawPath.build(it.packageInfo.applicationInfo.sourceDir),
                            size = storageStats.appBytes,
                        )
                    )
                }

                val privateAppCache = if (useRoot) {
                    emptySet<ContentItem>()
                } else {
                    setOf(
                        ContentItem(
                            label = R.string.analyzer_storage_content_app_cache_private_label.toCaString(),
                            path = RawPath.build(it.packageInfo.applicationInfo.sourceDir),
                            size = storageStats.appBytes,
                        )
                    )
                }

                val publicAppData = run {
                    setOf(
                        ContentItem(
                            label = R.string.analyzer_storage_content_app_cache_private_label.toCaString(),
                            path = RawPath.build(it.packageInfo.applicationInfo.sourceDir),
                            size = storageStats.appBytes,
                        )
                    )
                }

                val publicAppCache = run {
                    setOf(
                        ContentItem(
                            label = R.string.analyzer_storage_content_app_cache_private_label.toCaString(),
                            path = RawPath.build(it.packageInfo.applicationInfo.sourceDir),
                            size = storageStats.appBytes,
                        )
                    )
                }

//                // TODO on lower APIs we need to calculate this manually
//                @Suppress("NewApi")
//                if (hasApiLevel(31)) baseSize += it.stats.externalCacheBytes

                AppContent.PkgStat(
                    pkg = it,
                    appCode = appCode,
                    appData = emptySet(),
                    appCache = emptySet(),
                    extra = emptySet(),
                )
            }
            .onEach { log(TAG, VERBOSE) { "$it" } }

        val app = AppContent(
            id = storageId.value + ".app",
            spaceUsed = pkgStats.sumOf { it.totalSize },
            pkgStats = pkgStats,
        )
        val media = MediaContent(
            id = storageId.value + ".media",
            spaceUsed = 1024L * 1024 * 1024L * 24,
        )
        val system = SystemContent(
            id = storageId.value + ".system",
            spaceUsed = 1024L * 1024 * 1024L * 11,
        )
        return setOf(app, media, system)
    }

    companion object {
        private val TAG = logTag("Analyzer", "StorageContent", "Scanner")
    }
}