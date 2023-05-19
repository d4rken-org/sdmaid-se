package eu.darken.sdmse.analyzer.core.storage

import android.app.usage.StorageStatsManager
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.content.AppContentGroup
import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.analyzer.core.storage.categories.ContentCategory
import eu.darken.sdmse.analyzer.core.storage.categories.MediaCategory
import eu.darken.sdmse.analyzer.core.storage.categories.SystemCategory
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

class StorageScanner @Inject constructor(
    private val storageEnvironment: StorageEnvironment,
    private val storageManager2: StorageManager2,
    private val statsManager: StorageStatsManager,
    private val pkgRepo: PkgRepo,
    private val rootManager: RootManager,
) {


    suspend fun scan(storageId: DeviceStorage.Id): Collection<ContentCategory> {
        log(TAG) { "scan($storageId)" }
//        val storage = storageManager2.volumes.first { it.id }

        val useRoot = rootManager.useRoot()

        val pkgStats = pkgRepo.currentPkgs()
            .filter { it.packageInfo.applicationInfo != null }
            .map { pkg ->
                val storageStats = statsManager.queryStatsForUid(storageId.asUUID, pkg.packageInfo.applicationInfo.uid)

                val appCode = if (useRoot) {
                    AppContentGroup.from(label = pkg.label)
                } else {
                    AppContentGroup.from(
                        label = pkg.label,
                        ContentItem(
                            label = R.string.analyzer_storage_content_app_code_label.toCaString(),
                            path = RawPath.build(pkg.packageInfo.applicationInfo.sourceDir),
                            size = storageStats.appBytes,
                        )
                    )
                }

                val privateData = if (useRoot) {
                    AppContentGroup.from(
                        label = pkg.label,
                    )
                } else {
                    AppContentGroup.from(
                        label = pkg.label,
                        ContentItem(
                            label = R.string.analyzer_storage_content_app_data_private_label.toCaString(),
                            path = RawPath.build(pkg.packageInfo.applicationInfo.sourceDir),
                            size = storageStats.dataBytes,
                        )
                    )
                }

                val publicData = run {
                    AppContentGroup.from(
                        label = pkg.label,
                        ContentItem(
                            path = RawPath.build(pkg.packageInfo.applicationInfo.sourceDir),
                            size = storageStats.cacheBytes,
                        )
                    )
                }

                val extraData = run {
                    AppContentGroup.from(
                        label = pkg.label,
                    )
                }

//                // TODO on lower APIs we need to calculate this manually
//                @Suppress("NewApi")
//                if (hasApiLevel(31)) baseSize += it.stats.externalCacheBytes

                pkg.installId to AppCategory.PkgStat(
                    pkg = pkg,
                    appCode = appCode.takeIf { it.contents.isNotEmpty() },
                    privateData = privateData.takeIf { it.contents.isNotEmpty() },
                    publicData = publicData.takeIf { it.contents.isNotEmpty() },
                    extraData = extraData.takeIf { it.contents.isNotEmpty() },
                )
            }
            .onEach { log(TAG, VERBOSE) { "$it" } }
            .toMap()

        val app = AppCategory(
            storageId = storageId,
            spaceUsed = pkgStats.values.sumOf { it.totalSize },
            pkgStats = pkgStats,
        )
        val media = MediaCategory(
            storageId = storageId,
            spaceUsed = 1024L * 1024 * 1024L * 24,
        )
        val system = SystemCategory(
            storageId = storageId,
            spaceUsed = 1024L * 1024 * 1024L * 11,
        )
        return setOf(app, media, system)
    }

    companion object {
        private val TAG = logTag("Analyzer", "StorageContent", "Scanner")
    }
}