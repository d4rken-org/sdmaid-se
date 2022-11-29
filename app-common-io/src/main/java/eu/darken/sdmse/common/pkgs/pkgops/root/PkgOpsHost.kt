package eu.darken.sdmse.common.pkgs.pkgops.root

import android.app.ActivityManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.pkgops.LibcoreTool
import eu.darken.sdmse.common.shell.RootProcessShell
import eu.darken.sdmse.common.shell.SharedShell
import java.lang.reflect.Method
import javax.inject.Inject


class PkgOpsHost @Inject constructor(
    @ApplicationContext private val context: Context,
    @RootProcessShell private val sharedShell: SharedShell,
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

    private fun wrapPropagating(e: Exception): Exception {
        return if (e is UnsupportedOperationException) e
        else UnsupportedOperationException(e)
    }

    companion object {
        val TAG = logTag("Root", "Java", "PkgOps", "Host")
    }
}