package eu.darken.sdmse.main.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.hasData
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerDeleteTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
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
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.DebugCardProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.hasData
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderScanTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderTask
import eu.darken.sdmse.corpsefinder.ui.CorpseFinderDashCardVH
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.ui.dashboard.items.*
import eu.darken.sdmse.scheduler.core.SchedulerManager
import eu.darken.sdmse.scheduler.ui.SchedulerDashCardVH
import eu.darken.sdmse.setup.SetupManager
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.hasData
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerDeleteTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerScanTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerTask
import eu.darken.sdmse.systemcleaner.ui.SystemCleanerDashCardVH
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class DashboardFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val areaManager: DataAreaManager,
    private val taskManager: TaskManager,
    private val setupManager: SetupManager,
    private val corpseFinder: CorpseFinder,
    private val systemCleaner: SystemCleaner,
    private val appCleaner: AppCleaner,
    private val appControl: AppControl,
    private val debugCardProvider: DebugCardProvider,
    private val upgradeRepo: UpgradeRepo,
    private val generalSettings: GeneralSettings,
    private val webpageTool: WebpageTool,
    private val schedulerManager: SchedulerManager,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    init {
        if (!generalSettings.isOnboardingCompleted.valueBlocking) {
            DashboardFragmentDirections.actionDashboardFragmentToOnboardingWelcomeFragment().navigate()
        }
    }

    private val refreshTrigger = MutableStateFlow(rngString)

    val events = SingleLiveEvent<DashboardEvents>()

    private val upgradeInfo: Flow<UpgradeRepo.Info?> = upgradeRepo.upgradeInfo
        .map {
            @Suppress("USELESS_CAST")
            it as UpgradeRepo.Info?
        }
        .onStart { emit(null) }

    private val corpseFinderItem: Flow<CorpseFinderDashCardVH.Item> = combine(
        corpseFinder.data,
        corpseFinder.progress,
    ) { data, progress ->
        CorpseFinderDashCardVH.Item(
            data = data,
            progress = progress,
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
    private val systemCleanerItem: Flow<SystemCleanerDashCardVH.Item> = combine(
        systemCleaner.data,
        systemCleaner.progress,
    ) { data, progress ->
        SystemCleanerDashCardVH.Item(
            data = data,
            progress = progress,
            onScan = {
                launch { submitTask(SystemCleanerScanTask()) }
            },
            onDelete = {
                val task = SystemCleanerDeleteTask()
                events.postValue(DashboardEvents.SystemCleanerDeleteConfirmation(task))
            },
            onCancel = {
                launch { taskManager.cancel(SDMTool.Type.SYSTEMCLEANER) }
            },
            onViewDetails = { showSystemCleanerDetails() }
        )
    }
    private val appCleanerItem: Flow<AppCleanerDashCardVH.Item> = combine(
        appCleaner.data,
        appCleaner.progress,
        upgradeInfo.map { it?.isPro ?: false },
    ) { data, progress, isPro ->
        AppCleanerDashCardVH.Item(
            data = data,
            progress = progress,
            isPro = isPro,
            onScan = {
                launch { submitTask(AppCleanerScanTask()) }
            },
            onDelete = {
                val task = AppCleanerDeleteTask()
                events.postValue(DashboardEvents.AppCleanerDeleteConfirmation(task))
            },
            onCancel = {
                launch { taskManager.cancel(SDMTool.Type.APPCLEANER) }
            },
            onViewDetails = { showAppCleanerDetails() }
        )
    }

    private val appControlItem: Flow<AppControlDashCardVH.Item?> = combine(
        appControl.data,
        appControl.progress,
    ) { data, progress ->
        AppControlDashCardVH.Item(
            data = data,
            progress = progress,
            onViewDetails = {
                DashboardFragmentDirections.actionDashboardFragmentToAppControlListFragment().navigate()
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
                events.postValue(DashboardEvents.TodoHint)
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

    val listItems: LiveData<List<DashboardAdapter.Item>> = eu.darken.sdmse.common.flow.combine(
        debugCardProvider.create(this),
        upgradeInfo,
        setupManager.state,
        dataAreaItem,
        corpseFinderItem,
        systemCleanerItem,
        appCleanerItem,
        appControlItem,
        schedulerItem,
        refreshTrigger,
    ) { debugItem: DebugCardVH.Item?,
        upgradeInfo: UpgradeRepo.Info?,
        setupState: SetupManager.SetupState,
        dataAreaInfo: DataAreaCardVH.Item?,
        corpseFinderItem: CorpseFinderDashCardVH.Item?,
        systemCleanerItem: SystemCleanerDashCardVH.Item?,
        appCleanerItem: AppCleanerDashCardVH.Item?,
        appControlItem: AppControlDashCardVH.Item?,
        schedulerItem: SchedulerDashCardVH.Item?,
        _ ->
        val items = mutableListOf<DashboardAdapter.Item>()

        TitleCardVH.Item(
            upgradeInfo = upgradeInfo,
            onRibbonClicked = {
                webpageTool.open(SdmSeLinks.ISSUES)
            }
        ).run { items.add(this) }

        if (!setupState.isComplete && !setupState.isDismissed) {
            SetupCardVH.Item(
                setupState = setupState,
                onDismiss = {
                    setupManager.setDismissed(true)
                    events.postValue(DashboardEvents.SetupDismissHint)
                },
                onContinue = {
                    DashboardFragmentDirections.actionDashboardFragmentToSetupFragment(
                        showCompleted = false
                    ).navigate()
                }
            ).run { items.add(this) }
        }

        dataAreaInfo?.let { items.add(it) }

        corpseFinderItem?.let { items.add(it) }
        systemCleanerItem?.let { items.add(it) }
        appCleanerItem?.let { items.add(it) }
        appControlItem?.let { items.add(it) }

        schedulerItem?.let { items.add(it) }

        upgradeInfo
            ?.takeIf { !it.isPro }
            ?.let {
                UpgradeCardVH.Item(
                    onUpgrade = { DashboardFragmentDirections.actionDashboardFragmentToUpgradeFragment().navigate() }
                )
            }
            ?.run { items.add(this) }

        debugItem?.let { items.add(it) }

        items
    }
        .throttleLatest(500)
        .let {
            if (Bugs.isTrace) it.setupCommonEventHandlers(TAG) { "listItems" } else it
        }
        .asLiveData2()


    data class BottomBarState(
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
            WORKING,
            WORKING_CANCELABLE
        }
    }

    val bottomBarState = combine(
        upgradeInfo,
        taskManager.state,
        corpseFinder.data,
        systemCleaner.data,
        appCleaner.data,
    ) { upgradeInfo,
        taskState,
        corpseData,
        filterData,
        junkData ->

        val actionState: BottomBarState.Action = when {
            taskState.hasCancellable -> BottomBarState.Action.WORKING_CANCELABLE
            !taskState.isIdle -> BottomBarState.Action.WORKING
            corpseData.hasData || filterData.hasData || junkData.hasData -> BottomBarState.Action.DELETE
            else -> BottomBarState.Action.SCAN
        }
        val activeTasks = taskState.tasks.filter { it.isActive }.size
        val queuedTasks = taskState.tasks.filter { it.isQueued }.size
        val totalItems = (corpseData?.totalCount ?: 0) + (filterData?.totalCount ?: 0) + (junkData?.totalCount ?: 0)
        val totalSize = (corpseData?.totalSize ?: 0L) + (filterData?.totalSize ?: 0L) + (junkData?.totalSize ?: 0L)
        BottomBarState(
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
            when (actionState) {
                BottomBarState.Action.SCAN -> submitTask(CorpseFinderScanTask())
                BottomBarState.Action.WORKING_CANCELABLE -> taskManager.cancel(SDMTool.Type.CORPSEFINDER)
                BottomBarState.Action.WORKING -> {}
                BottomBarState.Action.DELETE -> if (corpseFinder.data.first() != null) {
                    submitTask(CorpseFinderDeleteTask())
                }
            }
        }
        launch {
            when (actionState) {
                BottomBarState.Action.SCAN -> submitTask(SystemCleanerScanTask())
                BottomBarState.Action.WORKING_CANCELABLE -> taskManager.cancel(SDMTool.Type.SYSTEMCLEANER)
                BottomBarState.Action.WORKING -> {}
                BottomBarState.Action.DELETE -> if (systemCleaner.data.first() != null) {
                    submitTask(SystemCleanerDeleteTask())
                }
            }
        }
        launch {
            when (actionState) {
                BottomBarState.Action.SCAN -> submitTask(AppCleanerScanTask())
                BottomBarState.Action.WORKING_CANCELABLE -> taskManager.cancel(SDMTool.Type.APPCLEANER)
                BottomBarState.Action.WORKING -> {}
                BottomBarState.Action.DELETE -> {
                    if (appCleaner.data.first() != null && upgradeRepo.isPro()) {
                        submitTask(AppCleanerDeleteTask())
                    } else if (appCleaner.data.first().hasData && !corpseFinder.data.first().hasData && !systemCleaner.data.first().hasData) {
                        DashboardFragmentDirections.actionDashboardFragmentToUpgradeFragment().navigate()
                    }
                }
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
        submitTask(SystemCleanerDeleteTask())
    }

    fun showSystemCleanerDetails() {
        log(TAG, INFO) { "showSystemCleanerDetails()" }
        DashboardFragmentDirections.actionDashboardFragmentToSystemCleanerListFragment().navigate()
    }

    fun confirmAppJunkDeletion() = launch {
        log(TAG, INFO) { "confirmAppJunkDeletion()" }

        if (!upgradeRepo.isPro()) {
            DashboardFragmentDirections.actionDashboardFragmentToUpgradeFragment().navigate()
            return@launch
        }
        submitTask(AppCleanerDeleteTask())
    }

    fun showAppCleanerDetails() {
        log(TAG, INFO) { "showAppCleanerDetails()" }
        DashboardFragmentDirections.actionDashboardFragmentToAppCleanerListFragment().navigate()
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
                is CorpseFinderDeleteTask.Success -> events.postValue(DashboardEvents.TaskResult(result))
            }
            is SystemCleanerTask.Result -> when (result) {
                is SystemCleanerScanTask.Success -> {}
                is SystemCleanerDeleteTask.Success -> events.postValue(DashboardEvents.TaskResult(result))
            }
            is AppCleanerTask.Result -> when (result) {
                is AppCleanerScanTask.Success -> {}
                is AppCleanerDeleteTask.Success -> events.postValue(DashboardEvents.TaskResult(result))
            }
        }
    }

    companion object {
        private val TAG = logTag("Dashboard", "Fragment", "VM")
    }
}