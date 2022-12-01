package eu.darken.sdmse.common.pkgs.pkgops.root

import android.content.pm.PackageInfo
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.getRootCause
import eu.darken.sdmse.common.files.core.local.root.ClientModule
import eu.darken.sdmse.common.user.UserHandle2
import timber.log.Timber
import java.io.IOException

class PkgOpsClient @AssistedInject constructor(
    @Assisted private val connection: PkgOpsConnection
) : ClientModule {

    fun getUserNameForUID(uid: Int): String? = try {
        connection.getUserNameForUID(uid)
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "getUserNameForUID(uid=$uid) failed.")
        throw fakeIOException(e.getRootCause())
    }

    fun getGroupNameforGID(gid: Int): String? = try {
        connection.getGroupNameforGID(gid)
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "getGroupNameforGID(gid=$gid) failed.")
        throw fakeIOException(e.getRootCause())
    }

    fun forceStop(packageName: String): Boolean = try {
        connection.forceStop(packageName)
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "forceStop(packageName=$packageName) failed.")
        throw fakeIOException(e.getRootCause())
    }

    fun getInstalledPackagesAsUser(flags: Int, userHandle: UserHandle2): List<PackageInfo> = try {
        connection.getInstalledPackagesAsUser(flags, userHandle.handleId)
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "getInstalledPackagesAsUser(flags=$flags, userHandle=$userHandle) failed.")
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

    companion object {
        val TAG = logTag("Root", "Java", "PkgOps", "Client")
    }

    @AssistedFactory
    interface Factory {
        fun create(connection: PkgOpsConnection): PkgOpsClient
    }
}