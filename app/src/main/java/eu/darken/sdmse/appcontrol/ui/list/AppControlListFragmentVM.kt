package eu.darken.sdmse.appcontrol.ui.list

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.appcontrol.core.tasks.AppControlScanTask
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@HiltViewModel
class AppControlListFragmentVM @Inject constructor(
    private val handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val appControl: AppControl,
    private val taskManager: TaskManager,
) : ViewModel3(dispatcherProvider) {

    val events = SingleLiveEvent<AppControlListEvents>()

    val items = combine(
        appControl.data,
        appControl.progress,
    ) { data, progress ->
        val appInfos = data?.apps
            ?.map { content ->
                AppControlListRowVH.Item(
                    appInfo = content,
                    onItemClicked = {
                        AppControlListFragmentDirections.actionAppControlListFragmentToAppActionDialog(
                            content.pkg.id
                        ).navigate()
                    },
                )
            }
            ?.toList()
        State(
            appInfos = appInfos,
            progress = progress,
        )
    }.asLiveData2()

    init {
        appControl.data
            .take(1)
            .filter { it == null }
            .onEach { appControl.submit(AppControlScanTask()) }
            .launchInViewModel()
    }

    data class State(
        val appInfos: List<AppControlListRowVH.Item>?,
        val progress: Progress.Data?,
    )

    companion object {
        private val TAG = logTag("AppControl", "List", "VM")
    }
}