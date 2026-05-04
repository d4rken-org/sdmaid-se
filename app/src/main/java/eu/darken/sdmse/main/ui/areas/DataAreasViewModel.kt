package eu.darken.sdmse.main.ui.areas

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@HiltViewModel
class DataAreasViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val dataAreaManager: DataAreaManager,
    private val taskManager: TaskManager,
    private val webpageTool: WebpageTool,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    private val isReloading = MutableStateFlow(false)

    val state: StateFlow<State> = combine(
        dataAreaManager.state,
        taskManager.state,
        isReloading,
    ) { areaState, taskState, reloading ->
        State(
            areas = areaState.areas,
            allowReload = taskState.isIdle && !reloading,
            isReloading = reloading,
        )
    }.safeStateIn(
        initialValue = State(),
        onError = { State() },
    )

    data class State(
        val areas: Set<DataArea>? = null,
        val allowReload: Boolean = false,
        val isReloading: Boolean = false,
    )

    fun reloadDataAreas() = launch {
        if (!isReloading.compareAndSet(expect = false, update = true)) return@launch

        log(TAG) { "reloadDataAreas()" }
        try {
            dataAreaManager.reloadAndAwait()
        } finally {
            isReloading.value = false
        }
    }

    fun openDocumentation() {
        // TODO: replace with a direct link to the data-areas documentation page.
        webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki")
    }

    companion object {
        private val TAG = logTag("DataAreas", "ViewModel")
    }
}
