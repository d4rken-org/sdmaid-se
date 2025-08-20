package eu.darken.sdmse.common.pkgs.pkgops.ipc

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.flowshell.core.cmd.FlowCmd
import eu.darken.flowshell.core.cmd.execute
import eu.darken.flowshell.core.process.FlowProcess
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.Bugs.isDryRun
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.ipc.IpcHostModule
import eu.darken.sdmse.common.ipc.RemoteInputStream
import eu.darken.sdmse.common.pkgs.deleteApplicationCacheFiles
import eu.darken.sdmse.common.pkgs.deleteApplicationCacheFilesAsUser
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.freeStorageAndNotify
import eu.darken.sdmse.common.pkgs.getInstalledPackagesAsUser
import eu.darken.sdmse.common.pkgs.getPackageInfosAsUser
import eu.darken.sdmse.common.pkgs.pkgops.ProcessScanner
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.shell.SharedShell
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.getIdentifier
import kotlinx.coroutines.runBlocking
import java.lang.Thread.sleep
import javax.inject.Inject


class PkgOpsHost @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sharedShell: SharedShell,
    private val processScanner: ProcessScanner,
) : PkgOpsConnection.Stub(), IpcHostModule {

    private val pm: PackageManager
        get() = context.packageManager

    private val am: ActivityManager
        get() = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    override fun getRunningPackages(): RunningPackagesResult = try {
        val result = try {
            am.runningAppProcesses!!
                .flatMap { it.pkgList.toList() }
                .distinct()
                .map {
                    // TODO can we get the correctly user handle?
                    InstallId(it.toPkgId(), UserHandle2())
                }
                .toSet()
        } catch (e: Exception) {
            log(TAG, ERROR) { "getRunningPackages(): runningAppProcesses failed due to $e " }
            runBlocking { processScanner.getRunningPackages() }
                .map { InstallId(it.pkgId, it.handle) }
                .toSet()
        }
        log(TAG, VERBOSE) { "getRunningPackages()=$result" }
        RunningPackagesResult(result)
    } catch (e: Exception) {
        log(TAG, ERROR) { "getRunningPackages() failed: ${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun forceStop(installId: InstallId): Boolean = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        @Suppress("DiscouragedPrivateApi")
        val forceStopPackageAsUser = am.javaClass.getDeclaredMethod(
            "forceStopPackageAsUser", String::class.java, Int::class.javaPrimitiveType
        ).apply {
            isAccessible = true
        }
        val pkg = installId.pkgId.name
        val userId = installId.userHandle.handleId
        log(TAG) { "Force stopping $pkg for user $userId..." }
        try {
            forceStopPackageAsUser.invoke(am, pkg, userId)
            true
        } catch (e: NoSuchMethodException) {
            log(TAG, ERROR) { "Method forceStopPackageAsUser was unavailable: ${e.asLog()}" }
            val result = runBlocking {
                sharedShell.useRes {
                    FlowCmd("am force-stop --user ${installId.userHandle.handleId} ${installId.pkgId.name}").execute(it)
                }
            }
            log(TAG, VERBOSE) { "forceStop($installId) result: $result" }
            result.isSuccessful
        }
    } catch (e: Exception) {
        log(TAG, ERROR) { "forceStop($installId) failed: ${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun clearCacheAsUser(packageName: String, handleId: Int, dryRun: Boolean): Boolean = try {
        log(TAG, VERBOSE) { "clearCache(packageName=$packageName, handleId=$handleId, dryRun=$dryRun)..." }
        if (dryRun) {
            sleep(100)
            true
        } else {
            runBlocking { pm.deleteApplicationCacheFilesAsUser(packageName, handleId) }
        }
    } catch (e: Exception) {
        log(TAG, ERROR) { "clearCache(packageName=$packageName, handleId=$handleId) failed: ${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun clearCache(packageName: String, dryRun: Boolean): Boolean = try {
        log(TAG, VERBOSE) { "clearCache(packageName=$packageName, dryRun=$dryRun)..." }
        if (dryRun) {
            sleep(100)
            true
        } else {
            runBlocking { pm.deleteApplicationCacheFiles(packageName) }
        }
    } catch (e: Exception) {
        log(TAG, ERROR) { "clearCache(packageName=$packageName) failed: ${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun trimCaches(desiredBytes: Long, storageId: String?, dryRun: Boolean): Boolean = try {
        log(TAG, VERBOSE) { "trimCaches(desiredBytes=$desiredBytes, storageId=$storageId, dryRun=$dryRun)..." }
        if (isDryRun) {
            log(TAG, INFO) { "DRYRUN: not executing trimCaches($desiredBytes, $storageId)" }
            sleep(2000)
            true
        } else {
            runBlocking { pm.freeStorageAndNotify(desiredBytes, storageId) }
        }
    } catch (e: Exception) {
        log(TAG, ERROR) { "trimCaches(desiredBytes=$desiredBytes, storageId=$storageId) failed: ${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun getPackageInfoAsUser(packageName: String, flags: Long, handleId: Int): PackageInfo? = try {
        log(TAG, VERBOSE) { "getPackageInfoAsUser($packageName, $flags, $handleId)..." }

        pm.getPackageInfosAsUser(packageName, flags, UserHandle2(handleId)).also {
            log(TAG) { "getPackageInfoAsUser($packageName, $flags, $handleId): $it" }
        }
    } catch (e: Exception) {
        log(TAG, ERROR) {
            "getPackageInfoAsUser(packageName=$packageName, flags=$flags, handleId=$handleId) failed: ${e.asLog()}"
        }
        throw e.wrapToPropagate()
    }

    override fun getInstalledPackagesAsUser(flags: Long, handleId: Int): List<PackageInfo> = try {
        log(TAG, VERBOSE) { "getInstalledPackagesAsUser($flags, $handleId)..." }

        pm.getInstalledPackagesAsUser(flags, UserHandle2(handleId)).also {
            log(TAG) { "getInstalledPackagesAsUser($flags, $handleId): ${it.size}" }
        }
    } catch (e: Exception) {
        log(TAG, ERROR) { "getInstalledPackagesAsUser(flags=$flags, handleId=$handleId) failed: ${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun getInstalledPackagesAsUserStream(flags: Long, handleId: Int): RemoteInputStream = try {
        log(TAG, VERBOSE) { "getInstalledPackagesAsUserStream($flags, $handleId)..." }
        pm.getInstalledPackagesAsUser(flags, UserHandle2(handleId)).also {
            log(TAG) { "getInstalledPackagesAsUserStream($flags, $handleId): ${it.size}" }
        }.toRemoteInputStream()
    } catch (e: Exception) {
        log(TAG, ERROR) { "getInstalledPackagesAsUserStream(flags=$flags, handleId=$handleId) failed: ${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun setApplicationEnabledSetting(id: InstallId, newState: Int, flags: Int) = try {
        log(TAG, VERBOSE) { "setApplicationEnabledSetting($id, $newState, $flags)..." }

        val currentUser = Process.myUserHandle().getIdentifier()
        log(TAG) { "currentUser: $currentUser" }
        if (id.userHandle.handleId == currentUser) {
            pm.setApplicationEnabledSetting(id.pkgId.name, newState, flags)
        } else {
            val command = when (newState) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> "enable"
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> "disable-user"
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> "disable-user"
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> "disable-until-used"
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> "default-state"
                else -> throw IllegalArgumentException("Unknown state: $newState")
            }

            val result = runBlocking {
                sharedShell.useRes {
                    FlowCmd("pm $command --user ${id.userHandle.handleId} ${id.pkgId.name}").execute(it)
                }
            }
            log(TAG, INFO) { "setApplicationEnabledSetting result: $result" }
        }

        log(TAG, VERBOSE) { "setApplicationEnabledSetting($id, $newState, $flags) succesful" }
    } catch (e: Exception) {
        log(TAG, ERROR) { "setApplicationEnabledSetting($id, $newState, $flags) failed: ${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun grantPermission(packageName: String, handleId: Int, permissionId: String): Boolean = try {
        log(TAG, VERBOSE) { "grantPermission($packageName, $handleId, $permissionId)..." }
        val result = runBlocking {
            sharedShell.useRes {
                FlowCmd("pm grant --user $handleId $packageName $permissionId").execute(it)
            }
        }
        result.exitCode == FlowProcess.ExitCode.OK
    } catch (e: Exception) {
        log(TAG, ERROR) { "grantPermission($packageName, $handleId, $permissionId) failed: ${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun revokePermission(packageName: String, handleId: Int, permissionId: String): Boolean = try {
        log(TAG, VERBOSE) { "revokePermission($packageName, $handleId, $permissionId)..." }
        val result = runBlocking {
            sharedShell.useRes {
                FlowCmd("pm revoke --user $handleId $packageName $permissionId").execute(it)
            }
        }
        result.exitCode == FlowProcess.ExitCode.OK
    } catch (e: Exception) {
        log(TAG, ERROR) { "revokePermission($packageName, $handleId, $permissionId) failed: ${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun setAppOps(packageName: String, handleId: Int, key: String, value: String): Boolean = try {
        log(TAG, VERBOSE) { "setAppOps($packageName, $handleId, $key, $value)..." }
        val result = runBlocking {
            sharedShell.useRes {
                FlowCmd("appops set --user $handleId $packageName $key $value ").execute(it)
            }
        }
        result.exitCode == FlowProcess.ExitCode.OK
    } catch (e: Exception) {
        log(TAG, ERROR) { "setAppOps($packageName, $handleId, $key, $value) failed: ${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    companion object {
        val TAG = logTag("Pkg", "Ops", "Service", "Host", Bugs.processTag)
    }
}