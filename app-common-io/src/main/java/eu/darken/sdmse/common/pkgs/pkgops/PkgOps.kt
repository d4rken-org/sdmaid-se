package eu.darken.sdmse.common.pkgs.pkgops

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.TransactionTooLargeException
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.hasCause
import eu.darken.sdmse.common.files.core.DeviceEnvironment
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.AppPkg
import eu.darken.sdmse.common.pkgs.NormalPkg
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.PkgPathInfo
import eu.darken.sdmse.common.pkgs.pkgops.root.PkgOpsClient
import eu.darken.sdmse.common.root.javaroot.JavaRootClient
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.user.UserHandleBB
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PkgOps @Inject constructor(
    private val javaRootClient: JavaRootClient,
    private val ipcFunnel: IPCFunnel,
    private val deviceEnvironment: DeviceEnvironment,
    @AppScope private val appScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) : HasSharedResource<Any> {

    override val sharedResource = SharedResource.createKeepAlive(
        TAG,
        appScope + dispatcherProvider.IO
    )

    private suspend fun <T> rootOps(action: (PkgOpsClient) -> T): T {
        sharedResource.addParent(javaRootClient)
        return javaRootClient.runModuleAction(PkgOpsClient::class.java) {
            return@runModuleAction action(it)
        }
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

    suspend fun queryPkg(pkgName: String, flags: Int = 0): Pkg? = ipcFunnel.use {
        log(TAG, VERBOSE) { "queryPkg($pkgName, $flags)..." }
        val pkgInfo: PackageInfo? = try {
            packageManager.getPackageInfo(pkgName, flags)
        } catch (e: PackageManager.NameNotFoundException) {
            log(TAG, VERBOSE) { "Pkg was not found, trying list-based lookup" }
            packageManager.getInstalledPackages(flags).singleOrNull { it.packageName == pkgName }
        }

        log(TAG, VERBOSE) { "queryPkg($pkgName, $flags): $pkgInfo" }
        pkgInfo?.let { AppPkg(it) }
    }

    suspend fun listPkgs(flags: Int = 0): Collection<Pkg> = ipcFunnel.use {
        log(TAG, VERBOSE) { "listPkgs($flags)..." }
        try {
            packageManager.getInstalledPackages(flags)
                .map { AppPkg(it) }
                .toList()
                .also { log(TAG, VERBOSE) { "listPkgs($flags): size=${it.size}" } }
        } catch (e: Exception) {
            if (e.hasCause(TransactionTooLargeException::class)) {
                throw RuntimeException("${TAG}:listPkgs($flags):TransactionTooLargeException")
            }
            throw RuntimeException(e)
        }
    }

    suspend fun queryAppInfos(
        pkg: String,
        flags: Int = PackageManager.GET_UNINSTALLED_PACKAGES
    ): ApplicationInfo? = ipcFunnel.use {
        try {
            packageManager.getApplicationInfo(pkg, flags)
        } catch (e: PackageManager.NameNotFoundException) {
            log(TAG, WARN) { "queryAppInfos($pkg=pkg,flags=$flags) packageName not found." }
            null
        }
    }

    suspend fun getLabel(packageName: String): String? = ipcFunnel.use {
        try {
            packageManager
                .getApplicationInfo(packageName, PackageManager.GET_UNINSTALLED_PACKAGES)
                .loadLabel(packageManager)
                .toString()
        } catch (e: PackageManager.NameNotFoundException) {
            log(TAG, WARN) { "getLabel(packageName=$packageName) packageName not found." }
            null
        }
    }

    suspend fun getLabel(applicationInfo: ApplicationInfo): String? = ipcFunnel.use {
        try {
            applicationInfo.loadLabel(packageManager).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            log(TAG, WARN) { "getLabel(applicationInfo=$applicationInfo) packageName not found." }
            null
        }
    }

    suspend fun viewArchive(path: String, flags: Int = 0): NormalPkg? = ipcFunnel.use {
        packageManager.getPackageArchiveInfo(path, flags)?.let {
            AppPkg(
                it
            )
        }
    }

    suspend fun getIcon(pkg: String): Drawable? {
        val appInfo = queryAppInfos(pkg, PackageManager.GET_UNINSTALLED_PACKAGES)
        return appInfo?.let { getIcon(it) }
    }

    suspend fun getIcon(appInfo: ApplicationInfo): Drawable? = ipcFunnel.use {
        try {
            appInfo.loadIcon(packageManager)
        } catch (e: Exception) {
            Timber.tag(TAG).d(e)
            null
        }
    }

    fun getPathInfos(packageName: String, userHandle: UserHandleBB): PkgPathInfo {
        val pubPrimary = LocalPath.build(
            deviceEnvironment.getPublicPrimaryStorage(userHandle).localPath,
            "Android",
            "data",
            packageName
        )
        val pubSecondary = deviceEnvironment.getPublicSecondaryStorage(userHandle)
            .map { LocalPath.build(it.localPath, "Android", "data", packageName) }
        return PkgPathInfo(packageName, pubPrimary, pubSecondary)
    }

    companion object {
        val TAG = logTag("PkgOps")
    }
}