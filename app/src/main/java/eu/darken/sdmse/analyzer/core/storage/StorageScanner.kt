package eu.darken.sdmse.analyzer.core.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.analyzer.core.storage.categories.ContentCategory
import eu.darken.sdmse.analyzer.core.storage.categories.MediaCategory
import eu.darken.sdmse.analyzer.core.storage.categories.SystemCategory
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.ReadException
import eu.darken.sdmse.common.files.listFiles
import eu.darken.sdmse.common.files.local.File
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.walk
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.forensics.OwnerInfo
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.currentPkgs
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.isSystemApp
import eu.darken.sdmse.common.pkgs.isUpdatedSystemApp
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.increaseProgress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.shizuku.ShizukuManager
import eu.darken.sdmse.common.shizuku.canUseShizukuNow
import eu.darken.sdmse.common.storage.StorageManager2
import eu.darken.sdmse.common.storage.StorageStatsManager2
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import javax.inject.Inject


class StorageScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageManager2: StorageManager2,
    private val statsManager: StorageStatsManager2,
    private val pkgRepo: PkgRepo,
    private val rootManager: RootManager,
    private val shizukuManager: ShizukuManager,
    private val userManager2: UserManager2,
    private val gatewaySwitch: GatewaySwitch,
    private val fileForensics: FileForensics,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(
        Progress.DEFAULT_STATE.copy(primary = eu.darken.sdmse.common.R.string.general_progress_preparing.toCaString())
    )

    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(50)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private val topLevelDirs = mutableSetOf<OwnerInfo>()

    private var useRoot = false
    private lateinit var currentUser: UserHandle2

    suspend fun scan(storage: DeviceStorage): Collection<ContentCategory> {
        log(TAG) { "scan($storage)" }

        updateProgressPrimary(storage.label)
        updateProgressSecondary(R.string.analyzer_progress_scanning_storage)

        useRoot = false // TODO: rootManager.useRoot()
        currentUser = userManager2.currentUser().handle

        val volume = storageManager2.storageVolumes.singleOrNull { it.uuid == storage.id.internalId }
        log(TAG) { "Target public volume: $volume" }

        return gatewaySwitch.useRes {
            fileForensics.useRes {
                val storageDir = volume?.directory
                    ?.let { LocalPath.build(it) }
                    ?.let { gatewaySwitch.lookup(it, type = GatewaySwitch.Type.AUTO) }

                val folders = storageDir?.lookedUp
                    ?.listFiles(gatewaySwitch)
                    ?.filter { it.name != "Android" }
                    ?.mapNotNull { fileForensics.findOwners(it) }
                    ?.filter { it.areaInfo.type == DataArea.Type.SDCARD }
                    ?.onEach { log(TAG) { "Top level dir: $it" } }
                    ?: emptySet()
                topLevelDirs.clear()
                topLevelDirs.addAll(folders)

                val apps = scanForApps(storage)

                updateProgressSecondary("Scanning media files")
                val media = storageDir
                    ?.let { scanForMedia(storage, it) }
                    ?: MediaCategory(storage.id, emptySet())

                updateProgressSecondary("Scanning system data")
                val system = scanForSystem(storage, apps, media)

                log(TAG) { "Apps: ${apps?.spaceUsed}" }
                log(TAG) { "Media: ${media.spaceUsed}" }
                log(TAG) { "System: ${system?.spaceUsed}" }

                setOfNotNull(apps, media, system)
            }
        }
    }

    private suspend fun scanForApps(storage: DeviceStorage): AppCategory? {
        log(TAG) { "scanForApps($storage)" }
        if (!Permission.PACKAGE_USAGE_STATS.isGranted(context)) {
            log(TAG, WARN) { "Permission PACKAGE_USAGE_STATS is missing, can't scan apps." }
            return AppCategory(
                storageId = storage.id,
                setupIncomplete = true,
                pkgStats = emptyMap()
            )
        }

        updateProgressPrimary(R.string.analyzer_progress_scanning_apps)

        val targetPkgs = pkgRepo.currentPkgs()
            .filter { it.packageName != "android" }
            .filter { it.packageInfo.applicationInfo != null }

        updateProgressCount(Progress.Count.Percent(0, targetPkgs.size))

        val pkgStats = targetPkgs
            .map {
                updateProgressSecondary(it.label ?: it.packageName.toCaString())
                processPkg(storage, it).also { increaseProgress() }
            }
            .filter { it.totalSize > 0L }
            .associateBy { it.id }

        return if (pkgStats.isNotEmpty()) {
            AppCategory(storageId = storage.id, pkgStats = pkgStats)
        } else {
            null
        }
    }

    private suspend fun processPkg(
        storage: DeviceStorage,
        pkg: Installed
    ): AppCategory.PkgStat {
        val appStorStats = statsManager.queryStatsForPkg(storage.id, pkg)

        val appCodeGroup = when {
            storage.type != DeviceStorage.Type.PRIMARY -> null
            pkg.isSystemApp && !pkg.isUpdatedSystemApp -> null
            useRoot -> ContentGroup(
                label = pkg.label
            )

            else -> {
                val appCode = pkg.packageInfo.applicationInfo.sourceDir
                    ?.let {
                        when {
                            it.endsWith("base.apk") -> File(it).parent
                            else -> it
                        }
                    }
                    ?.let {
                        ContentItem.fromInaccessible(
                            LocalPath.build(it),
                            if (pkg.userHandle == currentUser) appStorStats.appBytes else 0L
                        )
                    }

                ContentGroup(
                    label = R.string.analyzer_storage_content_app_code_label.toCaString(),
                    contents = setOfNotNull(appCode),
                )
            }
        }

        // TODO: For root we look up /data/media?
        val publicPath = storageManager2.volumes
            ?.filter { !it.isPrivate }
            ?.singleOrNull { it.fsUuid == storage.id.internalId }
            ?.path
            ?.let { LocalPath.build(it) }
            ?.let {
                when {
                    it.segments.last() == "emulated" -> it.child("${currentUser.handleId}")
                    else -> it
                }
            }

        val canReadAndroidData = !hasApiLevel(33) || rootManager.canUseRootNow() || shizukuManager.canUseShizukuNow()

        // Android/data/<pkg>
        val dataDirPub = publicPath
            ?.let { LocalPath.build(it.path, "Android", "data", pkg.packageName) }
            ?.let { pubData ->
                try {
                    val lookup = gatewaySwitch.lookup(pubData, type = GatewaySwitch.Type.AUTO)

                    if (lookup.fileType == FileType.DIRECTORY && canReadAndroidData) {
                        val children = try {
                            lookup.walk(gatewaySwitch).map { ContentItem.fromLookup(it) }.toList()
                        } catch (e: ReadException) {
                            emptySet()
                        }
                        children.plus(ContentItem.fromLookup(lookup)).toNestedContent().single()
                    } else {
                        ContentItem.fromInaccessible(pubData)
                    }
                } catch (e: ReadException) {
                    ContentItem.fromInaccessible(pubData)
                }
            }


        val dataDirBase = when {
            storage.type != DeviceStorage.Type.PRIMARY -> null
            useRoot -> null
            else -> pkg.packageInfo.applicationInfo.dataDir?.let {
                ContentItem.fromInaccessible(
                    LocalPath.build(it),
                    appStorStats.dataBytes - (dataDirPub?.size ?: 0L)
                )
            }
        }

        val dataDirDe = when {
            storage.type != DeviceStorage.Type.PRIMARY -> null
            useRoot -> null
            else -> pkg.packageInfo.applicationInfo.deviceProtectedDataDir?.let {
                ContentItem.fromInaccessible(LocalPath.build(it))
            }
        }

        val appDataGroup = ContentGroup(
            label = R.string.analyzer_storage_content_app_data_label.toCaString(),
            contents = setOfNotNull(dataDirBase, dataDirDe, dataDirPub),
        )

        // Android/media/<pkg>
        val appMediaGroup = publicPath
            ?.let { LocalPath.build(it.path, "Android", "media", pkg.packageName) }
            ?.let {
                try {
                    gatewaySwitch.lookup(it, type = GatewaySwitch.Type.AUTO)
                } catch (e: ReadException) {
                    null
                }
            }
            ?.let { lookup ->
                val children = if (lookup.fileType == FileType.DIRECTORY) {
                    lookup.walk(gatewaySwitch).map { ContentItem.fromLookup(it) }.toList()
                } else {
                    emptySet()
                }

                children.plus(ContentItem.fromLookup(lookup)).toNestedContent().single()
            }
            ?.let {
                ContentGroup(
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
            .mapNotNull { ownerInfo ->
                try {
                    gatewaySwitch.lookup(ownerInfo.areaInfo.file, type = GatewaySwitch.Type.AUTO).also {
                        consumed.add(ownerInfo)
                    }
                } catch (e: ReadException) {
                    null
                }
            }
            .map { lookup ->
                val children = if (lookup.fileType == FileType.DIRECTORY) {
                    lookup.walk(gatewaySwitch).map { ContentItem.fromLookup(it) }.toList().toNestedContent()
                } else {
                    emptySet()
                }
                children.plus(ContentItem.fromLookup(lookup)).toNestedContent().single()
            }
            .takeIf { it.isNotEmpty() }
            ?.let {
                ContentGroup(
                    label = R.string.analyzer_storage_content_app_extra_label.toCaString(),
                    contents = it,
                )
            }
        topLevelDirs.removeAll(consumed)

        return AppCategory.PkgStat(
            pkg = pkg,
            appCode = appCodeGroup,
            appData = appDataGroup,
            appMedia = appMediaGroup,
            extraData = extraData,
        )
    }

    private suspend fun scanForMedia(storage: DeviceStorage, mediaDir: APathLookup<*>): MediaCategory {
        log(TAG) { "scanForMedia($storage)" }
        updateProgressPrimary(R.string.analyzer_progress_scanning_userfiles)

        val topLevelContents: Collection<ContentItem> = topLevelDirs.mapNotNull { ownerInfo ->
            updateProgressSecondary(ownerInfo.areaInfo.file.userReadablePath)

            val lookup = try {
                gatewaySwitch.lookup(ownerInfo.areaInfo.file, type = GatewaySwitch.Type.AUTO)
            } catch (e: ReadException) {
                log(TAG, ERROR) { "Failed to look up top-level dir ${ownerInfo.areaInfo.file}: ${e.asLog()}" }
                return@mapNotNull null
            }

            val children = if (lookup.fileType == FileType.DIRECTORY) {
                lookup.walk(gatewaySwitch).map { ContentItem.fromLookup(it) }.toList()
            } else {
                emptySet()
            }

            children.plus(ContentItem.fromLookup(lookup)).toNestedContent().single()
        }

        val rootItem = topLevelContents.plus(ContentItem.fromLookup(mediaDir)).toNestedContent().single()

        val group = ContentGroup(
            label = R.string.analyzer_storage_content_type_media_label.toCaString(),
            contents = setOf(rootItem),
        )

        return MediaCategory(
            storageId = storage.id,
            groups = setOf(group),
        )
    }

    private suspend fun scanForSystem(
        storage: DeviceStorage,
        appCategory: AppCategory?,
        mediaCategory: MediaCategory
    ): SystemCategory? {
        log(TAG) { "scanForSystem($storage)" }
        updateProgressPrimary(R.string.analyzer_progress_scanning_system)
        updateProgressSecondary(Progress.DEFAULT_STATE.secondary)

        if (storage.type != DeviceStorage.Type.PRIMARY) {
            log(TAG) { "Not a primary storage: $storage" }
            return null
        }

        val unaccountedFor = storage.spaceUsed - (appCategory?.spaceUsed ?: 0L) - mediaCategory.spaceUsed

        val contentItems = setOf(
            ContentItem.fromInaccessible(LocalPath.build("system")),
            ContentItem.fromInaccessible(LocalPath.build("data"))
        )

        val groups = setOf(
            ContentGroup(
                label = R.string.analyzer_storage_content_type_system_label.toCaString(),
                contents = contentItems
            )
        )

        return SystemCategory(
            storageId = storage.id,
            groups = groups,
            spaceUsedOverride = unaccountedFor,
        )
    }

    companion object {
        private val TAG = logTag("Analyzer", "Storage", "Scanner")
    }
}
