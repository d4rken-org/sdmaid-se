package eu.darken.sdmse.common.pkgs.pkgops

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager.*
import android.content.pm.SharedLibraryInfo
import android.graphics.drawable.Drawable
import android.os.Process
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
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.container.ApkInfo
import eu.darken.sdmse.common.pkgs.container.NormalPkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.features.getInstallerInfo
import eu.darken.sdmse.common.pkgs.getSharedLibraries2
import eu.darken.sdmse.common.pkgs.pkgops.ipc.PkgOpsClient
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.service.RootServiceClient
import eu.darken.sdmse.common.root.service.runModuleAction
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PkgOps @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val rootServiceClient: RootServiceClient,
    private val ipcFunnel: IPCFunnel,
    private val userManager: UserManager2,
    private val rootManager: RootManager,
) : HasSharedResource<Any> {

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope + dispatcherProvider.IO)

    private suspend fun <T> rootOps(action: (PkgOpsClient) -> T): T {
        return rootServiceClient.runModuleAction(PkgOpsClient::class.java) { action(it) }
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

    suspend fun getLabel(packageName: String): String? = ipcFunnel.use {
        try {
            packageManager
                .getApplicationInfo(packageName, GET_UNINSTALLED_PACKAGES)
                .loadLabel(packageManager)
                .toString()
        } catch (e: NameNotFoundException) {
            log(TAG, WARN) { "getLabel(packageName=$packageName) packageName not found." }
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

    suspend fun changePackageState(id: Pkg.Id, enabled: Boolean) {
        log(TAG, VERBOSE) { "changePackageState($id, enabled=$enabled)" }
        val newState = when (enabled) {
            true -> COMPONENT_ENABLED_STATE_ENABLED
            false -> COMPONENT_ENABLED_STATE_DISABLED_USER
        }
        rootOps {
            it.setApplicationEnabledSetting(
                packageName = id.name,
                newState = newState,
                flags = kotlin.run {
                    @Suppress("NewApi")
                    if (hasApiLevel(30)) SYNCHRONOUS else 0
                }
            )
        }
    }

    companion object {
        val TAG = logTag("PkgOps")
    }
}