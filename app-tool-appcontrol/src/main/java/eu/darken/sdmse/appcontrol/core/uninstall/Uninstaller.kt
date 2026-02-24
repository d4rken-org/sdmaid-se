package eu.darken.sdmse.appcontrol.core.uninstall

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.canUseAdbNow
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.isSystemApp
import eu.darken.sdmse.common.pkgs.isUpdatedSystemApp
import eu.darken.sdmse.common.pkgs.pkgs
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.adoptChildResource
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.shell.ipc.ShellOpsCmd
import eu.darken.sdmse.common.user.UserManager2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
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
    private val adbManager: AdbManager,
    private val userManager2: UserManager2,
) : HasSharedResource<Any> {

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope + dispatcherProvider.IO)

    // TODO Uninstalling for archived apps
    suspend fun uninstall(app: AppInfo) {
        log(TAG, VERBOSE) { "uninstall($app)" }

        val isCurrentUser = app.installId.userHandle == userManager2.currentUser()

        when {
            app.pkg.isUpdatedSystemApp -> {
                log(TAG, INFO) { "Uninstalling system app update... (${app.pkg.versionName}[${app.pkg.versionCode}])" }
            }

            app.pkg.isSystemApp -> {
                log(TAG, INFO) { "Uninstalling system app..." }
            }
        }

        when {
            rootManager.canUseRootNow() -> {
                log(TAG) { "Using ROOT to uninstall ${app.installId}" }

                val userId = app.installId.userHandle.handleId
                val pkgName = app.installId.pkgId.name
                val shellCmd = ShellOpsCmd("pm uninstall --user $userId $pkgName")

                adoptChildResource(shellOps)
                val result = shellOps.execute(shellCmd, mode = ShellOps.Mode.ROOT)
                log(TAG) { "Uninstall command via ROOT result: $result" }
                if (!result.isSuccess) {
                    throw UninstallException(
                        installId = app.installId,
                        cause = IllegalStateException(result.errors.joinToString())
                    )
                }
            }

            adbManager.canUseAdbNow() -> {
                log(TAG) { "Using ADB to uninstall ${app.installId}" }

                val userId = app.installId.userHandle.handleId
                val pkgName = app.installId.pkgId.name
                val shellCmd = ShellOpsCmd("pm uninstall --user $userId $pkgName")

                adoptChildResource(shellOps)
                val result = shellOps.execute(shellCmd, mode = ShellOps.Mode.ADB)
                log(TAG) { "Uninstall command via ADB result: $result" }
                if (!result.isSuccess) {
                    throw UninstallException(
                        installId = app.installId,
                        cause = IllegalStateException(result.errors.joinToString())
                    )
                }
            }

            else -> {
                log(TAG) { "Using normal intent to uninstall ${app.installId}" }
                val appSettingsIntent = Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:${app.installId.pkgId.name}")
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
            var downgrade: Installed? = null

            // Wait until the app or update is no longer installed
            withTimeout(timeoutSeconds * 1000) {
                val postUninstallPkgs = if (isCurrentUser) {
                    pkgRepo.pkgs().first { pkgs ->
                        pkgs.none { it.installId == app.installId && it.versionCode == app.pkg.versionCode }
                    }
                } else {
                    delay(3000)

                    val getMatchingPackages = suspend {
                        pkgRepo.refresh()
                        pkgRepo.pkgs().first().filter {
                            it.installId == app.installId && it.versionCode == app.pkg.versionCode
                        }.also {
                            log(TAG) { "Refreshed: $it" }
                        }
                    }

                    val initial = getMatchingPackages()
                    var current = initial

                    while (currentCoroutineContext().isActive && current == initial && !current.isEmpty()) {
                        delay(3000)
                        current = getMatchingPackages()
                    }
                    current
                }

                downgrade = postUninstallPkgs.firstOrNull { it.installId == app.installId }
            }
            log(TAG) { "Successfully uninstalled ${app.installId}" }

            downgrade?.let {
                log(TAG, INFO) { "Uninstalled, but still exists. DOWNGRADE to ${it.versionName} (${it.versionCode})" }
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to uninstall ${app.installId}: ${e.asLog()}" }
            throw UninstallException(installId = app.installId, cause = e)
        }

        AppControl.lastUninstalledPkg = app.installId.pkgId
    }

    companion object {
        private val TAG = logTag("AppControl", "Uninstaller")
    }

}