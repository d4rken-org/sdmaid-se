package eu.darken.sdmse.common.pkgs.pkgops.ipc

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.rxshell.cmd.Cmd
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.ipc.IpcHostModule
import eu.darken.sdmse.common.ipc.RemoteInputStream
import eu.darken.sdmse.common.pkgs.deleteApplicationCacheFiles
import eu.darken.sdmse.common.pkgs.deleteApplicationCacheFilesAsUser
import eu.darken.sdmse.common.pkgs.freeStorageAndNotify
import eu.darken.sdmse.common.pkgs.getInstalledPackagesAsUser
import eu.darken.sdmse.common.pkgs.pkgops.LibcoreTool
import eu.darken.sdmse.common.shell.SharedShell
import eu.darken.sdmse.common.user.UserHandle2
import kotlinx.coroutines.runBlocking
import javax.inject.Inject


class PkgOpsHost @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libcoreTool: LibcoreTool,
    private val sharedShell: SharedShell,
) : PkgOpsConnection.Stub(), IpcHostModule {

    private val pm: PackageManager
        get() = context.packageManager

    private val am: ActivityManager
        get() = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

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

    override fun isRunning(packageName: String): Boolean = try {
        val result = try {
            val runningAppProcesses = am.runningAppProcesses
                ?.flatMap { it.pkgList.toList() }
                ?.distinct()
                ?: emptyList()
            runningAppProcesses.any { it == packageName }
        } catch (e: Exception) {
            log(TAG, ERROR) { "isRunning($packageName): runningAppProcesses failed due to ${e.asLog()} " }
            runBlocking {
                sharedShell.useRes {
                    Cmd.builder("pidof $packageName").execute(it)
                }.exitCode == Cmd.ExitCode.OK
            }
        }
        log(TAG, VERBOSE) { "isRunning(packageName=$packageName)=$result" }
        result
    } catch (e: Exception) {
        log(TAG, ERROR) { "isRunning(packageName=$packageName) failed." }
        throw wrapPropagating(e)
    }

    override fun forceStop(packageName: String): Boolean = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val forceStopPackage = am.javaClass.getDeclaredMethod("forceStopPackage", String::class.java).apply {
            isAccessible = true
        }
        forceStopPackage.invoke(am, packageName)
        true
    } catch (e: Exception) {
        log(TAG, ERROR) { "forceStop(packageName=$packageName) failed." }
        throw wrapPropagating(e)
    }

    override fun clearCacheAsUser(packageName: String, handleId: Int): Boolean = try {
        log(TAG, VERBOSE) { "clearCache(packageName=$packageName, handleId=$handleId)..." }
        runBlocking { pm.deleteApplicationCacheFilesAsUser(packageName, handleId) }
    } catch (e: Exception) {
        log(TAG, ERROR) { "clearCache(packageName=$packageName, handleId=$handleId) failed." }
        throw wrapPropagating(e)
    }

    override fun clearCache(packageName: String): Boolean = try {
        log(TAG, VERBOSE) { "clearCache(packageName=$packageName)..." }
        runBlocking { pm.deleteApplicationCacheFiles(packageName) }
    } catch (e: Exception) {
        log(TAG, ERROR) { "clearCache(packageName=$packageName) failed." }
        throw wrapPropagating(e)
    }

    override fun trimCaches(desiredBytes: Long, storageId: String?): Boolean = try {
        log(TAG, VERBOSE) { "trimCaches(desiredBytes=$desiredBytes, storageId=$storageId)..." }
        runBlocking { pm.freeStorageAndNotify(desiredBytes, storageId) }
    } catch (e: Exception) {
        log(TAG, ERROR) { "trimCaches(desiredBytes=$desiredBytes, storageId=$storageId) failed." }
        throw wrapPropagating(e)
    }

    override fun getInstalledPackagesAsUser(flags: Int, handleId: Int): List<PackageInfo> = try {
        log(TAG, VERBOSE) { "getInstalledPackagesAsUser($flags, $handleId)..." }

        val result = pm.getInstalledPackagesAsUser(flags, UserHandle2(handleId)).also {
            log(TAG) { "getInstalledPackagesAsUser($flags, $handleId): ${it.size}" }
        }
        result + result + result + result + result
    } catch (e: Exception) {
        log(TAG, ERROR) { "getInstalledPackagesAsUser(flags=$flags, handleId=$handleId) failed." }
        throw wrapPropagating(e)
    }

    override fun getInstalledPackagesAsUserStream(flags: Int, handleId: Int): RemoteInputStream = try {
        log(TAG, VERBOSE) { "getInstalledPackagesAsUserStream($flags, $handleId)..." }
        val packageManager = context.packageManager
        val result = packageManager.getInstalledPackagesAsUser(flags, UserHandle2(handleId)).also {
            log(TAG) { "getInstalledPackagesAsUser($flags, $handleId): ${it.size}" }
        }
        val payload = result + result + result + result + result
        payload.toRemoteInputStream()
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

    override fun grantPermission(packageName: String, handleId: Int, permissionId: String): Boolean = try {
        log(TAG, VERBOSE) { "grantPermission($packageName, $handleId, $permissionId)..." }
        val result = runBlocking {
            sharedShell.useRes {
                Cmd.builder("pm grant --user $handleId $packageName $permissionId").execute(it)
            }
        }
        result.exitCode == Cmd.ExitCode.OK
    } catch (e: Exception) {
        log(TAG, ERROR) { "grantPermission($packageName, $handleId, $permissionId) failed: $e" }
        throw wrapPropagating(e)
    }

    override fun setAppOps(packageName: String, handleId: Int, key: String, value: String): Boolean = try {
        log(TAG, VERBOSE) { "setAppOps($packageName, $handleId, $key, $value)..." }
        val result = runBlocking {
            sharedShell.useRes {
                Cmd.builder("appops set --user $handleId $packageName $key $value ").execute(it)
            }
        }
        result.exitCode == Cmd.ExitCode.OK
    } catch (e: Exception) {
        log(TAG, ERROR) { "setAppOps($packageName, $handleId, $key, $value) failed: $e" }
        throw wrapPropagating(e)
    }

    private fun wrapPropagating(e: Exception): Exception {
        return if (e is UnsupportedOperationException) e
        else UnsupportedOperationException(e)
    }

    companion object {
        val TAG = logTag("PkgOps", "Service", "Host", Bugs.processTag)
    }
}