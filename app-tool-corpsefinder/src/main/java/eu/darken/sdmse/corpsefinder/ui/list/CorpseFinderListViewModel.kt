package eu.darken.sdmse.corpsefinder.ui.list

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.CorpseIdentifier
import eu.darken.sdmse.corpsefinder.core.hasData
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.corpsefinder.ui.CorpseDetailsRoute
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
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
class CorpseFinderListViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val corpseFinder: CorpseFinder,
    private val taskSubmitter: TaskSubmitter,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    init {
        corpseFinder.state
            .map { it.data }
            .filter { !it.hasData }
            .take(1)
            .onEach { navUp() }
            .launchIn(vmScope)
    }

    val events = SingleEventFlow<Event>()

    val state: StateFlow<State> = combine(
        corpseFinder.state.map { it.data },
        corpseFinder.progress,
    ) { data, progress ->
        val rows = data?.corpses
            ?.sortedByDescending { it.size }
            ?.map { Row(corpse = it) }
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
        navTo(CorpseDetailsRoute(corpsePath = row.identifier))
    }

    fun onDeleteSelected(ids: Set<CorpseIdentifier>) {
        log(TAG, INFO) { "onDeleteSelected(${ids.size})" }
        if (ids.isEmpty()) return
        events.tryEmit(Event.ConfirmDeletion(ids, isSingle = ids.size == 1))
    }

    fun onDeleteConfirmed(ids: Set<CorpseIdentifier>) = launch {
        log(TAG, INFO) { "onDeleteConfirmed(${ids.size})" }
        val snapshot = corpseFinder.state.first().data ?: return@launch
        val validIds = ids.filter { id -> snapshot.corpses.any { it.identifier == id } }.toSet()
        if (validIds.isEmpty()) return@launch

        val task = CorpseFinderDeleteTask(targetCorpses = validIds)
        val result = taskSubmitter.submit(task) as CorpseFinderDeleteTask.Result
        log(TAG) { "onDeleteConfirmed(): Result was $result" }
        when (result) {
            is CorpseFinderDeleteTask.Success -> events.tryEmit(Event.TaskResult(result))
        }
    }

    fun onExcludeSelected(ids: Set<CorpseIdentifier>) = launch {
        log(TAG, INFO) { "onExcludeSelected(${ids.size})" }
        if (ids.isEmpty()) return@launch
        val snapshot = corpseFinder.state.first().data ?: return@launch
        val validIds = ids.filter { id -> snapshot.corpses.any { it.identifier == id } }.toSet()
        if (validIds.isEmpty()) return@launch
        corpseFinder.exclude(validIds)
        events.tryEmit(Event.ExclusionsCreated(validIds.size))
    }

    fun onShowDetailsFromDialog(ids: Set<CorpseIdentifier>) {
        val only = ids.singleOrNull() ?: return
        navTo(CorpseDetailsRoute(corpsePath = only))
    }

    data class State(
        val rows: List<Row>? = null,
        val progress: Progress.Data? = null,
    )

    data class Row(val corpse: Corpse) {
        val identifier: CorpseIdentifier get() = corpse.identifier
    }

    sealed interface Event {
        data class ConfirmDeletion(
            val ids: Set<CorpseIdentifier>,
            val isSingle: Boolean,
        ) : Event

        data class ExclusionsCreated(val count: Int) : Event
        data class TaskResult(val result: CorpseFinderDeleteTask.Result) : Event
    }

    companion object {
        private val TAG = logTag("CorpseFinder", "List", "ViewModel")
    }
}
