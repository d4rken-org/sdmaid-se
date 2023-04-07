package eu.darken.sdmse.common.pkgs.pkgops.root

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.getInstalledPackagesAsUser
import eu.darken.sdmse.common.pkgs.pkgops.LibcoreTool
import eu.darken.sdmse.common.user.UserHandle2
import java.lang.reflect.Method
import javax.inject.Inject


class PkgOpsHost @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libcoreTool: LibcoreTool,
) : PkgOpsConnection.Stub() {

    override fun getUserNameForUID(uid: Int): String? = try {
        libcoreTool.getNameForUid(uid)
    } catch (e: Exception) {
        log(TAG, ERROR) { "getUserNameForUID(uid=$uid) failed." }
        throw wrapPropagating(e)
    }

    override fun getGroupNameforGID(gid: Int): String? = try {
        libcoreTool.getNameForGid(gid)
    } catch (e: Exception) {
        log(TAG, ERROR) { "getGroupNameforGID(gid=$gid) failed." }
        throw wrapPropagating(e)
    }

    override fun forceStop(packageName: String): Boolean = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val forceStopPackage: Method = am.javaClass.getDeclaredMethod("forceStopPackage", String::class.java)
        forceStopPackage.isAccessible = true
        forceStopPackage.invoke(am, packageName)
        true
    } catch (e: Exception) {
        log(TAG, ERROR) { "forceStop(packageName=$packageName) failed." }
        throw wrapPropagating(e)
    }

    override fun getInstalledPackagesAsUser(flags: Int, handleId: Int): List<PackageInfo> = try {
        log(TAG, VERBOSE) { "getInstalledPackagesAsUser($flags, $handleId)..." }
        val packageManager = context.packageManager
        packageManager.getInstalledPackagesAsUser(flags, UserHandle2(handleId)).also {
            log(TAG) { "getInstalledPackagesAsUser($flags, $handleId): ${it.size}" }
        }
    } catch (e: Exception) {
        log(TAG, ERROR) { "getInstalledPackagesAsUser(flags=$flags, handleId=$handleId) failed." }
        throw wrapPropagating(e)
    }

    override fun setApplicationEnabledSetting(packageName: String, newState: Int, flags: Int) = try {
        log(TAG, VERBOSE) { "setApplicationEnabledSetting($packageName, $newState, $flags)..." }
        val packageManager = context.packageManager
        packageManager.setApplicationEnabledSetting(packageName, newState, flags)
        log(TAG, VERBOSE) { "setApplicationEnabledSetting($packageName, $newState, $flags) succesful" }
    } catch (e: Exception) {
        log(TAG, ERROR) { "setApplicationEnabledSetting($packageName, $newState, $flags) failed ($e)" }
        throw wrapPropagating(e)
    }

    private fun wrapPropagating(e: Exception): Exception {
        return if (e is UnsupportedOperationException) e
        else UnsupportedOperationException(e)
    }

    companion object {
        val TAG = logTag("Root", "Service", "PkgOps", "Host")
    }
}