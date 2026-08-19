package eu.darken.sdmse.corpsefinder.ui.list

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.compose.snackbar.ToolListEvent
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
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderScanTask
import eu.darken.sdmse.corpsefinder.ui.CorpseDetailsRoute
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
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
        // Start an initial scan if CorpseFinder has no data yet. The Dashboard only navigates here
        // after scanning, but the launcher shortcut opens this screen cold — without this it would
        // sit on the loading placeholder forever.
        //
        // Wait out any in-flight task before deciding: performScan nulls the data while it runs, so
        // checking immediately would duplicate an expensive scan. We wait instead of bailing out
        // because an incomplete task is no promise that data is coming — the uninstall watcher never
        // assigns any, and a cancelled or failed scan completes without assigning either. Bailing
        // out on those would strand this screen on the loading placeholder for good.
        launch {
            taskSubmitter.state.first { st ->
                st.tasks.none { it.toolType == SDMTool.Type.CORPSEFINDER && !it.isComplete }
            }
            val initState = corpseFinder.state.first()
            if (initState.data != null) return@launch
            taskSubmitter.submit(CorpseFinderScanTask())
        }
        // navUp only on a real drain-to-empty. mapNotNull skips the null loading state performScan
        // publishes while running, so drop(1) consumes the first REAL result, and the dedupe drops
        // the repeats the tool's data/progress combine produces on every progress tick.
        //
        // The dedupe key is the corpse collection, not the whole Data: Data also carries lastResult,
        // which a cold scan writes twice with different values, so two Data with identical corpses
        // compare unequal and an empty cold scan would slip past drop(1) and navigate away.
        corpseFinder.state
            .mapNotNull { it.data?.corpses }
            .distinctUntilChanged()
            .drop(1)
            .filter { it.isEmpty() }
            .take(1)
            .onEach { navUp() }
            .launchIn(vmScope)
    }

    val events = SingleEventFlow<Event>()

    // Row production excludes progress so high-frequency progress ticks during a scan don't re-sort
    // and re-map the whole corpse list. Progress is merged in last (below) as a cheap field swap that
    // preserves the rows List instance, letting keyed lazy rows skip recomposition.
    private val rowsState = corpseFinder.state
        .map { it.data }
        .distinctUntilChanged()
        .map { data ->
            val rows = data?.corpses
                ?.sortedByDescending { it.size }
                ?.map { Row(corpse = it) }
            State(rows = rows)
        }

    val state: StateFlow<State> = combine(
        rowsState,
        corpseFinder.progress,
    ) { base, progress ->
        base.copy(progress = progress)
    }.safeStateIn(
        initialValue = State(),
        onError = { State() },
    )

    fun onRowClick(row: Row) {
        log(TAG, INFO) { "onRowClick(${row.identifier})" }
        events.tryEmit(Event.ConfirmDeletion(setOf(row.identifier)))
    }

    fun onDetailsClick(row: Row) {
        log(TAG, INFO) { "onDetailsClick(${row.identifier})" }
        navTo(CorpseDetailsRoute(corpsePath = row.identifier))
    }

    fun onDeleteSelected(ids: Set<CorpseIdentifier>) {
        log(TAG, INFO) { "onDeleteSelected(${ids.size})" }
        if (ids.isEmpty()) return
        events.tryEmit(Event.ConfirmDeletion(ids))
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
        val undo = corpseFinder.exclude(validIds)
        events.tryEmit(Event.ExclusionsCreated(undo.exclusionIds.size))
    }

    fun onShowDetailsFromDialog(ids: Set<CorpseIdentifier>) {
        val target = ids.firstOrNull() ?: return
        navTo(CorpseDetailsRoute(corpsePath = target))
    }

    data class State(
        val rows: List<Row>? = null,
        val progress: Progress.Data? = null,
    )

    data class Row(val corpse: Corpse) {
        val identifier: CorpseIdentifier get() = corpse.identifier
    }

    sealed interface Event {
        data class ConfirmDeletion(val ids: Set<CorpseIdentifier>) : Event

        data class ExclusionsCreated(override val count: Int) : Event, ToolListEvent.ShowExclusionsCreated
        data class TaskResult(override val result: CorpseFinderDeleteTask.Result) : Event, ToolListEvent.ShowTaskResult
    }

    companion object {
        private val TAG = logTag("CorpseFinder", "List", "ViewModel")
    }
}
