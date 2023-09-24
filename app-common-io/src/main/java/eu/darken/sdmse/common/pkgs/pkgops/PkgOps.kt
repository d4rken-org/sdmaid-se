package eu.darken.sdmse.common.pkgs.pkgops

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager.*
import android.content.pm.SharedLibraryInfo
import android.graphics.drawable.Drawable
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.ModeUnavailableException
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.asFile
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.permissions.Permission.*
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.container.ApkInfo
import eu.darken.sdmse.common.pkgs.container.NormalPkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.features.getInstallerInfo
import eu.darken.sdmse.common.pkgs.getLabel2
import eu.darken.sdmse.common.pkgs.getSharedLibraries2
import eu.darken.sdmse.common.pkgs.pkgops.ipc.PkgOpsClient
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.root.service.runModuleAction
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.shizuku.ShizukuManager
import eu.darken.sdmse.common.shizuku.canUseShizukuNow
import eu.darken.sdmse.common.shizuku.service.runModuleAction
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PkgOps @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val ipcFunnel: IPCFunnel,
    private val userManager: UserManager2,
    private val rootManager: RootManager,
    private val shizukuManager: ShizukuManager,
    private val usageStatsManager: UsageStatsManager,
) : HasSharedResource<Any> {

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope + dispatcherProvider.IO)

    private suspend fun <T> adbOps(action: suspend (PkgOpsClient) -> T): T {
        return shizukuManager.serviceClient.runModuleAction(PkgOpsClient::class.java) { action(it) }
    }

    private suspend fun <T> rootOps(action: suspend (PkgOpsClient) -> T): T {
        return rootManager.serviceClient.runModuleAction(PkgOpsClient::class.java) { action(it) }
    }

    suspend fun getUserNameForUID(uid: Int): String? = rootOps { client ->
        client.getUserNameForUID(uid)
    }

    suspend fun getGroupNameforGID(gid: Int): String? = rootOps { client ->
        client.getGroupNameforGID(gid)
    }

    fun getUIDForUserName(userName: String): Int? = when (val gid = Process.getUidForName(userName)) {
        -1 -> null
        else -> gid
    }

    fun getGIDForGroupName(groupName: String): Int? = when (val gid = Process.getGidForName(groupName)) {
        -1 -> null
        else -> gid
    }

    suspend fun forceStop(packageName: String): Boolean = rootOps {
        it.forceStop(packageName)
    }

    suspend fun queryPkg(pkgName: Pkg.Id, flags: Int, userHandle: UserHandle2): Installed? = ipcFunnel.use {
        val pkgInfo: PackageInfo? = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(pkgName.name, flags)
        } catch (e: NameNotFoundException) {
            log(TAG, VERBOSE) { "queryPkg($pkgName, $flags): null" }
            null
        }

        log(TAG, VERBOSE) { "queryPkg($pkgName, $flags): $pkgInfo" }

        pkgInfo?.let {
            NormalPkg(
                packageInfo = it,
                userHandle = userHandle,
                installerInfo = it.getInstallerInfo(packageManager)
            )
        }
    }

    suspend fun queryPkgs(flags: Int): Collection<Installed> {
        @Suppress("DEPRECATION", "QueryPermissionsNeeded")
        val rawPkgs = ipcFunnel.use { packageManager.getInstalledPackages(flags) }

        val handle = userManager.currentUser().handle
        return ipcFunnel.use {
            rawPkgs.map {
                NormalPkg(
                    packageInfo = it,
                    userHandle = handle,
                    installerInfo = it.getInstallerInfo(packageManager)
                )
            }
        }
    }

    suspend fun queryPkgs(flags: Int, userHandle: UserHandle2): Collection<Installed> {
        val rawPkgs = rootOps { it.getInstalledPackagesAsUserStream(flags, userHandle) }

        return ipcFunnel.use {
            rawPkgs.map {
                NormalPkg(
                    packageInfo = it,
                    userHandle = userHandle,
                    installerInfo = it.getInstallerInfo(packageManager)
                )
            }
        }
    }

    suspend fun isInstalleMaybe(pkg: Pkg.Id, userHandle: UserHandle2): Boolean = try {
        ipcFunnel.use {
            packageManager.getPackageUid(pkg.name, 0)
        }
        true
    } catch (e: NameNotFoundException) {
        false
    }

    suspend fun queryAppInfos(
        pkg: Pkg.Id,
        flags: Int = GET_UNINSTALLED_PACKAGES
    ): ApplicationInfo? = ipcFunnel.use {
        try {
            packageManager.getApplicationInfo(pkg.name, flags)
        } catch (e: NameNotFoundException) {
            log(TAG, WARN) { "queryAppInfos($pkg=pkg,flags=$flags) packageName not found." }
            null
        }
    }

    suspend fun getLabel(pkgId: Pkg.Id): String? = ipcFunnel.use {
        try {
            ipcFunnel.use {
                packageManager.getLabel2(pkgId)
            }
        } catch (e: NameNotFoundException) {
            log(TAG, WARN) { "getLabel(packageName=$pkgId) packageName not found." }
            null
        }
    }

    suspend fun getLabel(applicationInfo: ApplicationInfo): String? = ipcFunnel.use {
        try {
            applicationInfo.loadLabel(packageManager).toString()
        } catch (e: NameNotFoundException) {
            log(TAG, WARN) { "getLabel(applicationInfo=$applicationInfo) packageName not found." }
            null
        }
    }

    suspend fun viewArchive(path: APath, flags: Int = 0): ApkInfo? = ipcFunnel.use {
        // TODO Can we support SAF here?
        val jFile = path.asFile()
        if (!jFile.exists()) return@use null

        packageManager.getPackageArchiveInfo(path.path, flags)?.let {
            ApkInfo(
                id = it.packageName.toPkgId(),
                packageInfo = it,
            )
        }
    }

    suspend fun getIcon(pkg: Pkg.Id): Drawable? {
        val appInfo = queryAppInfos(pkg, GET_UNINSTALLED_PACKAGES)
        return appInfo?.let { getIcon(it) }
    }

    suspend fun getIcon(appInfo: ApplicationInfo): Drawable? = ipcFunnel.use {
        try {
            appInfo.loadIcon(packageManager)
        } catch (e: Exception) {
            log(TAG) { "Failed to get icon ${e.asLog()}" }
            null
        }
    }

    suspend fun getSharedLibraries(
        flags: Int = 0
    ): List<SharedLibraryInfo> = ipcFunnel.use {
        packageManager.getSharedLibraries2(flags)
    }

    suspend fun changePackageState(id: Pkg.Id, enabled: Boolean, mode: Mode = Mode.AUTO) {
        log(TAG, VERBOSE) { "changePackageState($id, enabled=$enabled, mode=$mode)" }
        try {
            if (mode == Mode.NORMAL) throw PkgOpsException("changePackageState($id,$enabled) does not support mode=NORMAL")

            val newState = when (enabled) {
                true -> COMPONENT_ENABLED_STATE_ENABLED
                false -> COMPONENT_ENABLED_STATE_DISABLED_USER
            }

            val opsAction = { opsClient: PkgOpsClient ->
                opsClient.setApplicationEnabledSetting(
                    packageName = id.name,
                    newState = newState,
                    flags = run {
                        @Suppress("NewApi")
                        if (hasApiLevel(30)) SYNCHRONOUS else 0
                    }
                )
            }

            if (shizukuManager.canUseShizukuNow() && (mode == Mode.AUTO || mode == Mode.ADB)) {
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

    suspend fun clearCache(id: Installed.InstallId, mode: Mode = Mode.AUTO) {
        log(TAG) { "clearCache($id, $mode)" }
        try {
            if (mode == Mode.NORMAL) throw PkgOpsException("clearCache($id) does not support mode=NORMAL")

            if (mode == Mode.ADB) throw PkgOpsException("clearCache($id) does not support mode=ADB")

            if (rootManager.canUseRootNow() && (mode == Mode.AUTO || mode == Mode.ROOT)) {
                log(TAG) { "clearCache($id, $mode->ROOT)" }
                rootOps { it.clearCache(id) }
                return
            }

            throw ModeUnavailableException("Mode $mode is unavailable")
        } catch (e: Exception) {
            if (e is ModeUnavailableException) {
                log(TAG, DEBUG) { "clearCache(...): $mode unavailable for $id" }
            } else {
                log(TAG, WARN) { "clearCache($id,$mode) failed: ${e.asLog()}" }
            }
            throw PkgOpsException(message = "clearCache($id, $mode) failed", cause = e)
        }
    }

    suspend fun trimCaches(desiredBytes: Long, storageId: String? = null, mode: Mode = Mode.AUTO) {
        log(TAG) { "trimCaches($desiredBytes, $storageId, $mode)" }
        try {
            if (mode == Mode.NORMAL) throw PkgOpsException("trimCaches($storageId) does not support mode=NORMAL")

            if (shizukuManager.canUseShizukuNow() && (mode == Mode.AUTO || mode == Mode.ADB)) {
                log(TAG) { "trimCaches($desiredBytes, $storageId, $mode->ADB)" }
                adbOps { it.trimCaches(desiredBytes, storageId) }
                return
            }

            if (rootManager.canUseRootNow() && (mode == Mode.AUTO || mode == Mode.ROOT)) {
                log(TAG) { "trimCaches($desiredBytes, $storageId, $mode->ROOT)" }
                rootOps { it.trimCaches(desiredBytes, storageId) }
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


    suspend fun isRunning(id: Installed.InstallId, mode: Mode = Mode.AUTO): Boolean {
        try {
            if (shizukuManager.canUseShizukuNow() && (mode == Mode.AUTO || mode == Mode.ADB)) {
                log(TAG) { "isRunning($id, $mode->ADB)" }
                return adbOps { it.isRunning(id.pkgId) }
            }

            if (rootManager.canUseRootNow() && (mode == Mode.AUTO || mode == Mode.ROOT)) {
                log(TAG) { "isRunning($id, $mode->ROOT)" }
                return rootOps { it.isRunning(id.pkgId) }
            }

            if (PACKAGE_USAGE_STATS.isGranted(context) && (mode == Mode.AUTO || mode == Mode.NORMAL)) {
                log(TAG) { "isRunning($id, $mode->NORMAL)" }
                val now = System.currentTimeMillis()
                val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 60 * 1000, now)
                val stat = stats.find { it.packageName == id.pkgId.name }
                val secondsSinceLastUse = stat?.let { (System.currentTimeMillis() - it.lastTimeUsed) / 1000L }
                log(TAG) { "isRunning($id): ${secondsSinceLastUse}s" }
                return secondsSinceLastUse?.let { it < PULSE_PERIOD_SECONDS } ?: false
            }

            throw ModeUnavailableException("Mode $mode is unavailable")
        } catch (e: Exception) {
            if (e is ModeUnavailableException) {
                log(TAG, DEBUG) { "isRunning(...): $mode unavailable for $id" }
            } else {
                log(TAG, WARN) { "isRunning($id,$mode) failed: ${e.asLog()}" }
            }
            throw PkgOpsException(message = "isRunning($id, $mode) failed", cause = e)
        }
    }

    suspend fun grantPermission(id: Installed.InstallId, permission: Permission, mode: Mode = Mode.AUTO): Boolean {
        try {
            log(TAG) { "grantPermission($id, $permission, $mode)" }
            if (mode == Mode.NORMAL) throw PkgOpsException("grantPermission($id, $permission) does not support mode=NORMAL")

            if (shizukuManager.canUseShizukuNow() && (mode == Mode.AUTO || mode == Mode.ADB)) {
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

    suspend fun setAppOps(
        id: Installed.InstallId,
        key: AppOpsKey,
        value: AppOpsValue,
        mode: Mode = Mode.AUTO
    ): Boolean {
        try {
            log(TAG) { "setAppOps($id, $key, $value, $mode)" }
            if (mode == Mode.NORMAL) throw PkgOpsException("setAppOps($id, $key, $value) does not support mode=NORMAL")

            if (shizukuManager.canUseShizukuNow() && (mode == Mode.AUTO || mode == Mode.ADB)) {
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
        private const val PULSE_PERIOD_SECONDS = 10
        val TAG = logTag("PkgOps")
    }
}