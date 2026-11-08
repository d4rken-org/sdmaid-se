package eu.darken.sdmse.common.pkgs.pkgops.ipc

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.ApplicationInfo
import android.content.pm.InstallSourceInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserHandle
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
import eu.darken.sdmse.common.pkgs.pkgops.ArchivableCheck
import eu.darken.sdmse.common.pkgs.pkgops.ProcessScanner
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.shell.SharedShell
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.getIdentifier
import kotlinx.coroutines.runBlocking
import java.lang.Thread.sleep
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import javax.inject.Inject


class PkgOpsHost @Inject constructor(
    @ApplicationContext private val context: Context,
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
            if (result.exitCode != FlowProcess.ExitCode.OK) {
                throw IllegalStateException("pm $command failed (${result.exitCode}): ${result.errors}")
            }
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

    @SuppressLint("MissingPermission") // Runs in the privileged root/ADB IPC host process; SDK-guarded below
    override fun requestUnarchive(packageName: String, handleId: Int, statusReceiver: IntentSender) = try {
        log(TAG, VERBOSE) { "requestUnarchive($packageName, $handleId)..." }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val currentUser = Process.myUserHandle().getIdentifier()
            if (handleId == currentUser) {
                pm.packageInstaller.requestUnarchive(packageName, statusReceiver)
            } else {
                log(TAG) { "requestUnarchive($packageName, $handleId): Host user is $currentUser, targeting $handleId" }
                pm.packageInstaller.requestUnarchiveAsUser(
                    packageName = packageName,
                    callerPackageName = context.packageName,
                    statusReceiver = statusReceiver,
                    userHandle = UserHandle2(handleId).asUserHandle(),
                )
            }
        } else {
            throw UnsupportedOperationException("requestUnarchive requires API 35+")
        }
        log(TAG, VERBOSE) { "requestUnarchive($packageName, $handleId) initiated" }
    } catch (e: Exception) {
        log(TAG, ERROR) { "requestUnarchive($packageName, $handleId) failed: ${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    override fun getArchivablePackages(handleId: Int): List<String> = try {
        log(TAG, VERBOSE) { "getArchivablePackages($handleId)..." }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            log(TAG) { "getArchivablePackages($handleId): Requires API 35+" }
            emptyList()
        } else {
            val iPackageManager = Class.forName("android.app.ActivityThread")
                .getMethod("getPackageManager")
                .invoke(null)
                ?: throw IllegalStateException("ActivityThread.getPackageManager() is null")
            val getInstallSourceInfo = Class.forName("android.content.pm.IPackageManager").getMethod(
                "getInstallSourceInfo",
                String::class.java,
                Int::class.javaPrimitiveType,
            )
            val queryBroadcastReceiversAsUser = PackageManager::class.java.getMethod(
                "queryBroadcastReceiversAsUser",
                Intent::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            val queryIntentActivitiesAsUser = PackageManager::class.java.getMethod(
                "queryIntentActivitiesAsUser",
                Intent::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val receiverCache = mutableMapOf<String, Boolean>()

            pm.getInstalledPackagesAsUser(0L, UserHandle2(handleId))
                .mapNotNull { it.applicationInfo }
                .filter { it.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0 }
                .filter { appInfo ->
                    ArchivableCheck.isArchivable(
                        packageName = appInfo.packageName,
                        userId = handleId,
                        installerOf = { pkg, userId ->
                            val source = getInstallSourceInfo
                                .invokeUnwrapped(iPackageManager, pkg, userId) as InstallSourceInfo?
                            ArchivableCheck.responsibleInstaller(
                                updateOwner = source?.updateOwnerPackageName,
                                installingPackage = source?.installingPackageName,
                            )
                        },
                        hasUnarchiveReceiver = { installer, userId ->
                            receiverCache.getOrPut(installer) {
                                val intent = Intent(Intent.ACTION_UNARCHIVE_PACKAGE).setPackage(installer)
                                val receivers = queryBroadcastReceiversAsUser.invokeUnwrapped(pm, intent, 0, userId)
                                (receivers as List<*>?).orEmpty().isNotEmpty()
                            }
                        },
                        hasLauncherActivity = { pkg, userId ->
                            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg)
                            val activities = queryIntentActivitiesAsUser.invokeUnwrapped(pm, intent, 0, userId)
                            (activities as List<*>?).orEmpty().isNotEmpty()
                        },
                        isOptedOut = { pkg ->
                            appOps.unsafeCheckOpNoThrow(OPSTR_AUTO_REVOKE_IF_UNUSED, appInfo.uid, pkg) ==
                                AppOpsManager.MODE_IGNORED
                        },
                    )
                }
                .map { it.packageName }
                .also { log(TAG) { "getArchivablePackages($handleId): ${it.size} archivable" } }
        }
    } catch (e: Exception) {
        log(TAG, ERROR) { "getArchivablePackages($handleId) failed: ${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    private fun Method.invokeUnwrapped(receiver: Any?, vararg args: Any?): Any? = try {
        invoke(receiver, *args)
    } catch (e: InvocationTargetException) {
        throw e.targetException ?: e
    }

    // PackageInstaller.requestUnarchive only targets the caller's own user, IPackageInstaller takes any user
    // (AOSP android15-release and android16-release).
    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun PackageInstaller.requestUnarchiveAsUser(
        packageName: String,
        callerPackageName: String,
        statusReceiver: IntentSender,
        userHandle: UserHandle,
    ) {
        val installer = PackageInstaller::class.java.getDeclaredField("mInstaller")
            .apply { isAccessible = true }
            .get(this)
            ?: throw IllegalStateException("PackageInstaller.mInstaller is null")
        val requestUnarchive = Class.forName("android.content.pm.IPackageInstaller").getMethod(
            "requestUnarchive",
            String::class.java,
            String::class.java,
            IntentSender::class.java,
            UserHandle::class.java,
        )
        try {
            requestUnarchive.invoke(installer, packageName, callerPackageName, statusReceiver, userHandle)
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
    }

    companion object {
        // AppOpsManager.OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED, MODE_IGNORED means opted out of archiving
        // (AOSP android15-release / android16-release PackageArchiver.isAppOptedOutOfArchiving)
        private const val OPSTR_AUTO_REVOKE_IF_UNUSED = "android:auto_revoke_permissions_if_unused"
        val TAG = logTag("Pkg", "Ops", "Service", "Host", Bugs.processTag)
    }
}