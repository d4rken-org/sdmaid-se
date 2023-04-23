package eu.darken.sdmse.appcontrol.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.tasks.AppControlScanTask
import eu.darken.sdmse.appcontrol.core.tasks.AppControlTask
import eu.darken.sdmse.appcontrol.core.tasks.AppControlToggleTask
import eu.darken.sdmse.appcontrol.core.uninstall.UninstallTask
import eu.darken.sdmse.appcontrol.core.uninstall.Uninstaller
import eu.darken.sdmse.common.RootRequiredException
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.currentPkgs
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.isEnabled
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppControl @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val pkgOps: PkgOps,
    private val pkgRepo: PkgRepo,
    private val rootManager: RootManager,
    private val userManager: UserManager2,
    private val uninstaller: Uninstaller,
) : SDMTool, Progress.Client {

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope)

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub
    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private val internalData = MutableStateFlow(null as Data?)
    val data: Flow<Data?> = internalData

    override val type: SDMTool.Type = SDMTool.Type.APPCONTROL

    private val jobLock = Mutex()
    override suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result = jobLock.withLock {
        task as AppControlTask
        log(TAG) { "submit($task) starting..." }
        updateProgress { Progress.DEFAULT_STATE }
        try {
            val result = when (task) {
                is AppControlScanTask -> performScan(task)
                is AppControlToggleTask -> performToggle(task)
                is UninstallTask -> performUninstall(task)
                else -> throw UnsupportedOperationException("Unsupported task: $task")
            }

            log(TAG, INFO) { "submit($task) finished: $result" }
            result
        } finally {
            updateProgress { null }
        }
    }

    private suspend fun performScan(task: AppControlScanTask): AppControlScanTask.Result {
        log(TAG, VERBOSE) { "performScan(): $task" }

        internalData.value = null

        val currentUserHandle = userManager.currentUser().handle

        val appInfos = pkgRepo.currentPkgs()
            .filter { it.userHandle == currentUserHandle }
            .map { it.toAppInfo() }

        internalData.value = Data(
            apps = appInfos,
        )

        return AppControlScanTask.Success(
            itemCount = appInfos.size
        )
    }

    private suspend fun performToggle(task: AppControlToggleTask): AppControlToggleTask.Result {
        log(TAG) { "performToggle(): $task" }

        if (!rootManager.useRoot()) throw RootRequiredException("Toggeling apps requires root")

        val snapshot = internalData.value ?: throw IllegalStateException("App data wasn't loaded")

        val successful = mutableSetOf<Pkg.Id>()
        val failed = mutableSetOf<Pkg.Id>()
        updateProgressCount(Progress.Count.Percent(0, task.targets.size))

        pkgOps.useRes {
            task.targets.forEach { targetId ->
                val target = snapshot.apps.single { it.id == targetId }
                val oldState = target.pkg.isEnabled
                val newState = !oldState
                log(TAG) { "Toggeling $targetId enabled state $oldState -> $newState" }

                updateProgressPrimary(target.label)
                updateProgressSecondary(
                    if (newState) R.string.appcontrol_progress_enabling_package
                    else R.string.appcontrol_progress_disabling_package
                )

                try {
                    pkgOps.changePackageState(targetId, newState)
                    successful.add(targetId)
                    log(TAG, INFO) { "State successfully changed to $newState" }
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Failed to change state for $targetId: ${e.asLog()}" }
                    failed.add(targetId)
                } finally {
                    increaseProgress()
                }
            }
        }

        internalData.value = snapshot.copy(
            apps = snapshot.apps.mapNotNull { app ->
                when {
                    successful.contains(app.id) || failed.contains(app.id) -> {
                        val fresh = pkgRepo.refresh(app.id)
                        // TODO if the app is suddenly no longer installed, show the user an error?
                        fresh.map { it.toAppInfo() }
                    }
                    else -> setOf(app)
                }
            }.flatten()
        )

        return AppControlToggleTask.Success(successful, failed)
    }

    private suspend fun performUninstall(task: UninstallTask): UninstallTask.Result {
        log(TAG) { "performUninstall(): $task" }
        updateProgressCount(Progress.Count.Percent(0, task.targets.size))

        val snapshot = internalData.value ?: throw IllegalStateException("App data wasn't loaded")
        val successful = mutableSetOf<Installed.InstallId>()
        val failed = mutableSetOf<Installed.InstallId>()

        uninstaller.useRes {
            task.targets.forEach { targetId ->
                val target = snapshot.apps.single { it.installId == targetId }
                updateProgressPrimary(target.label)
                updateProgressSecondary(R.string.appcontrol_progress_uninstalling_app)

                try {
                    uninstaller.uninstall(target.installId)
                    successful.add(target.installId)
                    log(TAG, INFO) { "Successfully uninstalled $target" }
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Failed to uninstall $targetId: ${e.asLog()}" }
                    failed.add(targetId)
                } finally {
                    increaseProgress()
                }
            }
        }

        internalData.value = snapshot.copy(
            apps = snapshot.apps.mapNotNull { app ->
                when {
                    successful.contains(app.installId) || failed.contains(app.installId) -> {
                        // TODO on multi app uninstalls, this refreshes the data too often, once would be enough
                        val fresh = pkgRepo.refresh(app.id)
                        fresh.map { it.toAppInfo() }
                    }
                    else -> setOf(app)
                }
            }.flatten()
        )

        return UninstallTask.Result(successful, failed)
    }

    private suspend fun Installed.toAppInfo(): AppInfo {
        return AppInfo(
            pkg = this
        )
    }

    data class Data(
        val apps: Collection<AppInfo>
    )

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: AppControl): SDMTool
    }

    companion object {
        var lastUninstalledPkg: Pkg.Id? = null
            set(value) {
                field = value
                log(TAG) { "Updated lastUninstalledPkg to $value" }
            }
        private val TAG = logTag("AppControl")
    }
}