package eu.darken.sdmse.main.ui.areas

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.ui.dashboard.items.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class DataAreasViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val dataAreaManager: DataAreaManager,
    private val taskManager: TaskManager,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    val items = combine(
        dataAreaManager.state,
        taskManager.state
    ) { araeState, taskState ->
        val items = araeState.areas.map {
            DataAreaRowVH.Item(
                area = it,
            )
        }
        State(
            areas = items,
            allowReload = taskState.isIdle
        )
    }
        .onStart { emit(State()) }
        .asLiveData2()

    data class State(
        val areas: List<DataAreaRowVH.Item>? = null,
        val allowReload: Boolean = false
    )

    fun reloadDataAreas() = launch {
        log(TAG) { "reloadDataAreas()" }
        dataAreaManager.reload()
    }

    companion object {
        private val TAG = logTag("DataAreas", "ViewModel")
    }
}