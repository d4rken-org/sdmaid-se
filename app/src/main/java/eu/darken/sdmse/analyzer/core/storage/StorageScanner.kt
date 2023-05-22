package eu.darken.sdmse.analyzer.core.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.StorageStatsManager2
import eu.darken.sdmse.analyzer.core.content.AppContentGroup
import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.analyzer.core.content.MediaContentGroup
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.analyzer.core.storage.categories.ContentCategory
import eu.darken.sdmse.analyzer.core.storage.categories.MediaCategory
import eu.darken.sdmse.analyzer.core.storage.categories.SystemCategory
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.ReadException
import eu.darken.sdmse.common.files.exists
import eu.darken.sdmse.common.files.local.File
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.lookupFiles
import eu.darken.sdmse.common.files.walk
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.forensics.OwnerInfo
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.currentPkgs
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.storage.SAFMapper
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.common.storage.StorageManager2
import eu.darken.sdmse.common.user.UserManager2
import kotlinx.coroutines.flow.toList
import javax.inject.Inject

class StorageScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageEnvironment: StorageEnvironment,
    private val storageManager2: StorageManager2,
    private val statsManager: StorageStatsManager2,
    private val pkgRepo: PkgRepo,
    private val rootManager: RootManager,
    private val userManager2: UserManager2,
    private val gatewaySwitch: GatewaySwitch,
    private val fileForensics: FileForensics,
    private val safMapper: SAFMapper,
) {

    private val topLevelDirs = mutableSetOf<OwnerInfo>()

    suspend fun scan(storage: DeviceStorage): Collection<ContentCategory> {
        log(TAG) { "scan($storage)" }

        val volume = storageManager2.storageVolumes.singleOrNull { it.uuid == storage.id.internalId }
        log(TAG) { "Target public volume: $volume" }

        return gatewaySwitch.useRes {
            fileForensics.useRes {
                val folders = volume?.directory
                    ?.let { LocalPath.build(it) }
                    ?.lookupFiles(gatewaySwitch)
                    ?.mapNotNull { fileForensics.findOwners(it.lookedUp) }
                    ?.filter { it.areaInfo.type == DataArea.Type.SDCARD }
                    ?.onEach { log(TAG) { "Top level dir: $it" } }
                    ?: emptySet()
                topLevelDirs.clear()
                topLevelDirs.addAll(folders)

                val apps = scanForApps(storage)
                val media = scanForMedia(storage)
                val system = scanForSystem(storage)
                setOf(apps, media, system)
            }
        }
    }

    private suspend fun scanForApps(storage: DeviceStorage): AppCategory {
        log(TAG) { "scanForApps($storage)" }

        val useRoot = false // TODO: rootManager.useRoot()

        val pkgStats = pkgRepo.currentPkgs()
            .filter { it.packageInfo.applicationInfo != null }
            .map { pkg ->
                val appStorStats = statsManager.queryStatsForPkg(storage.id, pkg)

                val appCodeGroup = if (storage.type == DeviceStorage.Type.PRIMARY) {
                    if (useRoot) {
                        AppContentGroup(
                            label = pkg.label
                        )
                    } else {
                        val appCode = pkg.packageInfo.applicationInfo.sourceDir
                            ?.let {
                                when {
                                    it.endsWith("base.apk") -> File(it).parent
                                    else -> it
                                }
                            }
                            ?.let { ContentItem(path = LocalPath.build(it)) }
                        AppContentGroup(
                            label = R.string.analyzer_storage_content_app_code_label.toCaString(),
                            contents = setOfNotNull(appCode),
                            groupSizeOverride = appStorStats.appBytes,
                        )
                    }
                } else {
                    null
                }

                val appDataContents = mutableSetOf<ContentItem>()

                if (storage.type == DeviceStorage.Type.PRIMARY) {
                    if (useRoot) {
                        AppContentGroup(
                            label = pkg.label,
                        )
                    } else {
                        pkg.packageInfo.applicationInfo.dataDir
                            ?.let { ContentItem(path = LocalPath.build(it)) }
                            ?.run { appDataContents.add(this) }

                        pkg.packageInfo.applicationInfo.deviceProtectedDataDir
                            ?.let { ContentItem(path = LocalPath.build(it)) }
                            ?.run { appDataContents.add(this) }
                    }
                }

                val publicPath = storageManager2.volumes
                    ?.filter { !it.isPrivate }
                    ?.singleOrNull { it.fsUuid == storage.id.internalId }
                    ?.path?.path?.let { LocalPath.build(it, "0") }

                // Android/data/<pkg>
                val appDataGroup = publicPath
                    ?.let { LocalPath.build(it.path, "Android", "data", pkg.packageName) }
                    ?.takeIf { it.exists(gatewaySwitch) }
                    ?.let { pkgPubDataDir ->

                        val contents = if (!hasApiLevel(33)) {
                            try {
                                pkgPubDataDir.walk(gatewaySwitch).toList()
                            } catch (e: ReadException) {
                                null
                            }
                        } else {
                            null
                        }

                        ContentItem(
                            path = pkgPubDataDir,
                            size = contents?.sumOf { it.size },
                        )
                    }
                    ?.let {
                        AppContentGroup(
                            label = R.string.analyzer_storage_content_app_data_label.toCaString(),
                            contents = setOf(it),
                            groupSizeOverride = appStorStats.dataBytes
                        )
                    }

                // Android/media/<pkg>
                val appMediaGroup = publicPath
                    ?.let { LocalPath.build(it.path, "Android", "media", pkg.packageName) }
                    ?.takeIf { it.exists(gatewaySwitch) }
                    ?.let { pkgPubDataDir ->

                        val contents = try {
                            pkgPubDataDir.walk(gatewaySwitch).toList()
                        } catch (e: ReadException) {
                            emptySet()
                        }

                        ContentItem(
                            path = pkgPubDataDir,
                            children = contents.map { ContentItem(path = it.lookedUp, size = it.size) }
                        )
                    }
                    ?.let {
                        AppContentGroup(
                            label = R.string.analyzer_storage_content_app_media_label.toCaString(),
                            contents = setOf(it),
                        )
                    }

                val consumed = mutableSetOf<OwnerInfo>()
                val extraData = topLevelDirs
                    .filter {
                        val owner = it.getOwner(pkg.id) ?: return@filter false
                        !owner.hasFlag(Marker.Flag.CUSTODIAN) && !owner.hasFlag(Marker.Flag.COMMON)
                    }
                    .map { ownerInfo ->
                        consumed.add(ownerInfo)
                        val folderContent = ownerInfo.areaInfo.file.walk(gatewaySwitch).toList()
                        val sizeSum = folderContent.sumOf { it.size }.takeIf { it != 0L } ?: 3452L
                        ContentItem(
                            path = ownerInfo.areaInfo.file,
                            size = sizeSum,
                        )
                    }
                    .takeIf { it.isNotEmpty() }
                    ?.let {
                        AppContentGroup(
                            label = R.string.analyzer_storage_content_app_extra_label.toCaString(),
                            contents = it,
                        )
                    }
                topLevelDirs.removeAll(consumed)

                pkg.installId to AppCategory.PkgStat(
                    pkg = pkg,
                    appCode = appCodeGroup,
                    appData = appDataGroup,
                    appMedia = appMediaGroup,
                    extraData = extraData,
                )
            }
            .toMap()

        return AppCategory(
            storageId = storage.id,
            spaceUsed = pkgStats.values.sumOf { it.totalSize },
            pkgStats = pkgStats,
        )
    }

    private suspend fun scanForMedia(storage: DeviceStorage): MediaCategory {
        log(TAG) { "scanForMedia($storage)" }
        val topLevelContents = topLevelDirs
            .map { ownerInfo ->
                val children = ownerInfo.areaInfo.file.walk(gatewaySwitch).toList()
                ContentItem(
                    path = ownerInfo.areaInfo.file,
                    children = children.map {
                        ContentItem(path = it.lookedUp)
                    }
                )
            }

        val group = MediaContentGroup(
            label = R.string.analyzer_storage_content_type_media_label.toCaString(),
            contents = topLevelContents,
        )

        return MediaCategory(
            storageId = storage.id,
            groups = setOf(group),
        )
    }

    private suspend fun scanForSystem(storage: DeviceStorage): SystemCategory {
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