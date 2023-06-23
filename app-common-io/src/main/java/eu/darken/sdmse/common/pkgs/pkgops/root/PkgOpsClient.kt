package eu.darken.sdmse.common.pkgs.pkgops.root

import android.content.pm.PackageInfo
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.getRootCause
import eu.darken.sdmse.common.ipc.IpcClientModule
import eu.darken.sdmse.common.user.UserHandle2
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

    @AssistedFactory
    interface Factory {
        fun create(connection: PkgOpsConnection): PkgOpsClient
    }

    companion object {
        val TAG = logTag("Root", "Service", "PkgOps", "Client")
    }
}