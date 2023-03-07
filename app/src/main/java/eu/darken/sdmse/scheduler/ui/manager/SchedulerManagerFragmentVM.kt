package eu.darken.sdmse.scheduler.ui.manager

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.ui.dashboard.items.*
import eu.darken.sdmse.scheduler.core.SchedulerManager
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject

@HiltViewModel
class SchedulerManagerFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val taskManager: TaskManager,
    private val schedulerManager: SchedulerManager,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    val items = combine(
        schedulerManager.state,
        taskManager.state
    ) { schedulerState, taskState ->
        val items = schedulerState.schedules.map {
            SchedulerRowVH.Item(
                schedule = it,
                onItemClick = {
                    SchedulerManagerFragmentDirections.actionSchedulerManagerFragmentToScheduleItemDialog(
                        scheduleId = it.id
                    ).navigate()
                }
            )
        }
        State(
            listItems = items,
        )
    }
        .onStart { emit(State()) }
        .asLiveData2()

    fun createNew() {
        log(TAG) { "createNew()" }
        SchedulerManagerFragmentDirections.actionSchedulerManagerFragmentToScheduleItemDialog(
            scheduleId = UUID.randomUUID().toString()
        ).navigate()
    }

    data class State(
        val listItems: List<SchedulerRowVH.Item>? = null,
    )

    companion object {
        private val TAG = logTag("DataAreas", "Fragment", "VM")
    }
}