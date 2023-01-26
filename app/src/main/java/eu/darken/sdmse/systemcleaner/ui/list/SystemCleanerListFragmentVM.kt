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
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class SystemCleanerListFragmentVM @Inject constructor(
    private val handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val systemCleaner: SystemCleaner,
    private val taskManager: TaskManager,
) : ViewModel3(dispatcherProvider) {

    val events = SingleLiveEvent<FilterListEvents>()

    val items = systemCleaner.data
        .filterNotNull()
        .map { data ->
            data.filterContents.map { content ->
                FilterRowVH.Item(
                    content = content,
                    onItemClicked = {
                        events.postValue(FilterListEvents.ConfirmDeletion(it))
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
        log(TAG, INFO) { "doDelete(filterContent=$filterContent)" }
//        val task = CorpseFinderDeleteTask(toDelete = setOf(corpse.path))
//        taskManager.submit(task)
    }

    fun showDetails(filterContent: FilterContent) = launch {
        log(TAG, INFO) { "showDetails(filterContent=$filterContent)" }
        FilterListFragmentDirections.actionSystemCleanerListFragmentToSystemCleanerDetailsFragment(
            filterIdentifier = filterContent.filterIdentifier
        ).navigate()
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "List", "VM")
    }
}