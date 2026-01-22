package eu.darken.sdmse.appcontrol.core.archive

import dagger.Reusable
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.automation.core.AutomationManager
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
import eu.darken.sdmse.common.pkgs.isArchived
import eu.darken.sdmse.common.pkgs.pkgs
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.adoptChildResource
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.shell.ipc.ShellOpsCmd
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.setup.automation.AutomationSetupModule
import eu.darken.sdmse.setup.isComplete
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.plus
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@Reusable
class Archiver @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val pkgRepo: PkgRepo,
    private val shellOps: ShellOps,
    private val rootManager: RootManager,
    private val adbManager: AdbManager,
    private val userManager2: UserManager2,
    private val archiveSupport: ArchiveSupport,
    private val automation: AutomationManager,
    private val automationSetupModule: AutomationSetupModule,
) : HasSharedResource<Any> {

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope + dispatcherProvider.IO)

    suspend fun archive(app: AppInfo) {
        log(TAG, VERBOSE) { "archive($app)" }

        if (!archiveSupport.isArchivingEnabled) {
            throw ArchiveException(
                message = "Archiving is not available on this device",
                installId = app.installId,
            )
        }

        val isCurrentUser = app.installId.userHandle == userManager2.currentUser()
        val userId = app.installId.userHandle.handleId
        val pkgName = app.installId.pkgId.name
        val shellCmd = ShellOpsCmd("pm archive --user $userId $pkgName")

        when {
            rootManager.canUseRootNow() -> {
                log(TAG) { "Using ROOT to archive ${app.installId}" }

                adoptChildResource(shellOps)
                val result = shellOps.execute(shellCmd, mode = ShellOps.Mode.ROOT)
                log(TAG) { "Archive command via ROOT result: $result" }
                if (!result.isSuccess) {
                    throw ArchiveException(
                        installId = app.installId,
                        cause = IllegalStateException(result.errors.joinToString()),
                    )
                }
            }

            adbManager.canUseAdbNow() -> {
                log(TAG) { "Using ADB to archive ${app.installId}" }

                adoptChildResource(shellOps)
                val result = shellOps.execute(shellCmd, mode = ShellOps.Mode.ADB)
                log(TAG) { "Archive command via ADB result: $result" }
                if (!result.isSuccess) {
                    throw ArchiveException(
                        installId = app.installId,
                        cause = IllegalStateException(result.errors.joinToString()),
                    )
                }
            }

            automationSetupModule.isComplete() -> {
                log(TAG) { "Using Automation to archive ${app.installId}" }
                val task = ArchiveAutomationTask(listOf(app.installId))
                val result = automation.submit(task) as ArchiveAutomationTask.Result
                if (result.failed.contains(app.installId)) {
                    throw ArchiveException(
                        message = "Automation failed to archive app",
                        installId = app.installId,
                    )
                }
            }

            else -> {
                throw ArchiveException(
                    message = "Archiving requires Root, Shizuku, or Accessibility Service access",
                    installId = app.installId,
                )
            }
        }

        try {
            val timeoutSeconds = 60L
            log(TAG) { "Waiting for system archive process (timeout=$timeoutSeconds)" }

            // Wait until the app is archived (sourceDir becomes null)
            withTimeout(timeoutSeconds * 1000) {
                if (isCurrentUser) {
                    pkgRepo.pkgs().first { pkgs ->
                        pkgs.any { it.installId == app.installId && it.isArchived }
                    }
                } else {
                    delay(3000)

                    val getMatchingPackage = suspend {
                        pkgRepo.refresh()
                        pkgRepo.pkgs().first().firstOrNull {
                            it.installId == app.installId
                        }.also {
                            log(TAG) { "Refreshed archive state: $it, isArchived=${it?.isArchived}" }
                        }
                    }

                    var current = getMatchingPackage()

                    while (currentCoroutineContext().isActive && current?.isArchived != true) {
                        delay(3000)
                        current = getMatchingPackage()
                    }
                }
            }
            log(TAG, INFO) { "Successfully archived ${app.installId}" }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to verify archive for ${app.installId}: ${e.asLog()}" }
            throw ArchiveException(installId = app.installId, cause = e)
        }
    }

    companion object {
        private val TAG = logTag("AppControl", "Archiver")
    }
}
