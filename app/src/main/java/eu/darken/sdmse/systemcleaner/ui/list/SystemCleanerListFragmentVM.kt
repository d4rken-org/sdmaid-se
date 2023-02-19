package eu.darken.sdmse.systemcleaner.ui.list

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.systemcleaner.core.FilterContent
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerDeleteTask
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class SystemCleanerListFragmentVM @Inject constructor(
    private val handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val systemCleaner: SystemCleaner,
    private val taskManager: TaskManager,
) : ViewModel3(dispatcherProvider) {

    val events = SingleLiveEvent<SystemCleanerListEvents>()

    val items = systemCleaner.data
        .filterNotNull()
        .map { data ->
            data.filterContents.map { content ->
                SystemCleanerListRowVH.Item(
                    content = content,
                    onItemClicked = {
                        events.postValue(SystemCleanerListEvents.ConfirmDeletion(it))
                    },
                    onDetailsClicked = { showDetails(it) }
                )
            }
        }
        .asLiveData2()

    init {
        systemCleaner.data
            .filter { it == null }
            .take(1)
            .onEach { popNavStack() }
            .launchInViewModel()
    }

    fun doDelete(filterContent: FilterContent) = launch {
        log(TAG, INFO) { "doDelete(): $filterContent" }
        val task = SystemCleanerDeleteTask(toDelete = setOf(filterContent.filterIdentifier))
        val result = taskManager.submit(task) as SystemCleanerDeleteTask.Result
        log(TAG) { "doDelete(): Result was $result" }
        when (result) {
            is SystemCleanerDeleteTask.Success -> events.postValue(SystemCleanerListEvents.TaskResult(result))
        }
    }

    fun showDetails(filterContent: FilterContent) = launch {
        log(TAG, INFO) { "showDetails(filterContent=$filterContent)" }
        SystemCleanerListFragmentDirections.actionSystemCleanerListFragmentToSystemCleanerDetailsFragment(
            filterIdentifier = filterContent.filterIdentifier
        ).navigate()
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "List", "VM")
    }
}