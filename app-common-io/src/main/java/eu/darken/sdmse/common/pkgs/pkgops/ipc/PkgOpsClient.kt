package eu.darken.sdmse.common.pkgs.pkgops.ipc

import android.content.pm.PackageInfo
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.ipc.IpcClientModule
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.user.UserHandle2

class PkgOpsClient @AssistedInject constructor(
    @Assisted private val connection: PkgOpsConnection
) : IpcClientModule {

    fun getUserNameForUID(uid: Int): String? = try {
        connection.getUserNameForUID(uid)
    } catch (e: Exception) {
        throw e.unwrapPropagation().also {
            log(TAG, ERROR) { "getUserNameForUID(uid=$uid) failed: ${it.asLog()}" }
        }
    }

    fun getGroupNameforGID(gid: Int): String? = try {
        connection.getGroupNameforGID(gid)
    } catch (e: Exception) {
        throw e.unwrapPropagation().also {
            log(TAG, ERROR) { "getGroupNameforGID(gid=$gid) failed: ${it.asLog()}" }
        }
    }

    fun forceStop(packageName: String): Boolean = try {
        connection.forceStop(packageName)
    } catch (e: Exception) {
        throw e.unwrapPropagation().also {
            log(TAG, ERROR) { "forceStop(packageName=$packageName) failed: ${it.asLog()}" }
        }
    }

    fun isRunning(pkgId: Pkg.Id): Boolean = try {
        connection.isRunning(pkgId.name)
    } catch (e: Exception) {
        throw e.unwrapPropagation().also {
            log(TAG, ERROR) { "isRunning(pkgId=$pkgId) failed: ${it.asLog()}" }
        }
    }

    suspend fun clearCache(installId: Installed.InstallId, dryRun: Boolean): Boolean = try {
        connection.clearCacheAsUser(installId.pkgId.name, installId.userHandle.handleId, dryRun)
    } catch (e: Exception) {
        throw e.unwrapPropagation().also {
            log(TAG, ERROR) { "clearCache(installId=$installId) failed: ${it.asLog()}" }
        }
    }

    suspend fun clearCache(pkgId: Pkg.Id, dryRun: Boolean): Boolean = try {
        connection.clearCache(pkgId.name, dryRun)
    } catch (e: Exception) {
        throw e.unwrapPropagation().also {
            log(TAG, ERROR) { "clearCache(pkgId=$pkgId) failed: ${it.asLog()}" }
        }
    }

    suspend fun trimCaches(desiredBytes: Long, storageId: String? = null, dryRun: Boolean): Boolean = try {
        connection.trimCaches(desiredBytes, storageId, dryRun)
    } catch (e: Exception) {
        throw e.unwrapPropagation().also {
            log(TAG, ERROR) { "trimCaches(desiredBytes=$desiredBytes, storageId=$storageId) failed: ${it.asLog()}" }
        }
    }

    /**
     * Can fail if the amount of packages exceeds the IPC buffer size.
     * android.os.DeadObjectException: Transaction failed on small parcel; remote process probably died
     */
    fun getInstalledPackagesAsUser(flags: Long, userHandle: UserHandle2): List<PackageInfo> = try {
        connection.getInstalledPackagesAsUser(flags, userHandle.handleId)
    } catch (e: Exception) {
        throw e.unwrapPropagation().also {
            log(TAG, ERROR) { "getInstalledPackagesAsUser(flags=$flags, userHandle=$userHandle) failed: ${it.asLog()}" }
        }
    }

    fun getInstalledPackagesAsUserStream(flags: Long, userHandle: UserHandle2): List<PackageInfo> = try {
        connection.getInstalledPackagesAsUserStream(flags, userHandle.handleId).toPackageInfos()
    } catch (e: Exception) {
        throw e.unwrapPropagation().also {
            log(TAG, ERROR) {
                "getInstalledPackagesAsUserStream(flags=$flags, userHandle=$userHandle) failed: ${it.asLog()}"
            }
        }
    }

    fun setApplicationEnabledSetting(packageName: String, newState: Int, flags: Int): Unit = try {
        connection.setApplicationEnabledSetting(packageName, newState, flags)
    } catch (e: Exception) {
        throw e.unwrapPropagation().also {
            log(TAG, ERROR) {
                "setApplicationEnabledSetting(packageName=$packageName, newState=$newState, flags=$flags) failed: ${it.asLog()}"
            }
        }
    }

    fun grantPermission(id: Installed.InstallId, permission: Permission): Boolean = try {
        connection.grantPermission(id.pkgId.name, id.userHandle.handleId, permission.permissionId)
    } catch (e: Exception) {
        throw e.unwrapPropagation().also {
            log(TAG, ERROR) { "grantPermission(id=$id, permission=$permission) failed: ${it.asLog()}" }
        }
    }

    fun revokePermission(id: Installed.InstallId, permission: Permission): Boolean = try {
        connection.revokePermission(id.pkgId.name, id.userHandle.handleId, permission.permissionId)
    } catch (e: Exception) {
        throw e.unwrapPropagation().also {
            log(TAG, ERROR) { "revokePermission(id=$id, permission=$permission) failed: ${it.asLog()}" }
        }
    }

    fun setAppOps(id: Installed.InstallId, key: String, value: String): Boolean = try {
        connection.setAppOps(id.pkgId.name, id.userHandle.handleId, key, value)
    } catch (e: Exception) {
        throw e.unwrapPropagation().also {
            log(TAG, ERROR) { "setAppOps(id=$id, key=$key, value=$value) failed: ${it.asLog()}" }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(connection: PkgOpsConnection): PkgOpsClient
    }

    companion object {
        val TAG = logTag("Pkg", "Ops", "Service", "Client")
    }
}