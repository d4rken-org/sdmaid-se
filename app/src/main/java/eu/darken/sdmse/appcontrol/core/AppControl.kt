package eu.darken.sdmse.appcontrol.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.export.AppExportTask
import eu.darken.sdmse.appcontrol.core.export.AppExporter
import eu.darken.sdmse.appcontrol.core.forcestop.ForceStopTask
import eu.darken.sdmse.appcontrol.core.forcestop.ForceStopper
import eu.darken.sdmse.appcontrol.core.toggle.AppControlToggleTask
import eu.darken.sdmse.appcontrol.core.toggle.ComponentToggler
import eu.darken.sdmse.appcontrol.core.uninstall.UninstallTask
import eu.darken.sdmse.appcontrol.core.uninstall.Uninstaller
import eu.darken.sdmse.automation.core.AutomationManager
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.container.NormalPkg
import eu.darken.sdmse.common.pkgs.currentPkgs
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.features.SourceAvailable
import eu.darken.sdmse.common.pkgs.isEnabled
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.increaseProgress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.shizuku.ShizukuManager
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.setup.IncompleteSetupException
import eu.darken.sdmse.setup.SetupModule
import eu.darken.sdmse.setup.inventory.InventorySetupModule
import eu.darken.sdmse.setup.isComplete
import eu.darken.sdmse.setup.storage.StorageSetupModule
import eu.darken.sdmse.setup.usagestats.UsageStatsSetupModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AppControl @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val pkgRepo: PkgRepo,
    private val userManager: UserManager2,
    private val componentToggler: ComponentToggler,
    private val forceStopper: ForceStopper,
    private val uninstaller: Uninstaller,
    private val pkgOps: PkgOps,
    usageStatsSetupModule: UsageStatsSetupModule,
    storageSetupModule: StorageSetupModule,
    rootManager: RootManager,
    shizukuManager: ShizukuManager,
    settings: AppControlSettings,
    private val appExporterProvider: Provider<AppExporter>,
    private val appInventorySetupModule: InventorySetupModule,
    automationManager: AutomationManager,
) : SDMTool, Progress.Client {

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope)

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub
    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private val internalData = MutableStateFlow(null as Data?)

    override val type: SDMTool.Type = SDMTool.Type.APPCONTROL

    override val state: Flow<State> = eu.darken.sdmse.common.flow.combine(
        internalData,
        progress,
        usageStatsSetupModule.state,
        storageSetupModule.state,
        automationManager.useAcs,
        rootManager.useRoot,
        shizukuManager.useShizuku,
        settings.moduleSizingEnabled.flow,
        settings.moduleActivityEnabled.flow,
    ) { data, progress, usageState, storageState, useAcs, useRoot, useShizuku, sizingEnabled, activityEnabled ->

        State(
            data = data,
            progress = progress,
            isActiveInfoAvailable = activityEnabled && (usageState.isComplete || useRoot || useShizuku),
            isAppToggleAvailable = (useRoot || useShizuku),
            isSizeInfoAvailable = sizingEnabled && usageState.isComplete && storageState.isComplete,
            isForceStopAvailable = useAcs || useRoot || useShizuku,
        )
    }.replayingShare(appScope)

    private val jobLock = Mutex()
    override suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result = jobLock.withLock {
        task as AppControlTask
        log(TAG) { "submit($task) starting..." }
        updateProgress { Progress.Data() }
        try {
            val result = when (task) {
                is AppControlScanTask -> performScan(task)
                is AppControlToggleTask -> performToggle(task)
                is UninstallTask -> performUninstall(task)
                is AppExportTask -> performExportSave(task)
                is ForceStopTask -> performForceStop(task)
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
        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_loading_app_data)

        internalData.value = null

        if (!appInventorySetupModule.isComplete()) {
            log(TAG, WARN) { "SetupModule INVENTORY is not complete" }
            throw IncompleteSetupException(SetupModule.Type.INVENTORY)
        }

        val currentUserHandle = userManager.currentUser().handle
        val pkgs = if (task.refreshPkgCache) pkgRepo.refresh() else pkgRepo.currentPkgs()
        val appInfos = pkgs
            .filter { it.userHandle == currentUserHandle }
            .map { it.toAppInfo() }

        internalData.value = Data(
            apps = appInfos
        )

        return AppControlScanTask.Result(
            itemCount = appInfos.size
        )
    }

    private suspend fun performToggle(task: AppControlToggleTask): AppControlToggleTask.Result {
        log(TAG) { "performToggle(): $task" }

        val snapshot = internalData.value ?: throw IllegalStateException("App data wasn't loaded")

        val successful = mutableSetOf<Installed.InstallId>()
        val failed = mutableSetOf<Installed.InstallId>()
        updateProgressCount(Progress.Count.Percent(task.targets.size))

        componentToggler.useRes {
            task.targets.forEach { targetId ->
                val target = snapshot.apps.single { it.installId == targetId }
                val oldState = target.pkg.isEnabled
                val newState = !oldState
                log(TAG) { "Toggeling $targetId enabled state $oldState -> $newState" }

                updateProgressPrimary(target.label)
                updateProgressSecondary(
                    if (newState) R.string.appcontrol_progress_enabling_package
                    else R.string.appcontrol_progress_disabling_package
                )

                try {
                    componentToggler.changePackageState(target.installId, newState)
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

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_loading_app_data)
        updateProgressSecondary(CaString.EMPTY)
        updateProgressCount(Progress.Count.Indeterminate())

        val refreshed = pkgRepo.refresh()

        internalData.value = snapshot.copy(
            apps = snapshot.apps.map { app ->
                when {
                    successful.contains(app.installId) || failed.contains(app.installId) -> {
                        // TODO if the app is suddenly no longer installed, show the user an error?
                        refreshed.filter { it.id == app.id }.map { it.toAppInfo() }
                    }

                    else -> setOf(app)
                }
            }.flatten()
        )

        return AppControlToggleTask.Result(successful, failed)
    }

    private suspend fun performUninstall(task: UninstallTask): UninstallTask.Result {
        log(TAG) { "performUninstall(): $task" }
        updateProgressCount(Progress.Count.Counter(task.targets.size))

        val snapshot = internalData.value ?: throw IllegalStateException("App data wasn't loaded")
        val successful = mutableSetOf<Installed.InstallId>()
        val failed = mutableSetOf<Installed.InstallId>()

        uninstaller.useRes {
            task.targets.forEach { targetId ->
                val target = snapshot.apps.single { it.installId == targetId }
                updateProgressPrimary(target.label)
                updateProgressSecondary(R.string.appcontrol_progress_uninstalling_app)

                try {
                    uninstaller.uninstall(target)
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

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_loading_app_data)
        updateProgressSecondary(CaString.EMPTY)
        updateProgressCount(Progress.Count.Indeterminate())

        val refreshed = pkgRepo.refresh()

        internalData.value = snapshot.copy(
            apps = snapshot.apps.map { app ->
                when {
                    successful.contains(app.installId) || failed.contains(app.installId) -> {
                        refreshed.filter { it.id == app.id }.map { it.toAppInfo() }
                    }

                    else -> setOf(app)
                }
            }.flatten()
        )

        return UninstallTask.Result(successful, failed)
    }

    private suspend fun performExportSave(task: AppExportTask): AppExportTask.Result {
        log(TAG) { "performExportSave(): $task" }
        updateProgressPrimary { it.getString(R.string.appcontrol_progress_exporting_x, "...") }
        updateProgressCount(Progress.Count.Counter(task.targets.size))

        val snapshot = internalData.value ?: throw IllegalStateException("App data wasn't loaded")

        val exporter = appExporterProvider.get()

        val exportResults = task.targets
            .asFlow()
            .map { targetId -> snapshot.apps.single { it.installId == targetId } }
            .map { appInfo ->
                updateProgressPrimary {
                    it.getString(R.string.appcontrol_progress_exporting_x, appInfo.label.get(it))
                }
                exporter.withProgress(
                    client = this,
                    onUpdate = { existing, new -> existing?.copy(secondary = new?.primary ?: CaString.EMPTY) },
                    onCompletion = { current -> current }
                ) {
                    exporter.save(appInfo, task.savePath)
                }
            }
            .onEach { increaseProgress() }
            .toList()

        return AppExportTask.Result(
            success = exportResults.toSet(),
            failed = emptySet(),
        )
    }


    private suspend fun performForceStop(task: ForceStopTask): ForceStopTask.Result {
        log(TAG) { "performForceStop(): $task" }
        val snapshot = internalData.value ?: throw IllegalStateException("App data wasn't loaded")

        val successful = mutableSetOf<Installed.InstallId>()
        val failed = mutableSetOf<Installed.InstallId>()

        forceStopper.useRes {
            forceStopper.withProgress(this) {
                val mappedTargets = task.targets.map { id -> snapshot.apps.single { it.installId == id } }
                val result = forceStop(mappedTargets)
                successful.addAll(result.success)
                failed.addAll(result.failed)
            }
        }

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_loading_app_data)
        updateProgressSecondary(CaString.EMPTY)
        updateProgressCount(Progress.Count.Indeterminate())

        internalData.value = snapshot.copy(
            apps = snapshot.apps.map { app ->
                when {
                    successful.contains(app.installId) || failed.contains(app.installId) -> {
                        // TODO Do we need to update some info after force-stopping?
                        app
                    }

                    else -> app
                }
            }
        )

        return ForceStopTask.Result(successful, failed)
    }

    private suspend fun Installed.toAppInfo(): AppInfo {
        val determineActive = state.first().isActiveInfoAvailable
        val determineSizes = state.first().isSizeInfoAvailable

        return AppInfo(
            pkg = this,
            isActive = if (determineActive) pkgOps.isRunning(installId) else null,
            sizes = if (determineSizes) pkgOps.querySizeStats(installId) else null,
            canBeToggled = this is NormalPkg,
            canBeStopped = this is NormalPkg,
            canBeExported = this is SourceAvailable,
            canBeDeleted = true,
        )
    }

    data class State(
        val data: Data?,
        val progress: Progress.Data?,
        val isAppToggleAvailable: Boolean,
        val isActiveInfoAvailable: Boolean,
        val isSizeInfoAvailable: Boolean,
        val isForceStopAvailable: Boolean,
    ) : SDMTool.State

    data class Data(
        val apps: Collection<AppInfo>,
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