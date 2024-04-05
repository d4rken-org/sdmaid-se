package eu.darken.sdmse.main.ui.dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.ui.AnalyzerDashCardVH
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.hasData
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerOneClickTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerProcessingTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerSchedulerTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerTask
import eu.darken.sdmse.appcleaner.ui.AppCleanerDashCardVH
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.appcontrol.ui.AppControlDashCardVH
import eu.darken.sdmse.common.SdmSeLinks
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.DebugCardProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.debug.recorder.core.RecorderModule
import eu.darken.sdmse.common.debug.recorder.ui.DebugRecorderCardVH
import eu.darken.sdmse.common.flow.intervalFlow
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.updater.UpdateService
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.hasData
import eu.darken.sdmse.corpsefinder.core.tasks.*
import eu.darken.sdmse.corpsefinder.ui.CorpseFinderDashCardVH
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorDeleteTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorOneClickTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorScanTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorTask
import eu.darken.sdmse.deduplicator.ui.DeduplicatorDashCardVH
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.motd.MotdRepo
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.ui.dashboard.items.*
import eu.darken.sdmse.scheduler.core.SchedulerManager
import eu.darken.sdmse.scheduler.ui.SchedulerDashCardVH
import eu.darken.sdmse.setup.SetupManager
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.hasData
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerOneClickTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerProcessingTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerScanTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerSchedulerTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerTask
import eu.darken.sdmse.systemcleaner.ui.SystemCleanerDashCardVH
import kotlinx.coroutines.flow.*
import java.time.Duration
import java.time.Instant
import java.util.*
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val areaManager: DataAreaManager,
    private val taskManager: TaskManager,
    private val setupManager: SetupManager,
    private val corpseFinder: CorpseFinder,
    private val systemCleaner: SystemCleaner,
    private val appCleaner: AppCleaner,
    appControl: AppControl,
    analyzer: Analyzer,
    debugCardProvider: DebugCardProvider,
    private val deduplicator: Deduplicator,
    private val upgradeRepo: UpgradeRepo,
    private val generalSettings: GeneralSettings,
    private val webpageTool: WebpageTool,
    schedulerManager: SchedulerManager,
    private val updateService: UpdateService,
    private val recorderModule: RecorderModule,
    private val motdRepo: MotdRepo,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    init {
        if (!generalSettings.isOnboardingCompleted.valueBlocking) {
            DashboardFragmentDirections.actionDashboardFragmentToOnboardingWelcomeFragment().navigate()
        }
    }

    private val refreshTrigger = MutableStateFlow(rngString)

    val events = SingleLiveEvent<DashboardEvents>()

    private val updateInfo: Flow<UpdateCardVH.Item?> = updateService.availableUpdate
        .map { update ->
            if (update == null) {
                log(TAG, INFO) { "No update available" }
                return@map null
            }

            try {
                return@map UpdateCardVH.Item(
                    update = update,
                    onDismiss = {
                        launch {
                            updateService.dismissUpdate(update)
                            updateService.refresh()
                        }
                    },
                    onViewUpdate = {
                        launch { updateService.viewUpdate(update) }
                    },
                    onUpdate = {
                        launch { updateService.startUpdate(update) }
                    }
                )
            } catch (e: Exception) {
                log(TAG, WARN) { "Update check failed: ${e.asLog()}" }
                return@map null
            }
        }
        .onStart { emit(null) }

    private val upgradeInfo: Flow<UpgradeRepo.Info?> = upgradeRepo.upgradeInfo
        .map {
            @Suppress("USELESS_CAST")
            it as UpgradeRepo.Info?
        }
        .setupCommonEventHandlers(TAG) { "upgradeInfo" }
        .replayingShare(vmScope)

    private val titleCardItem = combine(
        upgradeInfo,
        taskManager.state
    ) { upgradeInfo, taskState ->
        TitleCardVH.Item(
            upgradeInfo = upgradeInfo,
            webpageTool = webpageTool,
            isWorking = !taskState.isIdle,
            onRibbonClicked = { webpageTool.open(SdmSeLinks.ISSUES) },
        )
    }

    private val corpseFinderItem: Flow<CorpseFinderDashCardVH.Item> = corpseFinder.state.map { state ->
        CorpseFinderDashCardVH.Item(
            data = state.data,
            progress = state.progress,
            onScan = {
                launch { submitTask(CorpseFinderScanTask()) }
            },
            onDelete = {
                val task = CorpseFinderDeleteTask()
                events.postValue(DashboardEvents.CorpseFinderDeleteConfirmation(task))
            },
            onCancel = {
                launch { taskManager.cancel(SDMTool.Type.CORPSEFINDER) }
            },
            onViewDetails = { showCorpseFinderDetails() }
        )
    }
    private val systemCleanerItem: Flow<SystemCleanerDashCardVH.Item> = systemCleaner.state.map { state ->
        SystemCleanerDashCardVH.Item(
            data = state.data,
            progress = state.progress,
            onScan = {
                launch { submitTask(SystemCleanerScanTask()) }
            },
            onDelete = {
                val task = SystemCleanerProcessingTask()
                events.postValue(DashboardEvents.SystemCleanerDeleteConfirmation(task))
            },
            onCancel = {
                launch { taskManager.cancel(SDMTool.Type.SYSTEMCLEANER) }
            },
            onViewDetails = { showSystemCleanerDetails() }
        )
    }
    private val appCleanerItem: Flow<AppCleanerDashCardVH.Item> = combine(
        appCleaner.state,
        upgradeInfo.map { it?.isPro ?: false },
    ) { state, isPro ->
        AppCleanerDashCardVH.Item(
            data = state.data,
            progress = state.progress,
            isPro = isPro,
            onScan = {
                launch { submitTask(AppCleanerScanTask()) }
            },
            onDelete = {
                val task = AppCleanerProcessingTask()
                events.postValue(DashboardEvents.AppCleanerDeleteConfirmation(task))
            },
            onCancel = {
                launch { taskManager.cancel(SDMTool.Type.APPCLEANER) }
            },
            onViewDetails = { showAppCleanerDetails() }
        )
    }

    private val deduplicatorItem: Flow<DeduplicatorDashCardVH.Item?> = deduplicator.state.map { state ->
        DeduplicatorDashCardVH.Item(
            data = state.data,
            progress = state.progress,
            onScan = {
                launch { submitTask(DeduplicatorScanTask()) }
            },
            onDelete = {
                launch {
                    val event = DashboardEvents.DeduplicatorDeleteConfirmation(
                        task = DeduplicatorDeleteTask(),
                        clusters = deduplicator.state.first().data?.clusters?.sortedByDescending { it.averageSize }
                    )
                    events.postValue(event)
                }
            },
            onCancel = {
                launch { taskManager.cancel(SDMTool.Type.DEDUPLICATOR) }
            },
            onViewDetails = { showDeduplicatorDetails() }
        )
    }

    private val appControlItem: Flow<AppControlDashCardVH.Item?> = appControl.state.mapLatest { state ->
        AppControlDashCardVH.Item(
            data = state.data,
            progress = state.progress,
            onViewDetails = {
                DashboardFragmentDirections.actionDashboardFragmentToAppControlListFragment().navigate()
            }
        )
    }

    private val analyzerItem: Flow<AnalyzerDashCardVH.Item?> = combine(
        analyzer.data,
        analyzer.progress,
    ) { data, progress ->
        AnalyzerDashCardVH.Item(
            data = data,
            progress = progress,
            onViewDetails = {
                DashboardFragmentDirections.actionDashboardFragmentToDeviceStorageFragment().navigate()
            }
        )
    }

    private val schedulerItem: Flow<SchedulerDashCardVH.Item?> = combine(
        schedulerManager.state,
        taskManager.state,
    ) { schedulerState, taskState ->
        SchedulerDashCardVH.Item(
            schedulerState = schedulerState,
            taskState = taskState,
            onManageClicked = {
                DashboardFragmentDirections.actionDashboardFragmentToSchedulerManagerFragment().navigate()
            }
        )
    }

    private val dataAreaItem: Flow<DataAreaCardVH.Item?> = areaManager.latestState
        .map {
            if (it == null) return@map null
            if (it.areas.isNotEmpty()) return@map null
            DataAreaCardVH.Item(
                state = it,
                onReload = {
                    launch {
                        areaManager.reload()
                    }
                }
            )
        }

    private val motdItem: Flow<MotdCardVH.Item?> = motdRepo.motd
        .map {
            if (it == null) return@map null
            MotdCardVH.Item(
                state = it,
                onPrimary = {
                    launch {
                        it.motd.primaryLink?.let { webpageTool.open(it) }
                    }
                },
                onTranslate = {
                    launch {
                        webpageTool.open(it.translationUrl)
                    }
                },
                onDismiss = {
                    launch { motdRepo.dismiss(it.id) }
                }
            )
        }

    private val setupCardItem: Flow<SetupCardVH.Item?> = setupManager.state
        .flatMapLatest { setupState ->
            if (setupState.isDone || setupState.isDismissed) return@flatMapLatest flowOf(null)

            val item = SetupCardVH.Item(
                setupState = setupState,
                onDismiss = {
                    setupManager.setDismissed(true)
                    events.postValue(DashboardEvents.SetupDismissHint)
                },
                onContinue = { MainDirections.goToSetup().navigate() }
            )

            if (setupState.isIncomplete) return@flatMapLatest flowOf(item)

            if (!setupState.isLoading) return@flatMapLatest flowOf(null)

            intervalFlow(1.seconds).map {
                val now = Instant.now()
                val loadingStart = setupState.startedLoadingAt ?: now
                if (Duration.between(loadingStart, now) >= Duration.ofSeconds(3)) {
                    item
                } else {
                    null
                }
            }
        }

    private val listItemsInternal: Flow<List<DashboardAdapter.Item>> = eu.darken.sdmse.common.flow.combine(
        recorderModule.state,
        debugCardProvider.create(this),
        titleCardItem,
        upgradeInfo,
        updateInfo,
        setupCardItem,
        dataAreaItem,
        corpseFinderItem,
        systemCleanerItem,
        appCleanerItem,
        deduplicatorItem,
        appControlItem,
        analyzerItem,
        schedulerItem,
        motdItem,
        refreshTrigger,
    ) { recorderState: RecorderModule.State,
        debugItem: DebugCardVH.Item?,
        titleInfo: TitleCardVH.Item,
        upgradeInfo: UpgradeRepo.Info?,
        updateInfo: UpdateCardVH.Item?,
        setupItem: SetupCardVH.Item?,
        dataAreaInfo: DataAreaCardVH.Item?,
        corpseFinderItem: CorpseFinderDashCardVH.Item?,
        systemCleanerItem: SystemCleanerDashCardVH.Item?,
        appCleanerItem: AppCleanerDashCardVH.Item?,
        deduplicatorItem: DeduplicatorDashCardVH.Item?,
        appControlItem: AppControlDashCardVH.Item?,
        analyzerItem: AnalyzerDashCardVH.Item?,
        schedulerItem: SchedulerDashCardVH.Item?,
        motdItem: MotdCardVH.Item?,
        _ ->
        val items = mutableListOf<DashboardAdapter.Item>(titleInfo)

        motdItem?.let { items.add(it) }
        updateInfo?.let { items.add(it) }
        setupItem?.let { items.add(it) }
        dataAreaInfo?.let { items.add(it) }

        corpseFinderItem?.let { items.add(it) }
        systemCleanerItem?.let { items.add(it) }
        appCleanerItem?.let { items.add(it) }
        deduplicatorItem?.let { items.add(it) }
        appControlItem?.let { items.add(it) }
        analyzerItem?.let { items.add(it) }

        schedulerItem?.let { items.add(it) }

        upgradeInfo
            ?.takeIf { !it.isPro }
            ?.let {
                UpgradeCardVH.Item(
                    onUpgrade = { MainDirections.goToUpgradeFragment().navigate() }
                )
            }
            ?.run { items.add(this) }

        recorderState
            .takeIf { it.isRecording || debugItem != null }
            ?.let {
                val item = DebugRecorderCardVH.Item(
                    webpageTool = webpageTool,
                    state = it,
                    onToggleRecording = {
                        if (it.isRecording) {
                            launch { recorderModule.stopRecorder() }
                        } else {
                            launch { recorderModule.startRecorder() }
                        }
                    }
                )
                if (it.isRecording) items.add(1, item) else items.add(item)
            }

        debugItem?.let { items.add(it) }

        items
    }
        .throttleLatest(500)
        .replayingShare(vmScope)

    val listItems = listItemsInternal.asLiveData()


    data class BottomBarState(
        val isReady: Boolean,
        val actionState: Action,
        val activeTasks: Int,
        val queuedTasks: Int,
        val totalItems: Int,
        val totalSize: Long,
        val upgradeInfo: UpgradeRepo.Info?,
    ) {
        enum class Action {
            SCAN,
            DELETE,
            ONECLICK,
            WORKING,
            WORKING_CANCELABLE
        }
    }

    val bottomBarState = eu.darken.sdmse.common.flow.combine(
        upgradeInfo,
        taskManager.state,
        corpseFinder.state,
        systemCleaner.state,
        appCleaner.state,
        generalSettings.enableDashboardOneClick.flow,
        listItemsInternal.map { items -> items.any { it is MainActionItem } },
    ) { upgradeInfo,
        taskState,
        corpseState,
        filterState,
        junkState,
        oneClickMode,
        listIsReady ->

        val actionState: BottomBarState.Action = when {
            taskState.hasCancellable -> BottomBarState.Action.WORKING_CANCELABLE
            !taskState.isIdle -> BottomBarState.Action.WORKING
            corpseState.data.hasData || filterState.data.hasData || junkState.data.hasData -> BottomBarState.Action.DELETE
            oneClickMode -> BottomBarState.Action.ONECLICK
            else -> BottomBarState.Action.SCAN
        }
        val activeTasks = taskState.tasks.filter { it.isActive }.size
        val queuedTasks = taskState.tasks.filter { it.isQueued }.size
        val totalItems =
            (corpseState.data?.totalCount ?: 0) + (filterState.data?.totalCount ?: 0) + (junkState.data?.totalCount
                ?: 0)
        val totalSize =
            (corpseState.data?.totalSize ?: 0L) + (filterState.data?.totalSize ?: 0L) + (junkState.data?.totalSize
                ?: 0L)
        BottomBarState(
            isReady = listIsReady,
            actionState = actionState,
            activeTasks = activeTasks,
            queuedTasks = queuedTasks,
            totalItems = totalItems,
            totalSize = totalSize,
            upgradeInfo = upgradeInfo,
        )
    }
        .asLiveData2()

    fun mainAction(actionState: BottomBarState.Action) {
        log(TAG) { "mainAction(actionState=$actionState)" }
        launch {
            if (!generalSettings.oneClickCorpseFinderEnabled.value()) {
                log(VERBOSE) { "CorpseFinder is disabled one-click mode." }
                return@launch
            }

            when (actionState) {
                BottomBarState.Action.SCAN -> submitTask(CorpseFinderScanTask())
                BottomBarState.Action.WORKING_CANCELABLE -> taskManager.cancel(SDMTool.Type.CORPSEFINDER)
                BottomBarState.Action.WORKING -> {}
                BottomBarState.Action.DELETE -> if (corpseFinder.state.first().data != null) {
                    submitTask(CorpseFinderDeleteTask())
                }

                BottomBarState.Action.ONECLICK -> submitTask(CorpseFinderOneClickTask())
            }
        }
        launch {
            if (!generalSettings.oneClickSystemCleanerEnabled.value()) {
                log(VERBOSE) { "SystemCleaner is disabled one-click mode." }
                return@launch
            }

            when (actionState) {
                BottomBarState.Action.SCAN -> submitTask(SystemCleanerScanTask())
                BottomBarState.Action.WORKING_CANCELABLE -> taskManager.cancel(SDMTool.Type.SYSTEMCLEANER)
                BottomBarState.Action.WORKING -> {}
                BottomBarState.Action.DELETE -> if (systemCleaner.state.first().data != null) {
                    submitTask(SystemCleanerProcessingTask())
                }

                BottomBarState.Action.ONECLICK -> submitTask(SystemCleanerOneClickTask())
            }
        }
        launch {
            if (!generalSettings.oneClickAppCleanerEnabled.value()) {
                log(VERBOSE) { "AppCleaner is disabled one-click mode." }
                return@launch
            }

            when (actionState) {
                BottomBarState.Action.SCAN -> submitTask(AppCleanerScanTask())
                BottomBarState.Action.WORKING_CANCELABLE -> taskManager.cancel(SDMTool.Type.APPCLEANER)
                BottomBarState.Action.WORKING -> {}
                BottomBarState.Action.DELETE -> {
                    if (appCleaner.state.first().data != null && upgradeRepo.isPro()) {
                        submitTask(AppCleanerProcessingTask())
                    } else if (appCleaner.state.first().data.hasData && !corpseFinder.state.first().data.hasData && !systemCleaner.state.first().data.hasData) {
                        MainDirections.goToUpgradeFragment().navigate()
                    }
                }

                BottomBarState.Action.ONECLICK -> {
                    if (upgradeRepo.isPro()) {
                        submitTask(AppCleanerOneClickTask())
                    } else if (appCleaner.state.first().data.hasData && !corpseFinder.state.first().data.hasData && !systemCleaner.state.first().data.hasData) {
                        MainDirections.goToUpgradeFragment().navigate()
                    }
                }
            }
        }
        launch {
            if (!generalSettings.oneClickDeduplicatorEnabled.value()) {
                log(VERBOSE) { "Deduplicator is disabled one-click mode." }
                return@launch
            }

            when (actionState) {
                BottomBarState.Action.SCAN -> submitTask(DeduplicatorScanTask())
                BottomBarState.Action.WORKING_CANCELABLE -> taskManager.cancel(SDMTool.Type.DEDUPLICATOR)
                BottomBarState.Action.WORKING -> {}
                BottomBarState.Action.DELETE -> if (deduplicator.state.first().data != null) {
                    submitTask(DeduplicatorDeleteTask())
                }

                BottomBarState.Action.ONECLICK -> submitTask(DeduplicatorOneClickTask())
            }
        }
    }

    fun confirmCorpseDeletion() = launch {
        log(TAG, INFO) { "confirmCorpseDeletion()" }
        submitTask(CorpseFinderDeleteTask())
    }

    fun showCorpseFinderDetails() {
        log(TAG, INFO) { "showCorpseFinderDetails()" }
        DashboardFragmentDirections.actionDashboardFragmentToCorpseFinderListFragment().navigate()
    }

    fun confirmFilterContentDeletion() = launch {
        log(TAG, INFO) { "confirmFilterContentDeletion()" }
        submitTask(SystemCleanerProcessingTask())
    }

    fun showSystemCleanerDetails() {
        log(TAG, INFO) { "showSystemCleanerDetails()" }
        DashboardFragmentDirections.actionDashboardFragmentToSystemCleanerListFragment().navigate()
    }

    fun confirmAppJunkDeletion() = launch {
        log(TAG, INFO) { "confirmAppJunkDeletion()" }

        if (!upgradeRepo.isPro()) {
            MainDirections.goToUpgradeFragment().navigate()
            return@launch
        }
        submitTask(AppCleanerProcessingTask())
    }

    fun showAppCleanerDetails() {
        log(TAG, INFO) { "showAppCleanerDetails()" }
        DashboardFragmentDirections.actionDashboardFragmentToAppCleanerListFragment().navigate()
    }

    fun showDeduplicatorDetails() {
        log(TAG, INFO) { "showDeduplicatorDetails()" }
        DashboardFragmentDirections.actionDashboardFragmentToDeduplicatorListFragment().navigate()
    }

    fun confirmDeduplicatorDeletion() = launch {
        log(TAG, INFO) { "confirmDeduplicatorDeletion()" }

        if (!upgradeRepo.isPro()) {
            MainDirections.goToUpgradeFragment().navigate()
            return@launch
        }
        submitTask(DeduplicatorDeleteTask())
    }

    fun undoSetupHide() = launch {
        log(TAG) { "undoSetupHide()" }
        setupManager.setDismissed(false)
    }

    private suspend fun submitTask(task: SDMTool.Task) {
        log(TAG, VERBOSE) { "Submitting $task" }
        val result = taskManager.submit(task)
        log(TAG, VERBOSE) { "Task result for $task was $result" }
        when (result) {
            is CorpseFinderTask.Result -> when (result) {
                is CorpseFinderScanTask.Success -> {}
                is UninstallWatcherTask.Success -> {}
                is CorpseFinderSchedulerTask.Success -> {}
                is CorpseFinderDeleteTask.Success -> events.postValue(DashboardEvents.TaskResult(result))
                is CorpseFinderOneClickTask.Success -> events.postValue(DashboardEvents.TaskResult(result))
            }

            is SystemCleanerTask.Result -> when (result) {
                is SystemCleanerScanTask.Success -> {}
                is SystemCleanerSchedulerTask.Success -> {}
                is SystemCleanerProcessingTask.Success -> events.postValue(DashboardEvents.TaskResult(result))
                is SystemCleanerOneClickTask.Success -> events.postValue(DashboardEvents.TaskResult(result))
            }

            is AppCleanerTask.Result -> when (result) {
                is AppCleanerScanTask.Success -> {}
                is AppCleanerSchedulerTask.Success -> {}
                is AppCleanerProcessingTask.Success -> events.postValue(DashboardEvents.TaskResult(result))
                is AppCleanerOneClickTask.Success -> events.postValue(DashboardEvents.TaskResult(result))
            }

            is DeduplicatorTask.Result -> when (result) {
                is DeduplicatorScanTask.Success -> {}
                is DeduplicatorDeleteTask.Success -> events.postValue(DashboardEvents.TaskResult(result))
                is DeduplicatorOneClickTask.Success -> events.postValue(DashboardEvents.TaskResult(result))
            }
        }
    }

    companion object {
        private val TAG = logTag("Dashboard", "ViewModel")
    }
}