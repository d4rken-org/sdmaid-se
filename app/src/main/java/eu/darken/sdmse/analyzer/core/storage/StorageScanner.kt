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
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.areas.hasFlags
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.ReadException
import eu.darken.sdmse.common.files.exists
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
import eu.darken.sdmse.common.pkgs.getPrivateDataDirs
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
    private val dataAreaManager: DataAreaManager,
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
    private var useShizuku = false
    private var dataAreas = mutableSetOf<DataArea>()
    private lateinit var currentUser: UserHandle2

    suspend fun scan(storage: DeviceStorage): Collection<ContentCategory> {
        log(TAG) { "scan($storage)" }

        updateProgressPrimary(storage.label)
        updateProgressSecondary(R.string.analyzer_progress_scanning_storage)

        useRoot = rootManager.canUseRootNow()
        useShizuku = shizukuManager.canUseShizukuNow()
        currentUser = userManager2.currentUser().handle

        val volume = storageManager2.storageVolumes.singleOrNull { it.uuid == storage.id.internalId }
        dataAreaManager.currentAreas()
            .filter { it.hasFlags(DataArea.Flag.PRIMARY) == volume?.isPrimary }
            .let {
                dataAreas.clear()
                dataAreas.addAll(it)
            }

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

        updateProgressCount(Progress.Count.Percent(targetPkgs.size))

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
            else -> {
                val appCode = pkg.packageInfo.applicationInfo.sourceDir
                    ?.let {
                        when {
                            it.endsWith("base.apk") -> File(it).parent
                            else -> it
                        }
                    }
                    ?.let { LocalPath.build(it) }
                    ?.let { codeDir ->
                        if (useRoot) {
                            codeDir.walkContentItem(gatewaySwitch)
                        } else {
                            ContentItem.fromInaccessible(
                                codeDir,
                                if (pkg.userHandle == currentUser) appStorStats.appBytes else 0L
                            )
                        }
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

        // Android/data/<pkg>
        val dataDirPubs = setOfNotNull(publicPath)
            .map { LocalPath.build(it.path, "Android", "data", pkg.packageName) }
            .mapNotNull { pubData ->
                when {
                    hasApiLevel(33) && !useRoot && !useShizuku -> ContentItem.fromInaccessible(pubData)

                    pubData.exists(gatewaySwitch) -> try {
                        pubData.walkContentItem(gatewaySwitch)
                    } catch (e: ReadException) {
                        ContentItem.fromInaccessible(pubData)
                    }

                    else -> null
                }
            }
            .toSet()

        val dataDirPrivs = when {
            storage.type != DeviceStorage.Type.PRIMARY -> emptySet()
            dataAreas.any { it.type == DataArea.Type.PRIVATE_DATA } -> pkg
                .getPrivateDataDirs(dataAreas)
                .filter { it.exists(gatewaySwitch) }
                .map { it.walkContentItem(gatewaySwitch) }

            else -> setOf(
                ContentItem.fromInaccessible(
                    LocalPath.build(pkg.packageInfo.applicationInfo.dataDir),
                    // This is a simplification, because storage stats don't provider more fine grained infos
                    appStorStats.dataBytes - (dataDirPubs.firstOrNull()?.size ?: 0L)
                )
            )
        }

        val appDataGroup = ContentGroup(
            label = R.string.analyzer_storage_content_app_data_label.toCaString(),
            contents = dataDirPrivs + dataDirPubs,
        )

        // Android/media/<pkg>
        val appMediaGroup = publicPath
            ?.let { LocalPath.build(it.path, "Android", "media", pkg.packageName) }
            ?.takeIf { it.exists(gatewaySwitch) }
            ?.walkContentItem(gatewaySwitch)
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
                    ownerInfo.areaInfo.file.walkContentItem(gatewaySwitch).also {
                        consumed.add(ownerInfo)
                    }
                } catch (e: ReadException) {
                    null
                }
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

            try {
                ownerInfo.areaInfo.file.walkContentItem(gatewaySwitch)
            } catch (e: ReadException) {
                log(TAG, ERROR) { "Failed to look up top-level dir ${ownerInfo.areaInfo.file}: ${e.asLog()}" }
                return@mapNotNull null
            }
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

    private suspend fun APath.walkContentItem(gatewaySwitch: GatewaySwitch): ContentItem {
        log(TAG, VERBOSE) { "Walking content items for $this" }
        val lookup = gatewaySwitch.lookup(this, type = GatewaySwitch.Type.AUTO)

        return if (lookup.fileType == FileType.DIRECTORY) {
            val children = try {
                this.walk(gatewaySwitch).map { ContentItem.fromLookup(it) }.toList()
            } catch (e: ReadException) {
                emptySet()
            }

            children.plus(ContentItem.fromLookup(lookup)).toNestedContent().single()
        } else {
            ContentItem.fromLookup(lookup)
        }
    }

    companion object {
        private val TAG = logTag("Analyzer", "Storage", "Scanner")
    }
}
