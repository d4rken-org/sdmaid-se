package eu.darken.sdmse.systemcleaner.ui.list

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.hasData
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerDeleteTask
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@HiltViewModel
class SystemCleanerListViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val systemCleaner: SystemCleaner,
    private val taskManager: TaskManager,
) : ViewModel3(dispatcherProvider) {

    init {
        systemCleaner.state
            .map { it.data }
            .filter { !it.hasData }
            .take(1)
            .onEach { popNavStack() }
            .launchInViewModel()
    }

    val events = SingleLiveEvent<SystemCleanerListEvents>()

    val state = combine(
        systemCleaner.state.map { it.data }.filterNotNull(),
        systemCleaner.progress,
    ) { data, progress ->
        val items = data.filterContents.map { content ->
            SystemCleanerListRowVH.Item(
                content = content,
                onItemClicked = { events.postValue(SystemCleanerListEvents.ConfirmDeletion(listOf(it))) },
                onDetailsClicked = { showDetails(it) }
            )
        }
        State(
            items = items,
            progress = progress,
        )
    }.asLiveData2()

    fun delete(items: Collection<SystemCleanerListAdapter.Item>, confirmed: Boolean = false) = launch {
        log(TAG, INFO) { "delete(): ${items.size}" }
        if (!confirmed) {
            events.postValue(SystemCleanerListEvents.ConfirmDeletion(items))
            return@launch
        }
        val task = SystemCleanerDeleteTask(targetFilters = items.map { it.content.identifier }.toSet())
        val result = taskManager.submit(task) as SystemCleanerDeleteTask.Result
        log(TAG) { "doDelete(): Result was $result" }
        when (result) {
            is SystemCleanerDeleteTask.Success -> events.postValue(SystemCleanerListEvents.TaskResult(result))
        }
    }

    fun showDetails(item: SystemCleanerListAdapter.Item) = launch {
        log(TAG, INFO) { "showDetails(filterContent=${item.content.identifier})" }
        SystemCleanerListFragmentDirections.actionSystemCleanerListFragmentToSystemCleanerDetailsFragment(
            filterIdentifier = item.content.identifier
        ).navigate()
    }


    data class State(
        val items: List<SystemCleanerListAdapter.Item>,
        val progress: Progress.Data?,
    )

    companion object {
        private val TAG = logTag("SystemCleaner", "List", "ViewModel")
    }
}