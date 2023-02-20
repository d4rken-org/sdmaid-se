package eu.darken.sdmse.systemcleaner.ui.details

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.systemcleaner.core.FilterContent
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import eu.darken.sdmse.systemcleaner.core.hasData
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerDeleteTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerScanTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerTask
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class FilterContentDetailsFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val systemCleaner: SystemCleaner,
    private val taskManager: TaskManager,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {
    private val args by handle.navArgs<FilterContentDetailsFragmentArgs>()

    init {
        systemCleaner.data
            .filter { !it.hasData }
            .take(1)
            .onEach {
                popNavStack()
            }
            .launchInViewModel()
    }

    val events = SingleLiveEvent<FilterContentDetailsEvents>()

    val state = systemCleaner.data
        .filterNotNull()
        .distinctUntilChangedBy { data ->
            data.filterContents.map { it.filterIdentifier }.toSet()
        }
        .map {
            State(
                items = it.filterContents.toList(),
                target = args.filterIdentifier,
            )
        }
        .asLiveData2()

    data class State(
        val items: List<FilterContent>,
        val target: FilterIdentifier?
    )

    fun forwardTask(task: SystemCleanerTask) = launch {
        log(TAG) { "forwardTask(): $task" }
        val result = taskManager.submit(task) as SystemCleanerTask.Result
        log(TAG) { "forwardTask(): Result $result" }
        when (result) {
            is SystemCleanerDeleteTask.Success -> events.postValue(FilterContentDetailsEvents.TaskResult(result))
            is SystemCleanerScanTask.Success -> {}
        }
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "Details", "Fragment", "VM")
    }
}