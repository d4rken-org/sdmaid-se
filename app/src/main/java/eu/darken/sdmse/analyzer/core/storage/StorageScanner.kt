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
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.ReadException
import eu.darken.sdmse.common.files.listFiles
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.forensics.OwnerInfo
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.currentPkgs
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
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject


class StorageScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageManager2: StorageManager2,
    private val pkgRepo: PkgRepo,
    private val rootManager: RootManager,
    private val shizukuManager: ShizukuManager,
    private val userManager2: UserManager2,
    private val gatewaySwitch: GatewaySwitch,
    private val fileForensics: FileForensics,
    private val dataAreaManager: DataAreaManager,
    private val appScannerFactory: AppStorageScanner.Factory,
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
            .filter { it.applicationInfo != null }

        updateProgressCount(Progress.Count.Percent(targetPkgs.size))

        val appScanner = appScannerFactory.create(
            useRoot = useRoot,
            useShizuku = useShizuku,
            currentUser = currentUser,
            dataAreas = dataAreas,
            storage = storage,
        )

        val pkgStats = targetPkgs
            .map { pkg ->
                updateProgressSecondary(pkg.label ?: pkg.packageName.toCaString())
                val result = appScanner.processPkg(pkg, topLevelDirs)
                increaseProgress()
                topLevelDirs.removeAll(result.consumedTopLevelDirs)
                result.pkgStat
            }
            .filter { it.totalSize > 0L }
            .associateBy { it.id }

        return if (pkgStats.isNotEmpty()) {
            AppCategory(storageId = storage.id, pkgStats = pkgStats)
        } else {
            null
        }
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

    companion object {
        private val TAG = logTag("Analyzer", "Storage", "Scanner")
    }
}
