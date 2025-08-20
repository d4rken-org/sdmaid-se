package eu.darken.sdmse.common.pkgs.pkgops

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.PackageManager.PackageInfoFlags
import android.content.pm.PackageManager.SYNCHRONOUS
import android.content.pm.SharedLibraryInfo
import android.os.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.ModeUnavailableException
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.AdbUnavailableException
import eu.darken.sdmse.common.adb.canUseAdbNow
import eu.darken.sdmse.common.adb.service.runModuleAction
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.asFile
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.permissions.Permission.PACKAGE_USAGE_STATS
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.container.PkgArchive
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.InstallerInfo
import eu.darken.sdmse.common.pkgs.features.getInstallerInfo
import eu.darken.sdmse.common.pkgs.getLabel2
import eu.darken.sdmse.common.pkgs.getSharedLibraries2
import eu.darken.sdmse.common.pkgs.pkgops.ipc.PkgOpsClient
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.RootUnavailableException
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.root.service.runModuleAction
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.keepResourcesAlive
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PkgOps @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    @param:ApplicationContext private val context: Context,
    private val ipcFunnel: IPCFunnel,
    private val rootManager: RootManager,
    private val adbManager: AdbManager,
    private val usageStatsManager: UsageStatsManager,
    private val storageStatsManager: StorageStatsManager,
    private val userManager2: UserManager2,
) : HasSharedResource<Any> {

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope + dispatcherProvider.IO)

    private suspend fun <T> adbOps(action: suspend (PkgOpsClient) -> T): T {
        if (!adbManager.canUseAdbNow()) throw AdbUnavailableException()
        return keepResourcesAlive(adbManager.serviceClient) {
            adbManager.serviceClient.runModuleAction(PkgOpsClient::class.java) { action(it) }
        }
    }

    private suspend fun <T> rootOps(action: suspend (PkgOpsClient) -> T): T {
        if (!rootManager.canUseRootNow()) throw RootUnavailableException()
        return keepResourcesAlive(rootManager.serviceClient) {
            rootManager.serviceClient.runModuleAction(PkgOpsClient::class.java) { action(it) }
        }
    }

    suspend fun forceStop(pkgId: Pkg.Id, mode: Mode = Mode.AUTO): Boolean {
        log(TAG, VERBOSE) { "forceStop($pkgId, mode=$mode)" }
        try {
            val opsAction = { opsClient: PkgOpsClient ->
                opsClient.forceStop(pkgId.name)
            }

            if (adbManager.canUseAdbNow() && (mode == Mode.AUTO || mode == Mode.ADB)) {
                log(TAG) { "forceStop($pkgId, $mode->ADB)" }
                return adbOps { opsAction(it) }

            }

            if (rootManager.canUseRootNow() && (mode == Mode.AUTO || mode == Mode.ROOT)) {
                log(TAG) { "forceStop($pkgId, $mode->ROOT)" }
                return rootOps { opsAction(it) }
            }

            throw ModeUnavailableException("Mode $mode is unavailable")
        } catch (e: Exception) {
            if (e is ModeUnavailableException) {
                log(TAG, DEBUG) { "forceStop(...): $mode unavailable for $pkgId" }
            } else {
                log(TAG, WARN) { "forceStop($pkgId, mode=$mode) failed: $e" }
            }
            throw PkgOpsException(message = "changePackageState($pkgId, $mode) failed", cause = e)
        }
    }

    suspend fun queryPkg(id: Pkg.Id, flags: Long, userHandle: UserHandle2, mode: Mode = Mode.AUTO): PackageInfo? {
        log(TAG) { "queryPkg($id, $flags, $userHandle, $mode)" }
        return when {
            mode == Mode.NORMAL || (mode == Mode.AUTO && userHandle == userManager2.currentUser().handle) -> ipcFunnel.use {
                try {
                    ipcFunnel.use {
                        if (hasApiLevel(33)) {
                            @Suppress("NewApi")
                            packageManager.getPackageInfo(id.name, PackageInfoFlags.of(flags))
                        } else {
                            packageManager.getPackageInfo(id.name, flags.toInt())
                        }
                    }
                } catch (_: NameNotFoundException) {
                    log(TAG, VERBOSE) { "queryPkg($id, $flags): null" }
                    null
                }
            }

            mode == Mode.ROOT || (mode == Mode.AUTO && rootManager.canUseRootNow()) -> {
                rootOps { it.getPackageInfoAsUser(id, flags, userHandle) }
            }

            mode == Mode.ADB || (mode == Mode.AUTO && adbManager.canUseAdbNow()) -> {
                adbOps { it.getPackageInfoAsUser(id, flags, userHandle) }
            }

            else -> {
                throw IllegalStateException("Can't get user specific packages (neither root nor adb) access available")
            }
        }
    }

    suspend fun queryPkgs(
        flags: Long,
        userHandle: UserHandle2? = null,
        mode: Mode = Mode.AUTO
    ): Collection<PackageInfo> {
        log(TAG) { "queryPkgs($flags, $userHandle, $mode)" }
        val targetHandle = userHandle ?: userManager2.currentUser().handle
        return when {
            mode == Mode.NORMAL || (mode == Mode.AUTO && targetHandle == userManager2.currentUser().handle) -> ipcFunnel.use {
                if (hasApiLevel(33)) {
                    @Suppress("NewApi")
                    packageManager.getInstalledPackages(PackageInfoFlags.of(flags))
                } else {
                    packageManager.getInstalledPackages(flags.toInt())
                }
            }

            mode == Mode.ROOT || (mode == Mode.AUTO && rootManager.canUseRootNow()) -> {
                rootOps { it.getInstalledPackagesAsUserStream(flags, targetHandle) }
            }

            mode == Mode.ADB || mode == Mode.AUTO && adbManager.canUseAdbNow() -> {
                adbOps { it.getInstalledPackagesAsUserStream(flags, targetHandle) }
            }

            else -> {
                throw IllegalStateException("Can't get user specific packages (neither root nor adb) access available")
            }
        }
    }

    suspend fun getInstallerData(
        pkgInfos: Collection<PackageInfo>
    ): Map<PackageInfo, InstallerInfo> = ipcFunnel.use {
        pkgInfos.associateWith { it.getInstallerInfo(packageManager) }
    }

    suspend fun isInstalleMaybe(pkg: Pkg.Id, userHandle: UserHandle2): Boolean = try {
        ipcFunnel.use {
            packageManager.getPackageUid(pkg.name, 0)
        }
        true
    } catch (_: NameNotFoundException) {
        false
    }

    suspend fun getLabel(pkgId: Pkg.Id): String? = ipcFunnel.use {
        try {
            ipcFunnel.use {
                packageManager.getLabel2(pkgId)
            }
        } catch (_: NameNotFoundException) {
            log(TAG, WARN) { "getLabel(packageName=$pkgId) packageName not found." }
            null
        }
    }

    suspend fun getLabel(applicationInfo: ApplicationInfo): String? = ipcFunnel.use {
        try {
            applicationInfo.loadLabel(packageManager).toString()
        } catch (_: NameNotFoundException) {
            log(TAG, WARN) { "getLabel(applicationInfo=$applicationInfo) packageName not found." }
            null
        }
    }

    suspend fun viewArchive(path: APath, flags: Int = 0): PkgArchive? = ipcFunnel.use {
        // TODO Can we support SAF here?
        val jFile = path.asFile()
        if (!jFile.exists()) return@use null

        packageManager.getPackageArchiveInfo(path.path, flags)?.let {
            PkgArchive(
                id = it.packageName.toPkgId(),
                packageInfo = it,
            )
        }
    }

    suspend fun getSharedLibraries(
        flags: Int = 0
    ): List<SharedLibraryInfo> = ipcFunnel.use {
        packageManager.getSharedLibraries2(flags)
    }

    suspend fun changePackageState(id: InstallId, enabled: Boolean, mode: Mode = Mode.AUTO) {
        log(TAG, VERBOSE) { "changePackageState($id, enabled=$enabled, mode=$mode)" }
        try {
            if (mode == Mode.NORMAL) throw PkgOpsException("changePackageState($id,$enabled) does not support mode=NORMAL")

            val newState = when (enabled) {
                true -> COMPONENT_ENABLED_STATE_ENABLED
                false -> COMPONENT_ENABLED_STATE_DISABLED_USER
            }

            val opsAction = { opsClient: PkgOpsClient ->
                opsClient.setApplicationEnabledSetting(
                    id = id,
                    newState = newState,
                    flags = run {
                        @Suppress("NewApi")
                        if (hasApiLevel(30)) SYNCHRONOUS else 0
                    }
                )
            }

            if (adbManager.canUseAdbNow() && (mode == Mode.AUTO || mode == Mode.ADB)) {
                log(TAG) { "changePackageState($id, enabled=$enabled, $mode->ADB)" }
                adbOps { opsAction(it) }
                return
            }

            if (rootManager.canUseRootNow() && (mode == Mode.AUTO || mode == Mode.ROOT)) {
                log(TAG) { "changePackageState($id, enabled=$enabled, $mode->ROOT)" }
                rootOps { opsAction(it) }
                return
            }

            throw ModeUnavailableException("Mode $mode is unavailable")
        } catch (e: Exception) {
            if (e is ModeUnavailableException) {
                log(TAG, DEBUG) { "changePackageState(...): $mode unavailable for $id" }
            } else {
                log(TAG, WARN) { "changePackageState($id, enabled=$enabled, mode=$mode) failed: $e" }
            }
            throw PkgOpsException(message = "changePackageState($id, $enabled, $mode) failed", cause = e)
        }
    }

    suspend fun trimCaches(
        desiredBytes: Long,
        storageId: String? = null,
        mode: Mode = Mode.AUTO,
    ) {
        log(TAG) { "trimCaches($desiredBytes, $storageId, $mode)" }
        try {
            if (mode == Mode.NORMAL) throw PkgOpsException("trimCaches($storageId) does not support mode=NORMAL")

            if (adbManager.canUseAdbNow() && (mode == Mode.AUTO || mode == Mode.ADB)) {
                log(TAG) { "trimCaches($desiredBytes, $storageId, $mode->ADB)" }
                adbOps { it.trimCaches(desiredBytes, storageId, dryRun = Bugs.isDryRun) }
                return
            }

            if (rootManager.canUseRootNow() && (mode == Mode.AUTO || mode == Mode.ROOT)) {
                log(TAG) { "trimCaches($desiredBytes, $storageId, $mode->ROOT)" }
                rootOps { it.trimCaches(desiredBytes, storageId, dryRun = Bugs.isDryRun) }
                return
            }

            throw ModeUnavailableException("Mode $mode is unavailable")
        } catch (e: Exception) {
            if (e is ModeUnavailableException) {
                log(TAG, DEBUG) { "trimCaches(...): $mode unavailable" }
            } else {
                log(TAG, WARN) { "trimCaches($desiredBytes, $storageId,$mode) failed: ${e.asLog()}" }
            }
            throw PkgOpsException(message = "trimCaches($desiredBytes, $storageId, $mode) failed", cause = e)
        }
    }

    suspend fun getRunningPackages(mode: Mode = Mode.AUTO): Set<InstallId> {
        try {
            if (adbManager.canUseAdbNow() && (mode == Mode.AUTO || mode == Mode.ADB)) {
                log(TAG, VERBOSE) { "getRunningPackages($mode->ADB)" }
                return adbOps { it.getRunningPackages() }
            }

            if (rootManager.canUseRootNow() && (mode == Mode.AUTO || mode == Mode.ROOT)) {
                log(TAG, VERBOSE) { "getRunningPackages($mode->ROOT)" }
                return rootOps { it.getRunningPackages() }
            }

            if (PACKAGE_USAGE_STATS.isGranted(context) && (mode == Mode.AUTO || mode == Mode.NORMAL)) {
                log(TAG, VERBOSE) { "getRunningPackages($mode->NORMAL)" }
                val now = System.currentTimeMillis()
                val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 240 * 1000, now)
                val currentUser = userManager2.currentUser()
                return stats
                    .groupBy { it.packageName }
                    .map { (_, value) -> value.maxBy { it.lastTimeUsed } }
                    .filter {
                        val secondsSinceLastUse = (System.currentTimeMillis() - it.lastTimeUsed) / 1000L
                        secondsSinceLastUse < 60
                    }
                    .map { InstallId(it.packageName.toPkgId(), currentUser.handle) }
                    .toSet()
            }

            throw ModeUnavailableException("Mode $mode is unavailable")
        } catch (e: Exception) {
            if (e is ModeUnavailableException) {
                log(TAG, DEBUG) { "getRunningPackages(...): $mode unavailable" }
            } else {
                log(TAG, WARN) { "getRunningPackages($mode) failed: ${e.asLog()}" }
            }
            throw PkgOpsException(message = "getRunningPackages($mode) failed", cause = e)
        }
    }

    suspend fun grantPermission(id: InstallId, permission: Permission, mode: Mode = Mode.AUTO): Boolean {
        try {
            log(TAG) { "grantPermission($id, $permission, $mode)" }
            if (mode == Mode.NORMAL) throw PkgOpsException("grantPermission($id, $permission) does not support mode=NORMAL")

            if (adbManager.canUseAdbNow() && (mode == Mode.AUTO || mode == Mode.ADB)) {
                log(TAG) { "grantPermission($id, $permission, $mode->ADB)" }
                return adbOps { it.grantPermission(id, permission) }
            }

            if (rootManager.canUseRootNow() && (mode == Mode.AUTO || mode == Mode.ROOT)) {
                log(TAG) { "grantPermission($id, $permission, $mode->ROOT)" }
                return rootOps { it.grantPermission(id, permission) }

            }

            throw ModeUnavailableException("Mode $mode is unavailable")
        } catch (e: Exception) {
            if (e is ModeUnavailableException) {
                log(TAG, DEBUG) { "grantPermission(...): $mode unavailable for $id" }
            } else {
                log(TAG, WARN) { "grantPermission($id, $permission, $mode) failed: ${e.asLog()}" }
            }
            throw PkgOpsException(message = "grantPermission($id, $permission, $mode) failed", cause = e)
        }
    }

    suspend fun revokePermission(id: InstallId, permission: Permission, mode: Mode = Mode.AUTO): Boolean {
        try {
            log(TAG) { "revokePermission($id, $permission, $mode)" }
            if (mode == Mode.NORMAL) throw PkgOpsException("revokePermission($id, $permission) does not support mode=NORMAL")

            if (adbManager.canUseAdbNow() && (mode == Mode.AUTO || mode == Mode.ADB)) {
                log(TAG) { "revokePermission($id, $permission, $mode->ADB)" }
                return adbOps { it.revokePermission(id, permission) }
            }

            if (rootManager.canUseRootNow() && (mode == Mode.AUTO || mode == Mode.ROOT)) {
                log(TAG) { "revokePermission($id, $permission, $mode->ROOT)" }
                return rootOps { it.revokePermission(id, permission) }

            }

            throw ModeUnavailableException("Mode $mode is unavailable")
        } catch (e: Exception) {
            if (e is ModeUnavailableException) {
                log(TAG, DEBUG) { "grantPermission(...): $mode unavailable for $id" }
            } else {
                log(TAG, WARN) { "grantPermission($id, $permission, $mode) failed: ${e.asLog()}" }
            }
            throw PkgOpsException(message = "grantPermission($id, $permission, $mode) failed", cause = e)
        }
    }

    suspend fun setAppOps(
        id: InstallId,
        key: AppOpsKey,
        value: AppOpsValue,
        mode: Mode = Mode.AUTO
    ): Boolean {
        try {
            log(TAG) { "setAppOps($id, $key, $value, $mode)" }
            if (mode == Mode.NORMAL) throw PkgOpsException("setAppOps($id, $key, $value) does not support mode=NORMAL")

            if (adbManager.canUseAdbNow() && (mode == Mode.AUTO || mode == Mode.ADB)) {
                log(TAG) { "setAppOps($id, $key, $value, $mode->ADB)" }
                return adbOps { it.setAppOps(id, key.raw, value.raw) }
            }

            if (rootManager.canUseRootNow() && (mode == Mode.AUTO || mode == Mode.ROOT)) {
                log(TAG) { "setAppOps($id, $key, $value, $mode->ROOT)" }
                return rootOps { it.setAppOps(id, key.raw, value.raw) }

            }

            throw ModeUnavailableException("Mode $mode is unavailable")
        } catch (e: Exception) {
            if (e is ModeUnavailableException) {
                log(TAG, DEBUG) { "setAppOps(...): $mode unavailable for $id" }
            } else {
                log(TAG, WARN) { "setAppOps($id, $key, $value, $mode) failed: ${e.asLog()}" }
            }
            throw PkgOpsException(message = "setAppOps($id, $key, $value $mode) failed", cause = e)
        }
    }

    data class SizeStats(
        val appBytes: Long,
        val cacheBytes: Long,
        val externalCacheBytes: Long?,
        val dataBytes: Long,
    ) {
        val total: Long
            get() = appBytes + dataBytes + cacheBytes
    }

    suspend fun querySizeStats(
        installId: InstallId,
        storageUUID: UUID = StorageManager.UUID_DEFAULT
    ): SizeStats? = try {
        log(TAG, VERBOSE) { "querySizeStats($installId,$storageUUID)" }
        val stats = storageStatsManager.queryStatsForPackage(
            storageUUID,
            installId.pkgId.name,
            installId.userHandle.asUserHandle(),
        )
        SizeStats(
            appBytes = stats.appBytes,
            cacheBytes = stats.cacheBytes,
            externalCacheBytes = if (hasApiLevel(31)) {
                @Suppress("NewApi")
                stats.externalCacheBytes
            } else null,
            dataBytes = stats.dataBytes,
        ).also { log(TAG, VERBOSE) { "querySizeStats($installId,$storageUUID) -> $it" } }
    } catch (_: NameNotFoundException) {
        null
    } catch (e: SecurityException) {
        log(TAG, WARN) { "Failed to querySizeStats due to lack of permission: $installId: $e" }
        null
    } catch (e: Exception) {
        log(TAG, ERROR) { "Failed to querySizeStats for $installId: ${e.asLog()}" }
        null
    }

    enum class AppOpsKey(val raw: String) {
        GET_USAGE_STATS("GET_USAGE_STATS"),
        MANAGE_EXTERNAL_STORAGE("MANAGE_EXTERNAL_STORAGE"),
        ;
    }

    enum class AppOpsValue(val raw: String) {
        ALLOW("allow"),
        ;
    }

    enum class Mode {
        AUTO, NORMAL, ROOT, ADB
    }

    companion object {
        val TAG = logTag("Pkg", "Ops")
    }
}