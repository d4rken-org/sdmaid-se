package eu.darken.sdmse.appcontrol.core.uninstall

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.useRootNow
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.adoptChildResource
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.shell.ipc.ShellOpsCmd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.plus
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@Reusable
class Uninstaller @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val pkgRepo: PkgRepo,
    private val shellOps: ShellOps,
    private val rootManager: RootManager,
) : HasSharedResource<Any> {

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope + dispatcherProvider.IO)

    suspend fun uninstall(installId: Installed.InstallId) {
        log(TAG, INFO) { "Uninstalling $installId" }

        if (rootManager.useRootNow()) {
            adoptChildResource(shellOps)
            val userId = installId.userHandle.handleId
            val pkgName = installId.pkgId.name
            val shellCmd = ShellOpsCmd("pm uninstall --user $userId $pkgName")
            val result = shellOps.execute(shellCmd, mode = ShellOps.Mode.ROOT)
            log(TAG) { "Uninstall command result: $result" }
            if (!result.isSuccess) {
                throw UninstallException(
                    installId,
                    IllegalStateException(result.errors.joinToString())
                )
            }
        } else {
            val appSettingsIntent = Intent(Intent.ACTION_DELETE)
            appSettingsIntent.data = Uri.parse("package:${installId.pkgId.name}")
            appSettingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(appSettingsIntent)
        }

        try {
            // Wait until the app is no longer installed
            withTimeout(30 * 1000) {
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