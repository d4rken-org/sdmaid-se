package eu.darken.sdmse.main.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.GatewaySwitch
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.files.core.walk
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.randomString
import eu.darken.sdmse.common.storage.SAFMapper
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderScanTask
import eu.darken.sdmse.corpsefinder.ui.CorpseFinderCardVH
import eu.darken.sdmse.main.core.taskmanager.TaskManager
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
    private val pkgOps: PkgOps,
    private val taskManager: TaskManager,
    private val safMapper: SAFMapper,
    private val gatewaySwitch: GatewaySwitch,
    private val setupManager: SetupManager,
    private val corpseFinder: CorpseFinder,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val refreshTrigger = MutableStateFlow(randomString())
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
                launch { taskManager.submit(CorpseFinderScanTask()) }
            },
            onDelete = {
                launch { taskManager.submit(CorpseFinderDeleteTask()) }
            }
        )
    }

    val listItems: LiveData<List<DashboardAdapter.Item>> = combine(
        setupManager.state,
        corpseFinderItem,
        refreshTrigger,
    ) { setupState, corpseFinderItem, _ ->
        val items = mutableListOf<DashboardAdapter.Item>()
        if (BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.DEV) {
            DebugCardVH.Item(
                onCheck = {
                    launch {
                        LocalPath.build("/storage/emulated/0")
                            .walk(gatewaySwitch)
                            .collectLatest {
                                log(TAG) { "WALK: $it" }
                            }
                    }
                },
                onSAF = {

                }
            ).run { items.add(this) }
        }
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


        items.add(corpseFinderItem)

        items
    }
        .setupCommonEventHandlers(TAG) { "listItems" }
        .asLiveData2()


    companion object {
        private val TAG = logTag("Dashboard", "Fragment", "VM")
    }
}