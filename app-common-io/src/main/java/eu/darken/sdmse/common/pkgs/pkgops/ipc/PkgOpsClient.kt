package eu.darken.sdmse.common.pkgs.pkgops.ipc

import android.content.pm.PackageInfo
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.getRootCause
import eu.darken.sdmse.common.ipc.IpcClientModule
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.user.UserHandle2
import kotlinx.coroutines.delay
import java.io.IOException

class PkgOpsClient @AssistedInject constructor(
    @Assisted private val connection: PkgOpsConnection
) : IpcClientModule {

    fun getUserNameForUID(uid: Int): String? = try {
        connection.getUserNameForUID(uid)
    } catch (e: Exception) {
        log(TAG, ERROR) { "getUserNameForUID(uid=$uid) failed: ${e.asLog()}" }
        throw fakeIOException(e.getRootCause())
    }

    fun getGroupNameforGID(gid: Int): String? = try {
        connection.getGroupNameforGID(gid)
    } catch (e: Exception) {
        log(TAG, ERROR) { "getGroupNameforGID(gid=$gid) failed: ${e.asLog()}" }
        throw fakeIOException(e.getRootCause())
    }

    fun forceStop(packageName: String): Boolean = try {
        connection.forceStop(packageName)
    } catch (e: Exception) {
        log(TAG, ERROR) { "forceStop(packageName=$packageName) failed: ${e.asLog()}" }
        throw fakeIOException(e.getRootCause())
    }

    fun isRunning(pkgId: Pkg.Id): Boolean = try {
        connection.isRunning(pkgId.name)
    } catch (e: Exception) {
        log(TAG, ERROR) { "isRunning(pkgId=$pkgId) failed: ${e.asLog()}" }
        throw fakeIOException(e.getRootCause())
    }

    suspend fun clearCache(installId: Installed.InstallId): Boolean = try {
        if (Bugs.isDryRun) {
            log(TAG, WARN) { "DRYRUN: not executing clearCache($installId)" }
            delay(50)
            true
        } else {
            connection.clearCacheAsUser(installId.pkgId.name, installId.userHandle.handleId)
        }
    } catch (e: Exception) {
        log(TAG, ERROR) { "clearCache(installId=$installId) failed: ${e.asLog()}" }
        throw fakeIOException(e.getRootCause())
    }

    suspend fun clearCache(pkgId: Pkg.Id): Boolean = try {
        if (Bugs.isDryRun) {
            log(TAG, WARN) { "DRYRUN: not executing clearCache($pkgId)" }
            delay(50)
            true
        } else {
            connection.clearCache(pkgId.name)
        }
    } catch (e: Exception) {
        log(TAG, ERROR) { "clearCache(pkgId=$pkgId) failed: ${e.asLog()}" }
        throw fakeIOException(e.getRootCause())
    }

    suspend fun trimCaches(desiredBytes: Long, storageId: String? = null): Boolean = try {
        if (Bugs.isDryRun) {
            log(TAG, WARN) { "DRYRUN: not executing trimCaches($desiredBytes, $storageId)" }
            delay(2000)
            true
        } else {
            connection.trimCaches(desiredBytes, storageId)
        }
    } catch (e: Exception) {
        log(TAG, ERROR) { "trimCaches(desiredBytes=$desiredBytes, storageId=$storageId) failed: ${e.asLog()}" }
        throw fakeIOException(e.getRootCause())
    }

    /**
     * Can fail if the amount of packages exceeds the IPC buffer size.
     * android.os.DeadObjectException: Transaction failed on small parcel; remote process probably died
     */
    fun getInstalledPackagesAsUser(flags: Int, userHandle: UserHandle2): List<PackageInfo> = try {
        connection.getInstalledPackagesAsUser(flags, userHandle.handleId)
    } catch (e: Exception) {
        log(TAG, ERROR) { "getInstalledPackagesAsUser(flags=$flags, userHandle=$userHandle) failed: ${e.asLog()}" }
        throw fakeIOException(e.getRootCause())
    }

    fun getInstalledPackagesAsUserStream(flags: Int, userHandle: UserHandle2): List<PackageInfo> = try {
        connection.getInstalledPackagesAsUserStream(flags, userHandle.handleId).toPackageInfos()
    } catch (e: Exception) {
        log(
            TAG,
            ERROR
        ) { "getInstalledPackagesAsUserStream(flags=$flags, userHandle=$userHandle) failed: ${e.asLog()}" }
        throw fakeIOException(e.getRootCause())
    }

    fun setApplicationEnabledSetting(packageName: String, newState: Int, flags: Int): Unit = try {
        connection.setApplicationEnabledSetting(packageName, newState, flags)
    } catch (e: Exception) {
        log(TAG, ERROR) {
            "setApplicationEnabledSetting(packageName=$packageName, newState=$newState, flags=$flags) failed: ${e.asLog()}"
        }
        throw fakeIOException(e.getRootCause())
    }

    private fun fakeIOException(e: Throwable): IOException {
        val gulpExceptionPrefix = "java.io.IOException: "
        val message = when {
            e.message.isNullOrEmpty() -> e.toString()
            e.message?.startsWith(gulpExceptionPrefix) == true -> e.message!!.replace(gulpExceptionPrefix, "")
            else -> ""
        }
        return IOException(message, e.cause)
    }

    fun grantPermission(id: Installed.InstallId, permission: Permission): Boolean = try {
        connection.grantPermission(id.pkgId.name, id.userHandle.handleId, permission.permissionId)
    } catch (e: Exception) {
        log(TAG, ERROR) { "grantPermission(id=$id, permission=$permission) failed: ${e.asLog()}" }
        throw fakeIOException(e.getRootCause())
    }

    fun setAppOps(id: Installed.InstallId, key: String, value: String): Boolean = try {
        connection.setAppOps(id.pkgId.name, id.userHandle.handleId, key, value)
    } catch (e: Exception) {
        log(TAG, ERROR) { "setAppOps(id=$id, key=$key, value=$value) failed: ${e.asLog()}" }
        throw fakeIOException(e.getRootCause())
    }

    @AssistedFactory
    interface Factory {
        fun create(connection: PkgOpsConnection): PkgOpsClient
    }

    companion object {
        val TAG = logTag("PkgOps", "Service", "Client")
    }
}