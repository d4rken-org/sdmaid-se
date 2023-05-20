package eu.darken.sdmse.analyzer.core.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.StorageStatsManager2
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
import eu.darken.sdmse.common.files.local.File
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.currentPkgs
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.common.storage.StorageManager2
import eu.darken.sdmse.common.user.UserManager2
import javax.inject.Inject

class StorageScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageEnvironment: StorageEnvironment,
    private val storageManager2: StorageManager2,
    private val statsManager: StorageStatsManager2,
    private val pkgRepo: PkgRepo,
    private val rootManager: RootManager,
    private val userManager2: UserManager2,
) {

    suspend fun scan(storage: DeviceStorage): Collection<ContentCategory> {
        log(TAG) { "scan($storage)" }
        val apps = scanForApps(storage)
        val media = scanForMedia(storage)
        val system = scanForSystem(storage)
        return setOf(apps, media, system)
    }

    private suspend fun scanForApps(storage: DeviceStorage): AppCategory {
        log(TAG) { "scanForApps($storage)" }

        val useRoot = false // TODO: rootManager.useRoot()

        val pkgStats = pkgRepo.currentPkgs()
            .filter { it.packageInfo.applicationInfo != null }
            .map { pkg ->
                val appStorStats = statsManager.queryStatsForPkg(storage.id, pkg)

                val appCode = if (storage.type == DeviceStorage.Type.PRIMARY) {
                    if (useRoot) {
                        AppContentGroup.from(
                            label =
                            pkg.label
                        )
                    } else {
                        val appCode = pkg.packageInfo.applicationInfo.sourceDir?.let {
                            ContentItem(
                                path = LocalPath.build(it),
                                size = appStorStats.appBytes,
                            )
                        }
                        AppContentGroup(
                            label = R.string.analyzer_storage_content_app_code_label.toCaString(),
                            contents = setOfNotNull(appCode),
                        )
                    }
                } else {
                    null
                }

                val privateData = if (storage.type == DeviceStorage.Type.PRIMARY) {
                    if (useRoot) {
                        AppContentGroup.from(
                            label = pkg.label,
                        )
                    } else {
                        val baseData = pkg.packageInfo.applicationInfo.dataDir?.let {
                            ContentItem(
                                path = LocalPath.build(it),
                                size = appStorStats.dataBytes,
                            )
                        }
                        val deData = pkg.packageInfo.applicationInfo.deviceProtectedDataDir?.let {
                            ContentItem(
                                path = LocalPath.build(it),
                            )
                        }
                        AppContentGroup(
                            label = R.string.analyzer_storage_content_app_data_private_label.toCaString(),
                            contents = setOfNotNull(baseData, deData),
                        )
                    }
                } else {
                    null
                }

                val publicData: AppContentGroup? = run {
                    val targetPublicVolume = storageManager2.volumes
                        ?.filter { !it.isPrivate }
                        ?.singleOrNull { it.fsUuid == storage.id.internalId }
                        ?: return@run null
                    val publicPath = targetPublicVolume.path?.path ?: return@run null

                    context.externalCacheDirs
                        ?.singleOrNull { it.path.startsWith(publicPath) }
                        ?.let { it.parentFile?.parentFile }
                        ?.let { File(it.path, pkg.packageName) }
                        ?.let {
                            @Suppress("NewApi")
                            val size = if (hasApiLevel(31)) {
                                appStorStats.externalCacheBytes
                            } else {
                                // TODO on lower APIs we need to calculate this manually
                                null
                            }
                            val content = ContentItem(
                                path = LocalPath.build(it),
                                size = size
                            )
                            AppContentGroup(
                                label = R.string.analyzer_storage_content_app_data_public_label.toCaString(),
                                contents = setOf(content),
                            )
                        }
                }

                val extraData = run {
                    AppContentGroup.from(
                        label = R.string.analyzer_storage_content_app_extra_label.toCaString(),
                    )
                }

                pkg.installId to AppCategory.PkgStat(
                    pkg = pkg,
                    appCode = appCode?.takeIf { it.groupSize != 0L },
                    privateData = privateData?.takeIf { it.groupSize != 0L },
                    publicData = publicData?.takeIf { it.groupSize != 0L },
                    extraData = extraData.takeIf { it.groupSize != 0L },
                )
            }
            .onEach { log(TAG, VERBOSE) { "$it" } }
            .toMap()

//        val storageStats = statsManager.queryStatsForUser(storageId, userManager2.currentUser().handle)

        return AppCategory(
            storageId = storage.id,
            spaceUsed = pkgStats.values.sumOf { it.totalSize },
            pkgStats = pkgStats,
        )
    }

    suspend fun scanForMedia(storage: DeviceStorage): MediaCategory {
        log(TAG) { "scanForMedia($storage)" }

        return MediaCategory(
            storageId = storage.id,
            spaceUsed = 0,
        )
    }

    suspend fun scanForSystem(storage: DeviceStorage): SystemCategory {
        log(TAG) { "scanForSystem($storage)" }
        if (storage.type == DeviceStorage.Type.SECONDARY) {
            log(TAG) { "No system data here, it's a secondary storage." }
        }
        return SystemCategory(
            storageId = storage.id,
            spaceUsed = 0,
        )
    }

    companion object {
        private val TAG = logTag("Analyzer", "Storage", "Scanner")
    }
}