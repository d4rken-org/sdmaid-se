package eu.darken.sdmse.systemcleaner.ui.list

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.compose.snackbar.ToolListEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.exclusion.ui.ExclusionsListRoute
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.main.core.taskmanager.getLatestTask
import eu.darken.sdmse.systemcleaner.core.FilterContent
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import eu.darken.sdmse.systemcleaner.core.hasData
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerProcessingTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerScanTask
import eu.darken.sdmse.systemcleaner.ui.FilterContentDetailsRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
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
class SystemCleanerListViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val systemCleaner: SystemCleaner,
    private val taskSubmitter: TaskSubmitter,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    /** Set when the scan this screen started for itself failed, see the entry logic below. */
    private val entryScanFailed = MutableStateFlow(false)

    init {
        // Start an initial scan if SystemCleaner has no data yet. The Dashboard only navigates here
        // after scanning, but the navigation back stack is saved-state backed while the tool's data
        // is plain in-memory state: after process death the restored screen comes back with no data
        // at all and would sit on the loading placeholder forever.
        //
        // Wait out any in-flight task before deciding: performScan nulls the data while it runs, so
        // checking immediately would duplicate an expensive scan. We wait instead of bailing out
        // because an incomplete task is no promise that data is coming — a task that completes
        // without producing data would strand this screen on the loading placeholder for good.
        launch {
            while (true) {
                val idleState = taskSubmitter.state.first { st ->
                    st.tasks.none { it.toolType == SDMTool.Type.SYSTEMCLEANER && !it.isComplete }
                }
                // A scan the user cancelled must not be restarted behind their back, and a failed one
                // would most likely just fail again. Scans are started from the Dashboard, so that's
                // where we send them. No completed entry at all is the process-death case: scan.
                val latest = idleState.getLatestTask(SDMTool.Type.SYSTEMCLEANER)
                if (latest != null && (latest.cancelledAt != null || latest.error != null)) {
                    navUp()
                    return@launch
                }
                if (systemCleaner.state.first().data != null) return@launch
                // Atomic submit: between the idle check above and here another entry point (a second
                // screen, the Dashboard) may have registered its own scan.
                val submitted = try {
                    taskSubmitter.submitIfToolIdle(SystemCleanerScanTask())
                } catch (e: CancellationException) {
                    // Rethrows if the ViewModel itself is going away, so teardown doesn't navigate.
                    currentCoroutineContext().ensureActive()
                    log(TAG, INFO) { "Entry scan was cancelled" }
                    navUp()
                    return@launch
                } catch (e: Exception) {
                    // No navUp here: the error dialog lives in this screen's host, so navigating
                    // away would dispose it before it renders and the user would see nothing. No
                    // rethrow either, that would emit the same error a second time through
                    // launchErrorHandler. The failure flag turns the placeholder into an empty state.
                    log(TAG, WARN) { "Entry scan failed: ${e.asLog()}" }
                    entryScanFailed.value = true
                    errorEvents.emit(e)
                    return@launch
                }
                // Declined: another task for this tool registered in the race window. It may well
                // complete without producing any data, so wait it out and decide again instead of
                // leaving the screen loading forever.
                if (submitted != null) return@launch
            }
        }
        // mapNotNull { it.data } skips the null transitions performScan publishes at the start of a
        // refresh, so navUp fires only on a real drain-to-empty, not during loading. (was BUG-FIXME-9)
        // distinctUntilChanged() drops the repeats: the tool's state combines data with progress, so
        // the same Data re-emits on every progress tick and a cold empty scan would otherwise get
        // past drop(1) on its second identical emission.
        systemCleaner.state
            .mapNotNull { it.data }
            .distinctUntilChanged()
            .map { it.hasData }
            .drop(1)
            .filter { !it }
            .take(1)
            .onEach { navUp() }
            .launchIn(vmScope)
    }

    val events = SingleEventFlow<Event>()

    // Row production excludes progress so high-frequency progress ticks during a scan don't re-sort
    // and re-map the whole filter-content list. Progress is merged in last (below) as a cheap field
    // swap that preserves the rows List instance, letting keyed lazy rows skip recomposition.
    private val rowsState = combine(
        systemCleaner.state.map { it.data }.distinctUntilChanged(),
        entryScanFailed,
    ) { data, scanFailed ->
        val rows = when {
            data != null -> data.filterContents
                .sortedByDescending { it.size }
                .map { Row(content = it) }
            // Null rows mean "loading". Without data and without a scan that could still deliver
            // it, that placeholder would never go away, so show the empty state instead.
            scanFailed -> emptyList<Row>()
            else -> null
        }
        State(rows = rows)
    }

    val state: StateFlow<State> = combine(
        rowsState,
        systemCleaner.progress,
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
        navTo(FilterContentDetailsRoute(filterIdentifier = row.identifier))
    }

    fun onDeleteSelected(ids: Set<FilterIdentifier>) {
        log(TAG, INFO) { "onDeleteSelected(${ids.size})" }
        if (ids.isEmpty()) return
        events.tryEmit(Event.ConfirmDeletion(ids))
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
        val target = ids.firstOrNull() ?: return
        navTo(FilterContentDetailsRoute(filterIdentifier = target))
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
        data class ConfirmDeletion(val ids: Set<FilterIdentifier>) : Event

        data class TaskResult(override val result: SystemCleanerProcessingTask.Result) : Event, ToolListEvent.ShowTaskResult

        data class ExclusionsCreated(override val count: Int) : Event, ToolListEvent.ShowExclusionsCreated
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "List", "ViewModel")
    }
}
