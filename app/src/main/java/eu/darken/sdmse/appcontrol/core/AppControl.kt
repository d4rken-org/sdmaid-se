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
import eu.darken.sdmse.common.adb.AdbManager
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
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.isEnabled
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.increaseProgress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.keepResourceHoldersAlive
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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AppControl @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val userManager: UserManager2,
    private val componentToggler: ComponentToggler,
    private val forceStopper: ForceStopper,
    private val uninstaller: Uninstaller,
    usageStatsSetupModule: UsageStatsSetupModule,
    storageSetupModule: StorageSetupModule,
    rootManager: RootManager,
    adbManager: AdbManager,
    private val appExporterProvider: Provider<AppExporter>,
    private val appInventorySetupModule: InventorySetupModule,
    automationManager: AutomationManager,
    private val appScan: AppScan,
) : SDMTool, Progress.Client {

    private val usedResources = setOf(appScan)
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
        adbManager.useAdb,
    ) { data, progress, usageState, storageState, useAcs, useRoot, useAdb ->

        State(
            data = data,
            progress = progress,
            canInfoActive = usageState.isComplete || useRoot || useAdb,
            canInfoSize = usageState.isComplete && storageState.isComplete,
            canInfoScreenTime = usageState.isComplete,
            canToggle = useRoot || useAdb,
            canForceStop = useAcs || useRoot || useAdb,
        )
    }.replayingShare(appScope)

    private val jobLock = Mutex()
    override suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result = jobLock.withLock {
        task as AppControlTask
        log(TAG) { "submit($task) starting..." }
        updateProgress { Progress.Data() }
        try {
            val result = keepResourceHoldersAlive(usedResources) {
                when (task) {
                    is AppControlScanTask -> performScan(task)
                    is AppControlToggleTask -> performToggle(task)
                    is UninstallTask -> performUninstall(task)
                    is AppExportTask -> performExportSave(task)
                    is ForceStopTask -> performForceStop(task)
                    else -> throw UnsupportedOperationException("Unsupported task: $task")
                }
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

        val curState = state.first()

        val appInfos = appScan.run {
            if (task.refreshPkgCache) refresh()
            allApps(
                user = userManager.currentUser().handle,
                includeActive = task.loadInfoActive && curState.canInfoActive,
                includeSize = task.loadInfoSize && curState.canInfoSize,
                includeUsage = task.loadInfoScreenTime && curState.canInfoScreenTime,
            )
        }

        internalData.value = Data(
            apps = appInfos,
            hasInfoScreenTime = task.loadInfoScreenTime && curState.canInfoScreenTime,
            hasInfoActive = task.loadInfoSize && curState.canInfoSize,
            hasInfoSize = task.loadInfoSize && curState.canInfoSize,
        )

        return AppControlScanTask.Result(itemCount = appInfos.size)
    }

    private suspend fun performToggle(task: AppControlToggleTask): AppControlToggleTask.Result {
        log(TAG) { "performToggle(): $task" }

        val snapshot = internalData.value ?: throw IllegalStateException("App data wasn't loaded")

        val enabled = mutableSetOf<InstallId>()
        val disabled = mutableSetOf<InstallId>()
        val failed = mutableSetOf<InstallId>()
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
                    if (newState) enabled.add(targetId) else disabled.add(targetId)
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

        appScan.refresh()

        internalData.value = snapshot.copy(
            apps = run {
                val affectedPkgs = enabled.map { it.pkgId } + disabled.map { it.pkgId } + failed.map { it.pkgId }
                val cleanedSnapshot = snapshot.apps.filterNot { affectedPkgs.contains(it.id) }
                val updatedPkgs = affectedPkgs.map {
                    appScan.app(
                        pkgId = it,
                        includeSize = snapshot.hasInfoSize,
                        includeActive = snapshot.hasInfoActive,
                        includeUsage = snapshot.hasInfoScreenTime
                    )
                }.flatten()
                cleanedSnapshot + updatedPkgs
            }
        )

        return AppControlToggleTask.Result(enabled, disabled, failed)
    }

    private suspend fun performUninstall(task: UninstallTask): UninstallTask.Result {
        log(TAG) { "performUninstall(): $task" }
        updateProgressCount(Progress.Count.Counter(task.targets.size))

        val snapshot = internalData.value ?: throw IllegalStateException("App data wasn't loaded")
        val successful = mutableSetOf<InstallId>()
        val failed = mutableSetOf<InstallId>()

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

        appScan.refresh()

        internalData.value = snapshot.copy(
            apps = run {
                val affectedPkgs = successful.map { it.pkgId } + failed.map { it.pkgId }
                val cleanedSnapshot = snapshot.apps.filterNot { affectedPkgs.contains(it.id) }
                val updatedPkgs = affectedPkgs.map {
                    appScan.app(
                        pkgId = it,
                        includeSize = snapshot.hasInfoSize,
                        includeActive = snapshot.hasInfoActive,
                        includeUsage = snapshot.hasInfoScreenTime
                    )
                }.flatten()
                cleanedSnapshot + updatedPkgs
            }
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

        val successful = mutableSetOf<InstallId>()
        val failed = mutableSetOf<InstallId>()

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


    data class State(
        val data: Data?,
        val progress: Progress.Data?,
        val canToggle: Boolean,
        val canForceStop: Boolean,
        val canInfoSize: Boolean,
        val canInfoActive: Boolean,
        val canInfoScreenTime: Boolean,
    ) : SDMTool.State

    data class Data(
        val id: UUID = UUID.randomUUID(),
        val apps: Collection<AppInfo>,
        val hasInfoScreenTime: Boolean,
        val hasInfoActive: Boolean,
        val hasInfoSize: Boolean,
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