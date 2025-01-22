package eu.darken.sdmse.analyzer.core.storage

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.coroutine.SuspendingLazy
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.ReadException
import eu.darken.sdmse.common.files.core.local.File
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.getPrivateDataDirs
import eu.darken.sdmse.common.pkgs.isArchived
import eu.darken.sdmse.common.pkgs.isSystemApp
import eu.darken.sdmse.common.pkgs.isUpdatedSystemApp
import eu.darken.sdmse.common.storage.StorageManager2
import eu.darken.sdmse.common.storage.StorageStatsManager2
import eu.darken.sdmse.common.user.UserHandle2


class AppStorageScanner @AssistedInject constructor(
    private val storageManager2: StorageManager2,
    private val statsManager: StorageStatsManager2,
    private val gatewaySwitch: GatewaySwitch,
    @Assisted("useRoot") private val useRoot: Boolean,
    @Assisted("useAdb") private val useAdb: Boolean,
    @Assisted private val currentUser: UserHandle2,
    @Assisted private val dataAreas: Set<DataArea>,
    @Assisted private val storage: DeviceStorage,
) {

    private val publicPaths = SuspendingLazy<Set<APath>> {
        val mainPath = storageManager2.volumes
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
        setOfNotNull(mainPath)
    }

    private val publicMediaPaths = SuspendingLazy<Set<APath>> {
        publicPaths.value()
            .map { it.child("Android", "media") }
            .filter { gatewaySwitch.exists(it, type = GatewaySwitch.Type.AUTO) }
            .toSet()
    }

    private val publicDataPaths = SuspendingLazy<Set<APath>> {
        publicPaths.value()
            .map { it.child("Android", "data") }
            .filter { gatewaySwitch.exists(it, type = GatewaySwitch.Type.AUTO) }
            .toSet()
    }

    suspend fun process(
        request: Request,
    ): Result {
        val appStorStats = try {
            statsManager.queryStatsForPkg(storage.id, request.pkg)
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to query stats for ${request.pkg.id} due to $e" }
            null
        }

        val appCodeGroup = when {
            storage.type != DeviceStorage.Type.PRIMARY -> null
            request.pkg.isSystemApp && !request.pkg.isUpdatedSystemApp -> null
            request.pkg.isArchived -> null
            else -> request.pkg.applicationInfo?.sourceDir
                ?.let {
                    when {
                        it.endsWith("base.apk") -> File(it).parent
                        else -> it
                    }
                }
                ?.let { LocalPath.build(it) }
                ?.let { codeDir ->
                    if (!request.shallow && useRoot) {
                        try {
                            return@let codeDir.walkContentItem(gatewaySwitch)
                        } catch (e: ReadException) {
                            log(TAG, ERROR) { "Failed to read $codeDir despire root access? ${e.asLog()}" }
                        }
                    }

                    if (appStorStats != null) {
                        return@let ContentItem.fromInaccessible(
                            codeDir,
                            if (request.pkg.userHandle == currentUser) appStorStats.appBytes else 0L
                        )
                    }

                    ContentItem.fromInaccessible(codeDir)
                }
                ?.let {
                    ContentGroup(
                        label = R.string.analyzer_storage_content_app_code_label.toCaString(),
                        contents = setOf(it),
                    )
                }
        }

        // TODO: For root we look up /data/media?

        // Android/data/<request.pkg>
        val dataDirPubs = publicDataPaths.value()
            .map { it.child(request.pkg.packageName) }
            .mapNotNull { pubData ->
                when {
                    request.shallow || (hasApiLevel(33) && !useRoot && !useAdb) -> {
                        ContentItem.fromInaccessible(pubData)
                    }

                    gatewaySwitch.exists(pubData, type = GatewaySwitch.Type.AUTO) -> try {
                        pubData.walkContentItem(gatewaySwitch)
                    } catch (e: ReadException) {
                        ContentItem.fromInaccessible(pubData)
                    }

                    else -> null
                }
            }
            .toSet()

        val dataDirPrivs = run {
            if (storage.type != DeviceStorage.Type.PRIMARY) return@run emptySet()

            if (!request.shallow && dataAreas.any { it.type == DataArea.Type.PRIVATE_DATA }) {
                try {
                    return@run request.pkg
                        .getPrivateDataDirs(dataAreas)
                        .filter { gatewaySwitch.exists(it, type = GatewaySwitch.Type.CURRENT) }
                        .map { it.walkContentItem(gatewaySwitch) }
                } catch (e: ReadException) {
                    log(TAG, ERROR) { "Failed to read private data dirs for $request.pkg: ${e.asLog()}" }
                }
            }

            if (appStorStats != null) {
                return@run setOfNotNull(request.pkg.applicationInfo?.dataDir).map {
                    ContentItem.fromInaccessible(
                        LocalPath.build(it),
                        // This is a simplification, because storage stats don't provide more fine grained infos
                        appStorStats.dataBytes - (dataDirPubs.firstOrNull()?.size ?: 0L)
                    )
                }
            }

            setOfNotNull(request.pkg.applicationInfo?.dataDir).map {
                ContentItem.fromInaccessible(LocalPath.build(it))
            }
        }

        val appDataGroup = ContentGroup(
            label = R.string.analyzer_storage_content_app_data_label.toCaString(),
            contents = dataDirPrivs + dataDirPubs,
        )

        // Android/media/<request.pkg>
        val appMediaGroup = publicMediaPaths.value()
            .map { it.child(request.pkg.packageName) }
            .filter { gatewaySwitch.exists(it, type = GatewaySwitch.Type.AUTO) }
            .mapNotNull { path ->
                try {
                    if (request.shallow) {
                        path.sizeContentItem(gatewaySwitch)
                    } else {
                        path.walkContentItem(gatewaySwitch)
                    }
                } catch (e: ReadException) {
                    null
                }
            }
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?.let {
                ContentGroup(
                    label = R.string.analyzer_storage_content_app_media_label.toCaString(),
                    contents = it,
                )
            }

        val extraDataGroup = request.extraData
            .mapNotNull { path ->
                try {
                    if (request.shallow) {
                        path.sizeContentItem(gatewaySwitch)
                    } else {
                        path.walkContentItem(gatewaySwitch)
                    }
                } catch (e: ReadException) {
                    null
                }
            }
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?.let {
                ContentGroup(
                    label = R.string.analyzer_storage_content_app_extra_label.toCaString(),
                    contents = it,
                )
            }

        return Result(
            pkgStat = AppCategory.PkgStat(
                pkg = request.pkg,
                isShallow = request.shallow,
                appCode = appCodeGroup,
                appData = appDataGroup,
                appMedia = appMediaGroup,
                extraData = extraDataGroup,
            ),
        )
    }

    sealed interface Request {
        val pkg: Installed
        val shallow: Boolean
        val extraData: Collection<APath>

        data class Initial(
            override val pkg: Installed,
            override val extraData: Collection<APath>,
        ) : Request {
            override val shallow: Boolean
                get() = true
        }

        data class Reprocessing(
            val pkgStat: AppCategory.PkgStat,
        ) : Request {
            override val pkg: Installed
                get() = pkgStat.pkg

            override val shallow: Boolean
                get() = false

            override val extraData: Collection<APath>
                get() = pkgStat.extraData?.contents?.map { it.path } ?: emptySet()
        }
    }

    data class Result(
        val pkgStat: AppCategory.PkgStat,
    )

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("useRoot") useRoot: Boolean,
            @Assisted("useAdb") useAdb: Boolean,
            currentUser: UserHandle2,
            dataAreas: Set<DataArea>,
            storage: DeviceStorage,
        ): AppStorageScanner
    }

    companion object {
        private val TAG = logTag("Analyzer", "Storage", "Scanner", "Pkg")
    }
}