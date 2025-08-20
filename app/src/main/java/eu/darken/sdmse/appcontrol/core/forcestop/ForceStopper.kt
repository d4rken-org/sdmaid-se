package eu.darken.sdmse.appcontrol.core.forcestop

import dagger.Reusable
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.automation.core.AutomationManager
import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.canUseAdbNow
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.increaseProgress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.adoptChildResource
import eu.darken.sdmse.setup.automation.AutomationSetupModule
import eu.darken.sdmse.setup.isComplete
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.plus
import javax.inject.Inject

@Reusable
class ForceStopper @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val pkgOps: PkgOps,
    private val automation: AutomationManager,
    private val adbManager: AdbManager,
    private val rootManager: RootManager,
    private val automationSetupModule: AutomationSetupModule,
) : HasSharedResource<Any>, Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(
        Progress.Data(primary = R.string.general_progress_preparing.toCaString())
    )
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope + dispatcherProvider.IO)

    suspend fun forceStop(apps: List<AppInfo>): Result {
        log(TAG, INFO) { "forceStop(${apps.size})" }
        adoptChildResource(pkgOps)


        val successful = mutableSetOf<InstallId>()
        val failed = mutableSetOf<InstallId>()

        if (rootManager.canUseRootNow() || adbManager.canUseAdbNow()) {
            log(TAG) { "Using ROOT/ADB..." }
            updateProgressCount(Progress.Count.Percent(apps.size))
            apps.forEach {
                log(TAG) { "Force stopping ${it.installId} " }
                updateProgressPrimary { ctx ->
                    ctx.getString(R.string.general_progress_processing_x, it.label.get(ctx))
                }
                try {
                    pkgOps.forceStop(it.installId)
                    log(TAG, INFO) { "State successfully force stopped ${it.installId}" }
                    successful.add(it.installId)
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Failed to force-stop $it: ${e.asLog()}" }
                    failed.add(it.installId)
                } finally {
                    increaseProgress()
                }
            }
        } else if (automationSetupModule.isComplete()) {
            log(TAG) { "Using Automation..." }
            val task = ForceStopAutomationTask(apps.map { it.installId }.toList())
            val result = automation.submit(task) as ForceStopAutomationTask.Result
            successful.addAll(result.successful)
            failed.addAll(result.failed)
        }

        return Result(
            success = successful,
            failed = failed,
        )
    }

    data class Result(
        val success: Set<InstallId>,
        val failed: Set<InstallId>,
    )

    companion object {
        private val TAG = logTag("AppControl", "ForceStopper")
    }
}