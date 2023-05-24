package eu.darken.sdmse.analyzer.core.storage

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
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.files.exists
import eu.darken.sdmse.common.files.local.File
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.lookup
import eu.darken.sdmse.common.files.lookupFiles
import eu.darken.sdmse.common.files.walk
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.forensics.OwnerInfo
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.currentPkgs
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.isSystemApp
import eu.darken.sdmse.common.pkgs.isUpdatedSystemApp
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.storage.SAFMapper
import eu.darken.sdmse.common.storage.StorageManager2
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import javax.inject.Inject

class StorageScanner @Inject constructor(
    private val storageManager2: StorageManager2,
    private val statsManager: StorageStatsManager2,
    private val pkgRepo: PkgRepo,
    private val rootManager: RootManager,
    private val userManager2: UserManager2,
    private val gatewaySwitch: GatewaySwitch,
    private val fileForensics: FileForensics,
    private val safMapper: SAFMapper,
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
                val folders = volume?.directory
                    ?.let { LocalPath.build(it) }
                    ?.lookupFiles(gatewaySwitch)
                    ?.filter { it.name != "Android" }
                    ?.mapNotNull { fileForensics.findOwners(it.lookedUp) }
                    ?.filter { it.areaInfo.type == DataArea.Type.SDCARD }
                    ?.onEach { log(TAG) { "Top level dir: $it" } }
                    ?: emptySet()
                topLevelDirs.clear()
                topLevelDirs.addAll(folders)

                val apps = scanForApps(storage)

                updateProgressSecondary("Scanning media files")
                val media = scanForMedia(storage)

                updateProgressSecondary("Scanning system data")
                val system = scanForSystem(storage, apps, media)

                log(TAG) { "Apps: ${apps.spaceUsed}" }
                log(TAG) { "Media: ${media.spaceUsed}" }
                log(TAG) { "System: ${system.spaceUsed}" }

                setOf(apps, media, system)
            }
        }
    }

    private suspend fun scanForApps(storage: DeviceStorage): AppCategory {
        log(TAG) { "scanForApps($storage)" }
        updateProgressPrimary(R.string.analyzer_progress_scanning_apps)

        val pkgStats = pkgRepo.currentPkgs()
            .filter { it.packageName != "android" }
            .filter { it.packageInfo.applicationInfo != null }
            .associate {
                updateProgressSecondary(it.label ?: it.packageName.toCaString())
                it.installId to processPkg(storage, it)
            }

        return AppCategory(
            storageId = storage.id,
            spaceUsed = pkgStats.values.sumOf { it.totalSize },
            pkgStats = pkgStats,
        )
    }

    private suspend fun processPkg(
        storage: DeviceStorage,
        pkg: Installed
    ): AppCategory.PkgStat {
        val appStorStats = statsManager.queryStatsForPkg(storage.id, pkg)

        val appCodeGroup = when {
            storage.type != DeviceStorage.Type.PRIMARY -> null
            pkg.isSystemApp && !pkg.isUpdatedSystemApp -> null
            useRoot -> AppContentGroup(
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
                    ?.let { ContentItem.fromInaccessible(LocalPath.build(it)) }

                AppContentGroup(
                    label = R.string.analyzer_storage_content_app_code_label.toCaString(),
                    contents = setOfNotNull(appCode),
                    groupSizeOverride = if (pkg.userHandle == currentUser) appStorStats.appBytes else 0L,
                )
            }
        }

        val dataDirBase = when {
            storage.type != DeviceStorage.Type.PRIMARY -> null
            useRoot -> null
            else -> pkg.packageInfo.applicationInfo.dataDir?.let {
                ContentItem.fromInaccessible(LocalPath.build(it))
            }
        }

        val dataDirDe = when {
            storage.type != DeviceStorage.Type.PRIMARY -> null
            useRoot -> null
            else -> pkg.packageInfo.applicationInfo.deviceProtectedDataDir?.let {
                ContentItem.fromInaccessible(LocalPath.build(it))
            }
        }

        val publicPath = storageManager2.volumes
            ?.filter { !it.isPrivate }
            ?.singleOrNull { it.fsUuid == storage.id.internalId }
            ?.path?.path?.let { LocalPath.build(it, "0") }

        // Android/data/<pkg>
        val dataDirPub = publicPath
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

                if (contents != null) {
                    val lookup = pkgPubDataDir.lookup(gatewaySwitch)
                    val children = contents.map { ContentItem.fromLookup(it) }.toNesting()
                    ContentItem.fromLookup(
                        lookup = lookup,
                        children = children,
                    )
                } else {
                    ContentItem.fromInaccessible(pkgPubDataDir)
                }
            }

        val appDataGroup = AppContentGroup(
            label = R.string.analyzer_storage_content_app_data_label.toCaString(),
            contents = setOfNotNull(dataDirBase, dataDirDe, dataDirPub),
            groupSizeOverride = appStorStats.dataBytes
        )

        // Android/media/<pkg>
        val appMediaGroup = publicPath
            ?.let { LocalPath.build(it.path, "Android", "media", pkg.packageName) }
            ?.takeIf { it.exists(gatewaySwitch) }
            ?.let { pubMediaDir ->
                val lookup = pubMediaDir.lookup(gatewaySwitch)
                val children = pubMediaDir.walk(gatewaySwitch).map { ContentItem.fromLookup(it) }.toList().toNesting()
                ContentItem.fromLookup(lookup, children)
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
                val lookup = ownerInfo.areaInfo.file.lookup(gatewaySwitch)
                val children = ownerInfo.areaInfo.file.walk(gatewaySwitch)
                    .map { ContentItem.fromLookup(it) }
                    .toList().toNesting()
                ContentItem.fromLookup(lookup, children)
            }
            .takeIf { it.isNotEmpty() }
            ?.let {
                AppContentGroup(
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

    private suspend fun scanForMedia(storage: DeviceStorage): MediaCategory {
        log(TAG) { "scanForMedia($storage)" }
        updateProgressPrimary(R.string.analyzer_progress_scanning_userfiles)

        val topLevelContents = topLevelDirs.map { ownerInfo ->
            updateProgressSecondary(ownerInfo.areaInfo.file.userReadablePath)

            val lookup = ownerInfo.areaInfo.file.lookup(gatewaySwitch)
            val children = lookup.walk(gatewaySwitch)
                .map { ContentItem.fromLookup(it) }
                .toList().toNesting()
            ContentItem.fromLookup(lookup, children)
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

    private suspend fun scanForSystem(
        storage: DeviceStorage,
        appCategory: AppCategory,
        mediaCategory: MediaCategory
    ): SystemCategory {
        log(TAG) { "scanForSystem($storage)" }
        updateProgressPrimary(R.string.analyzer_progress_scanning_system)
        updateProgressSecondary(Progress.DEFAULT_STATE.secondary)

        if (storage.type != DeviceStorage.Type.PRIMARY) {
            log(TAG) { "Not a primary storage: $storage" }
            return SystemCategory(storageId = storage.id, spaceUsed = 0)
        }

        val unaccountedFor = storage.spaceUsed - appCategory.spaceUsed - mediaCategory.spaceUsed

        return SystemCategory(
            storageId = storage.id,
            spaceUsed = unaccountedFor,
        )
    }

    companion object {
        private val TAG = logTag("Analyzer", "Storage", "Scanner")
    }
}

internal fun Collection<ContentItem>.toNesting(): Collection<ContentItem> {
    val workList = this.sortedByDescending { it.path.segments.size }.toMutableList()

    val topLevel = mutableListOf<ContentItem>()

    val parentIndexMap = mutableMapOf<Segments, Int>()

    for ((index, item) in workList.withIndex()) {
        val parentSegs = item.path.segments.subList(0, item.path.segments.size - 1)

        val parentIndex = parentIndexMap[parentSegs]
            ?: workList.indexOfFirst { it.path.segments == parentSegs }.also { parentIndexMap[parentSegs] = it }

        if (parentIndex != -1) {
            val parent = workList[parentIndex]

            val updatedParent = parent.copy(
                children = (parent.children ?: emptySet()).plus(item)
            )
            workList[parentIndex] = updatedParent
        } else {
            topLevel.add(item)
        }
    }

    return topLevel
}

