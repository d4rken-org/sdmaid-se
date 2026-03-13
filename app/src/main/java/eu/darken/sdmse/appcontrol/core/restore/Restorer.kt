package eu.darken.sdmse.appcontrol.core.restore

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
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.isArchived
import eu.darken.sdmse.common.pkgs.pkgs
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.SharedResource
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
class Restorer @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val pkgRepo: PkgRepo,
    private val userManager2: UserManager2,
    private val automation: AutomationManager,
    private val automationSetupModule: AutomationSetupModule,
    private val unarchiveManager: UnarchiveManager,
    private val rootManager: RootManager,
    private val adbManager: AdbManager,
) : HasSharedResource<Any> {

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope + dispatcherProvider.IO)

    suspend fun restore(app: AppInfo) {
        log(TAG, VERBOSE) { "restore($app)" }

        if (!app.pkg.isArchived) {
            throw RestoreException(
                message = "App is not archived and cannot be restored",
                installId = app.installId,
            )
        }

        val isCurrentUser = app.installId.userHandle == userManager2.currentUser()
        val pkgName = app.installId.pkgId.name

        val hasElevatedAccess = rootManager.canUseRootNow() || adbManager.canUseAdbNow()

        // Try PackageInstaller API via Root/ADB first (API 35+)
        if (hasApiLevel(35) && hasElevatedAccess) {
            log(TAG) { "Attempting PackageInstaller.requestUnarchive via Root/ADB for ${app.installId}" }
            try {
                val result = unarchiveManager.requestUnarchive(pkgName)
                if (result.isSuccess) {
                    log(TAG, INFO) { "PackageInstaller unarchive initiated successfully for ${app.installId}" }
                    // Continue to wait for the app to be fully restored below
                } else {
                    log(TAG, WARN) { "PackageInstaller unarchive failed: ${result.statusMessage}" }
                    // Fall through to automation
                    useAutomationFallback(app)
                }
            } catch (e: Exception) {
                log(TAG, WARN) { "PackageInstaller unarchive exception, falling back: ${e.asLog()}" }
                // Fall through to automation
                useAutomationFallback(app)
            }
        } else {
            // Pre-API 35 or no Root/ADB: Only automation is supported for restore
            if (hasApiLevel(35)) {
                log(TAG) { "No Root/ADB access for ${app.installId}, falling back to automation" }
            }
            useAutomationFallback(app)
        }

        try {
            // Longer timeout for restore - may need to download APK from Play Store
            val timeoutSeconds = 120L
            log(TAG) { "Waiting for system restore process (timeout=$timeoutSeconds)" }

            // Wait until the app is no longer archived (sourceDir becomes non-null)
            withTimeout(timeoutSeconds * 1000) {
                if (isCurrentUser) {
                    pkgRepo.pkgs().first { pkgs ->
                        pkgs.any { it.installId == app.installId && !it.isArchived }
                    }
                } else {
                    delay(3000)

                    val getMatchingPackage = suspend {
                        pkgRepo.refresh()
                        pkgRepo.pkgs().first().firstOrNull {
                            it.installId == app.installId
                        }.also {
                            log(TAG) { "Refreshed restore state: $it, isArchived=${it?.isArchived}" }
                        }
                    }

                    var current = getMatchingPackage()

                    while (currentCoroutineContext().isActive && current?.isArchived != false) {
                        delay(3000)
                        current = getMatchingPackage()
                    }
                }
            }
            log(TAG, INFO) { "Successfully restored ${app.installId}" }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to verify restore for ${app.installId}: ${e.asLog()}" }
            throw RestoreException(installId = app.installId, cause = e)
        }
    }

    private suspend fun useAutomationFallback(app: AppInfo) {
        if (!automationSetupModule.isComplete()) {
            throw RestoreException(
                message = "Restoring requires Accessibility Service access (or PackageInstaller for Play Store apps on Android 15+)",
                installId = app.installId,
            )
        }

        log(TAG) { "Using Automation to restore ${app.installId}" }
        val task = RestoreAutomationTask(listOf(app.installId))
        val result = automation.submit(task) as RestoreAutomationTask.Result
        if (result.failed.contains(app.installId)) {
            throw RestoreException(
                message = "Automation failed to restore app",
                installId = app.installId,
            )
        }
    }

    companion object {
        private val TAG = logTag("AppControl", "Restorer")
    }
}
