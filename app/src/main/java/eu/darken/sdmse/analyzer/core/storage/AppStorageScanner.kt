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
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.coroutine.SuspendingLazy
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.ReadException
import eu.darken.sdmse.common.files.local.File
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.forensics.OwnerInfo
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.getPrivateDataDirs
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
    @Assisted("useShizuku") private val useShizuku: Boolean,
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

    suspend fun processPkg(
        pkg: Installed,
        topLevelDirs: Set<OwnerInfo>,
    ): Result {
        val appStorStats = try {
            statsManager.queryStatsForPkg(storage.id, pkg)
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to query stats for ${pkg.id} due to $e" }
            null
        }

        val appCodeGroup = when {
            storage.type != DeviceStorage.Type.PRIMARY -> null
            pkg.isSystemApp && !pkg.isUpdatedSystemApp -> null
            else -> {
                val appCode = pkg.applicationInfo?.sourceDir
                    ?.let {
                        when {
                            it.endsWith("base.apk") -> File(it).parent
                            else -> it
                        }
                    }
                    ?.let { LocalPath.build(it) }
                    ?.let { codeDir ->
                        when {
                            useRoot -> codeDir.walkContentItem(gatewaySwitch)
                            appStorStats != null -> ContentItem.fromInaccessible(
                                codeDir,
                                if (pkg.userHandle == currentUser) appStorStats.appBytes else 0L
                            )

                            else -> ContentItem.fromInaccessible(codeDir)
                        }
                    }

                ContentGroup(
                    label = R.string.analyzer_storage_content_app_code_label.toCaString(),
                    contents = setOfNotNull(appCode),
                )
            }
        }

        // TODO: For root we look up /data/media?

        // Android/data/<pkg>
        val dataDirPubs = publicDataPaths.value()
            .map { it.child(pkg.packageName) }
            .mapNotNull { pubData ->
                when {
                    hasApiLevel(33) && !useRoot && !useShizuku -> ContentItem.fromInaccessible(pubData)

                    gatewaySwitch.exists(pubData, type = GatewaySwitch.Type.AUTO) -> try {
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
                .filter { gatewaySwitch.exists(it, type = GatewaySwitch.Type.CURRENT) }
                .map { it.walkContentItem(gatewaySwitch) }

            appStorStats != null -> setOfNotNull(pkg.applicationInfo?.dataDir).map {
                ContentItem.fromInaccessible(
                    LocalPath.build(it),
                    // This is a simplification, because storage stats don't provider more fine grained infos
                    appStorStats.dataBytes - (dataDirPubs.firstOrNull()?.size ?: 0L)
                )
            }

            else -> setOfNotNull(pkg.applicationInfo?.dataDir).map {
                ContentItem.fromInaccessible(LocalPath.build(it))
            }
        }

        val appDataGroup = ContentGroup(
            label = R.string.analyzer_storage_content_app_data_label.toCaString(),
            contents = dataDirPrivs + dataDirPubs,
        )

        // Android/media/<pkg>
        val appMediaGroup = publicMediaPaths.value()
            .map { it.child(pkg.packageName) }
            .filter { gatewaySwitch.exists(it, type = GatewaySwitch.Type.AUTO) }
            .map { it.walkContentItem(gatewaySwitch) }
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?.let { contentSet ->
                ContentGroup(
                    label = R.string.analyzer_storage_content_app_media_label.toCaString(),
                    contents = contentSet,
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

        return Result(
            pkgStat = AppCategory.PkgStat(
                pkg = pkg,
                appCode = appCodeGroup,
                appData = appDataGroup,
                appMedia = appMediaGroup,
                extraData = extraData,
            ),
            consumedTopLevelDirs = consumed,
        )
    }

    data class Result(
        val pkgStat: AppCategory.PkgStat,
        val consumedTopLevelDirs: Set<OwnerInfo>,
    )

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("useRoot") useRoot: Boolean,
            @Assisted("useShizuku") useShizuku: Boolean,
            currentUser: UserHandle2,
            dataAreas: Set<DataArea>,
            storage: DeviceStorage,
        ): AppStorageScanner
    }

    companion object {
        private val TAG = logTag("Analyzer", "Storage", "Scanner", "Pkg")
    }
}