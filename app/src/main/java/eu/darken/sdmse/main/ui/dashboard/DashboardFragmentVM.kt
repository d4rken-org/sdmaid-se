package eu.darken.sdmse.main.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.autoreport.DebugSettings
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.randomString
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderScanTask
import eu.darken.sdmse.corpsefinder.ui.CorpseFinderCardVH
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.ui.dashboard.items.DataAreaCardVH
import eu.darken.sdmse.main.ui.dashboard.items.DebugCardVH
import eu.darken.sdmse.main.ui.dashboard.items.SetupCardVH
import eu.darken.sdmse.setup.SetupManager
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
    private val debugSettings: DebugSettings,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val refreshTrigger = MutableStateFlow(randomString())
    private var isSetupDismissed = false
    val dashboardevents = SingleLiveEvent<DashboardEvents>()

    private val debugItem = combine(
        debugSettings.isDebugMode.flow,
        debugSettings.isTraceMode.flow
    ) { isDebug, isTrace ->
        if (!isDebug) return@combine null
        DebugCardVH.Item(
            isTraceEnabled = isTrace,
            onTraceEnabled = { debugSettings.isTraceMode.valueBlocking = it },
            onRunTest = {

            }
        )
    }


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

    val listItems: LiveData<List<DashboardAdapter.Item>> = combine(
        debugItem,
        setupManager.state,
        dataAreaInfo,
        corpseFinderItem,
        refreshTrigger,
    ) { debugItem: DebugCardVH.Item?,
        setupState: SetupManager.SetupState,
        dataAreaInfo: DataAreaCardVH.Item?,
        corpseFinderItem: CorpseFinderCardVH.Item,
        _ ->
        val items = mutableListOf<DashboardAdapter.Item>()

        debugItem?.let { items.add(it) }

        if (!setupState.isComplete && !isSetupDismissed) {
            SetupCardVH.Item(
                setupState = setupState,
                onDismiss = {
                    isSetupDismissed = true
                    refreshTrigger.value = randomString()
                },
                onContinue = {
                    DashboardFragmentDirections.actionDashboardFragmentToSetupFragment().navigate()
                }
            ).run { items.add(this) }
        }

        dataAreaInfo?.let { items.add(it) }

        items.add(corpseFinderItem)

        items
    }
        .setupCommonEventHandlers(TAG) { "listItems" }
        .asLiveData2()


    companion object {
        private val TAG = logTag("Dashboard", "Fragment", "VM")
    }
}