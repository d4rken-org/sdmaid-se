package eu.darken.sdmse.systemcleaner.ui.list

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.exclusion.ui.ExclusionsListRoute
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.systemcleaner.core.FilterContent
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import eu.darken.sdmse.systemcleaner.core.hasData
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerProcessingTask
import eu.darken.sdmse.systemcleaner.ui.FilterContentDetailsRoute
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@HiltViewModel
class SystemCleanerListViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val systemCleaner: SystemCleaner,
    private val taskSubmitter: TaskSubmitter,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    init {
        systemCleaner.state
            .map { it.data }
            .filter { !it.hasData }
            .take(1)
            .onEach { navUp() }
            .launchIn(vmScope)
    }

    val events = SingleEventFlow<Event>()

    val state: StateFlow<State> = combine(
        systemCleaner.state.map { it.data },
        systemCleaner.progress,
    ) { data, progress ->
        val rows = data?.filterContents
            ?.sortedByDescending { it.size }
            ?.map { Row(content = it) }
        State(rows = rows, progress = progress)
    }.safeStateIn(
        initialValue = State(),
        onError = { State() },
    )

    fun onRowClick(row: Row) {
        log(TAG, INFO) { "onRowClick(${row.identifier})" }
        events.tryEmit(Event.ConfirmDeletion(setOf(row.identifier), isSingle = true))
    }

    fun onDetailsClick(row: Row) {
        log(TAG, INFO) { "onDetailsClick(${row.identifier})" }
        navTo(FilterContentDetailsRoute(filterIdentifier = row.identifier))
    }

    fun onDeleteSelected(ids: Set<FilterIdentifier>) {
        log(TAG, INFO) { "onDeleteSelected(${ids.size})" }
        if (ids.isEmpty()) return
        events.tryEmit(Event.ConfirmDeletion(ids, isSingle = ids.size == 1))
    }

    fun onDeleteConfirmed(ids: Set<FilterIdentifier>) = launch {
        log(TAG, INFO) { "onDeleteConfirmed(${ids.size})" }
        val data = systemCleaner.state.first().data ?: return@launch
        val validIds = ids.filter { id -> data.filterContents.any { it.identifier == id } }.toSet()
        if (validIds.isEmpty()) return@launch

        val task = SystemCleanerProcessingTask(targetFilters = validIds)
        val result = taskSubmitter.submit(task) as SystemCleanerProcessingTask.Result
        log(TAG) { "onDeleteConfirmed(): Result was $result" }
        when (result) {
            is SystemCleanerProcessingTask.Success -> events.tryEmit(Event.TaskResult(result))
        }
    }

    fun onShowDetailsFromDialog(ids: Set<FilterIdentifier>) {
        val only = ids.singleOrNull() ?: return
        navTo(FilterContentDetailsRoute(filterIdentifier = only))
    }

    fun onExcludeSelected(ids: Set<FilterIdentifier>) = launch {
        log(TAG, INFO) { "onExcludeSelected(${ids.size})" }
        if (ids.isEmpty()) return@launch
        val data = systemCleaner.state.first().data ?: return@launch
        var totalExclusions = 0
        ids.forEach { id ->
            val fc = data.filterContents.firstOrNull { it.identifier == id } ?: return@forEach
            val paths = fc.items.map { it.path }.toSet()
            if (paths.isEmpty()) return@forEach
            val undo = systemCleaner.exclude(id, paths)
            totalExclusions += undo.exclusionIds.size
        }
        if (totalExclusions == 0) return@launch
        events.tryEmit(Event.ExclusionsCreated(totalExclusions))
    }

    fun onShowExclusions() {
        navTo(ExclusionsListRoute)
    }

    data class State(
        val rows: List<Row>? = null,
        val progress: Progress.Data? = null,
    )

    data class Row(val content: FilterContent) {
        val identifier: FilterIdentifier get() = content.identifier
    }

    sealed interface Event {
        data class ConfirmDeletion(
            val ids: Set<FilterIdentifier>,
            val isSingle: Boolean,
        ) : Event

        data class TaskResult(val result: SystemCleanerProcessingTask.Result) : Event

        data class ExclusionsCreated(val count: Int) : Event
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "List", "ViewModel")
    }
}
