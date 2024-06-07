package eu.darken.sdmse.appcontrol.core.uninstall

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.adoptChildResource
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.shell.ipc.ShellOpsCmd
import eu.darken.sdmse.common.shizuku.ShizukuManager
import eu.darken.sdmse.common.shizuku.canUseShizukuNow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.plus
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import kotlin.math.roundToLong

@Reusable
class Uninstaller @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val pkgRepo: PkgRepo,
    private val shellOps: ShellOps,
    private val rootManager: RootManager,
    private val shizukuManager: ShizukuManager,
) : HasSharedResource<Any> {

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope + dispatcherProvider.IO)

    // TODO Uninstalling for archived apps
    suspend fun uninstall(app: AppInfo) {
        log(TAG, VERBOSE) { "uninstall($app)" }
        val installId = app.installId
        when {
            rootManager.canUseRootNow() -> {
                log(TAG) { "Using ROOT to uninstall $installId" }

                val userId = installId.userHandle.handleId
                val pkgName = installId.pkgId.name
                val shellCmd = ShellOpsCmd("pm uninstall --user $userId $pkgName")

                adoptChildResource(shellOps)
                val result = shellOps.execute(shellCmd, mode = ShellOps.Mode.ROOT)
                log(TAG) { "Uninstall command via ROOT result: $result" }
                if (!result.isSuccess) {
                    throw UninstallException(installId, IllegalStateException(result.errors.joinToString()))
                }
            }

            shizukuManager.canUseShizukuNow() -> {
                log(TAG) { "Using ADB to uninstall $installId" }

                val userId = installId.userHandle.handleId
                val pkgName = installId.pkgId.name
                val shellCmd = ShellOpsCmd("pm uninstall --user $userId $pkgName")

                adoptChildResource(shellOps)
                val result = shellOps.execute(shellCmd, mode = ShellOps.Mode.ADB)
                log(TAG) { "Uninstall command via ADB result: $result" }
                if (!result.isSuccess) {
                    throw UninstallException(installId, IllegalStateException(result.errors.joinToString()))
                }
            }

            else -> {
                log(TAG) { "Using normal instant to uninstall $installId" }
                val appSettingsIntent = Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:${installId.pkgId.name}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(appSettingsIntent)
            }
        }

        try {
            val timeoutSeconds = maxOf(
                // Default
                60,
                // 15s per 100MB
                app.sizes?.total?.let { 15 * (it / (100f * 1048576)) }?.roundToLong() ?: 0L
            )
            log(TAG) { "Waiting for system uninstall process (timeout=$timeoutSeconds)" }
            // Wait until the app is no longer installed
            withTimeout(timeoutSeconds * 1000) {
                pkgRepo.pkgs.first { pkgs ->
                    pkgs.none { it.installId == installId }
                }
            }
            log(TAG) { "Successfully uninstalled $installId" }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to uninstall $installId: ${e.asLog()}" }
            throw UninstallException(installId, cause = e)
        }

        AppControl.lastUninstalledPkg = installId.pkgId
    }

    companion object {
        private val TAG = logTag("AppControl", "Uninstaller")
    }

}