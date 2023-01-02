package eu.darken.sdmse.main.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerDeleteTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
import eu.darken.sdmse.appcleaner.ui.AppCleanerCardVH
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.DebugCardProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderScanTask
import eu.darken.sdmse.corpsefinder.ui.CorpseFinderCardVH
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.ui.dashboard.items.DataAreaCardVH
import eu.darken.sdmse.main.ui.dashboard.items.DebugCardVH
import eu.darken.sdmse.main.ui.dashboard.items.SetupCardVH
import eu.darken.sdmse.setup.SetupManager
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerDeleteTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerScanTask
import eu.darken.sdmse.systemcleaner.ui.SystemCleanerCardVH
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
    private val debugCardProvider: DebugCardProvider,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val refreshTrigger = MutableStateFlow(rngString)
    private var isSetupDismissed = false
    val dashboardevents = SingleLiveEvent<DashboardEvents>()

    private val corpseFinderItem: Flow<CorpseFinderCardVH.Item> = combine(
        corpseFinder.data,
        corpseFinder.progress,
    ) { data, progress ->
        CorpseFinderCardVH.Item(
            data = data,
            progress = progress,
            onScan = {
                launch {
                    taskManager.useRes {
                        taskManager.submit(CorpseFinderScanTask())
                    }
                }
            },
            onDelete = {
                launch { taskManager.submit(CorpseFinderDeleteTask()) }
            },
            onCancel = {
                launch { taskManager.cancel(SDMTool.Type.CORPSEFINDER) }
            },
            onViewDetails = {
                DashboardFragmentDirections.actionDashboardFragmentToCorpseFinderDetailsFragment().navigate()
            }
        )
    }
    private val systemCleanerItem: Flow<SystemCleanerCardVH.Item> = combine(
        systemCleaner.data,
        systemCleaner.progress,
    ) { data, progress ->
        SystemCleanerCardVH.Item(
            data = data,
            progress = progress,
            onScan = {
                launch {
                    taskManager.useRes {
                        taskManager.submit(SystemCleanerScanTask())
                    }
                }
            },
            onDelete = {
                launch { taskManager.submit(SystemCleanerDeleteTask()) }
            },
            onCancel = {
                launch { taskManager.cancel(SDMTool.Type.SYSTEMCLEANER) }
            },
            onViewDetails = {
                DashboardFragmentDirections.actionDashboardFragmentToSystemCleanerDetailsFragment().navigate()
            }
        )
    }
    private val appCleanerItem: Flow<AppCleanerCardVH.Item> = combine(
        appCleaner.data,
        appCleaner.progress,
    ) { data, progress ->
        AppCleanerCardVH.Item(
            data = data,
            progress = progress,
            onScan = {
                launch {
                    taskManager.useRes {
                        taskManager.submit(AppCleanerScanTask())
                    }
                }
            },
            onDelete = {
                launch { taskManager.submit(AppCleanerDeleteTask()) }
            },
            onCancel = {
                launch { taskManager.cancel(SDMTool.Type.APPCLEANER) }
            },
            onViewDetails = {
                DashboardFragmentDirections.actionDashboardFragmentToAppCleanerDetailsFragment().navigate()
            }
        )
    }
    private val dataAreaInfo = areaManager.latestState
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
        setupManager.state,
        dataAreaInfo,
        corpseFinderItem,
        systemCleanerItem,
        appCleanerItem,
        refreshTrigger,
    ) { debugItem: DebugCardVH.Item?,
        setupState: SetupManager.SetupState,
        dataAreaInfo: DataAreaCardVH.Item?,
        corpseFinderItem: CorpseFinderCardVH.Item?,
        systemCleanerItem: SystemCleanerCardVH.Item?,
        appCleanerItem: AppCleanerCardVH.Item?,
        _ ->
        val items = mutableListOf<DashboardAdapter.Item>()

        debugItem?.let { items.add(it) }

        if (!setupState.isComplete && !isSetupDismissed) {
            SetupCardVH.Item(
                setupState = setupState,
                onDismiss = {
                    isSetupDismissed = true
                    refreshTrigger.value = rngString
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

        items
    }
        .setupCommonEventHandlers(TAG) { "listItems" }
        .throttleLatest(500)
        .asLiveData2()


    companion object {
        private val TAG = logTag("Dashboard", "Fragment", "VM")
    }
}