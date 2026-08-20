package eu.darken.sdmse.appcleaner.ui.list

import android.annotation.SuppressLint
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerProcessingTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerTask
import eu.darken.sdmse.appcleaner.ui.AppJunkDetailsRoute
import eu.darken.sdmse.common.compose.snackbar.ToolListEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isProForUi
import eu.darken.sdmse.exclusion.ui.ExclusionsListRoute
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.main.core.taskmanager.getLatestTask
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

@SuppressLint("StaticFieldLeak")
@HiltViewModel
class AppCleanerListViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val appCleaner: AppCleaner,
    private val taskSubmitter: TaskSubmitter,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    /** Set when the scan this screen started for itself failed, see the entry logic below. */
    private val entryScanFailed = MutableStateFlow(false)

    init {
        // Start an initial scan if AppCleaner has no data yet. The Dashboard only navigates here
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
                    st.tasks.none { it.toolType == SDMTool.Type.APPCLEANER && !it.isComplete }
                }
                // A scan the user cancelled must not be restarted behind their back, and a failed one
                // would most likely just fail again. Scans are started from the Dashboard, so that's
                // where we send them. No completed entry at all is the process-death case: scan.
                val latest = idleState.getLatestTask(SDMTool.Type.APPCLEANER)
                if (latest != null && (latest.cancelledAt != null || latest.error != null)) {
                    navUp()
                    return@launch
                }
                if (appCleaner.state.first().data != null) return@launch
                // Atomic submit: between the idle check above and here another entry point (a second
                // screen, the Dashboard) may have registered its own scan.
                val submitted = try {
                    taskSubmitter.submitIfToolIdle(AppCleanerScanTask())
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
                    // A missing inventory setup (IncompleteSetupException) lands here.
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
        // navUp only on a real drain-to-empty. mapNotNull skips the null loading state performScan
        // publishes while running, so drop(1) consumes the first REAL result — without it a cold
        // scan that finds nothing would navUp instead of showing the empty list. The dedupe drops
        // the repeats the tool's data/progress combine produces on every progress tick, which would
        // otherwise get an empty cold scan past drop(1) on its second identical emission.
        appCleaner.state
            .mapNotNull { it.data }
            .distinctUntilChanged()
            .drop(1)
            .filter { it.junks.isEmpty() }
            .take(1)
            .onEach { navUp() }
            .launchIn(vmScope)
    }

    val events = SingleEventFlow<Event>()

    private val searchQuery = MutableStateFlow("")

    // Row production excludes progress so high-frequency progress ticks during a scan don't re-sort
    // and re-map the whole junk list. Progress is merged in last (below) as a cheap field swap that
    // preserves the rows List instance, letting keyed lazy rows skip recomposition.
    private val rowsState = combine(
        appCleaner.state.map { it.data }.distinctUntilChanged(),
        searchQuery,
        entryScanFailed,
    ) { data, rawQuery, scanFailed ->
        val all = when {
            data != null -> data.junks.sortedByDescending { it.size }
            // Null rows mean "loading". Without data and without a scan that could still deliver
            // it, that placeholder would never go away, so show the empty state instead.
            scanFailed -> emptyList<AppJunk>()
            else -> null
        }
        val normalized = AppCleanerSearchMatcher.normalizeQuery(rawQuery)
        val filtered = if (normalized.isEmpty()) {
            all
        } else {
            all?.filter { junk ->
                AppCleanerSearchMatcher.matches(
                    label = junk.label.get(context),
                    packageName = junk.pkg.packageName,
                    normalizedQuery = normalized,
                )
            }
        }
        State(
            rows = filtered?.map { Row(junk = it) },
            searchQuery = rawQuery,
            isSearchFilterActive = normalized.isNotEmpty(),
            totalCount = all?.size ?: 0,
        )
    }

    val state: StateFlow<State> = combine(
        rowsState,
        appCleaner.progress,
    ) { base, progress ->
        base.copy(progress = progress)
    }.safeStateIn(
        initialValue = State(),
        onError = { State() },
    )

    fun onSearchQueryChanged(query: String) {
        log(TAG) { "onSearchQueryChanged($query)" }
        searchQuery.value = query
    }

    fun onRowClick(row: Row) = launch {
        log(TAG, INFO) { "onRowClick(${row.identifier})" }
        if (!upgradeRepo.isProForUi()) {
            navTo(UpgradeRoute())
            return@launch
        }
        events.tryEmit(Event.ConfirmDeletion(setOf(row.identifier)))
    }

    fun onDetailsClick(row: Row) {
        log(TAG, INFO) { "onDetailsClick(${row.identifier})" }
        navTo(AppJunkDetailsRoute(identifier = row.identifier))
    }

    fun onDeleteSelected(ids: Set<InstallId>) = launch {
        log(TAG, INFO) { "onDeleteSelected(${ids.size})" }
        if (ids.isEmpty()) return@launch
        if (!upgradeRepo.isProForUi()) {
            navTo(UpgradeRoute())
            return@launch
        }
        events.tryEmit(Event.ConfirmDeletion(ids))
    }

    fun onDeleteConfirmed(ids: Set<InstallId>) = launch {
        log(TAG, INFO) { "onDeleteConfirmed(${ids.size})" }
        if (!upgradeRepo.isProForUi()) {
            navTo(UpgradeRoute())
            return@launch
        }
        val data = appCleaner.state.first().data ?: return@launch
        val validIds = ids.filter { id -> data.junks.any { it.identifier == id } }.toSet()
        if (validIds.isEmpty()) return@launch

        val task = AppCleanerProcessingTask(targetPkgs = validIds)
        val result = taskSubmitter.submit(task) as AppCleanerTask.Result
        log(TAG) { "onDeleteConfirmed(): Result was $result" }
        events.tryEmit(Event.TaskResult(result))
    }

    fun onExcludeSelected(ids: Set<InstallId>) = launch {
        log(TAG, INFO) { "onExcludeSelected(${ids.size})" }
        val data = appCleaner.state.first().data ?: return@launch
        val validIds = ids.filter { id -> data.junks.any { it.identifier == id } }.toSet()
        if (validIds.isEmpty()) return@launch
        val undo = appCleaner.exclude(validIds)
        events.tryEmit(Event.ExclusionsCreated(undo.exclusionIds.size))
    }

    fun onShowDetailsFromDialog(ids: Set<InstallId>) {
        val target = ids.firstOrNull() ?: return
        navTo(AppJunkDetailsRoute(identifier = target))
    }

    fun onShowExclusions() {
        navTo(ExclusionsListRoute)
    }

    data class State(
        val rows: List<Row>? = null,
        val progress: Progress.Data? = null,
        val searchQuery: String = "",
        val isSearchFilterActive: Boolean = false,
        val totalCount: Int = 0,
    )

    data class Row(val junk: AppJunk) {
        val identifier: InstallId get() = junk.identifier
    }

    sealed interface Event {
        data class ConfirmDeletion(val ids: Set<InstallId>) : Event

        data class TaskResult(override val result: AppCleanerTask.Result) : Event, ToolListEvent.ShowTaskResult

        data class ExclusionsCreated(override val count: Int) : Event, ToolListEvent.ShowExclusionsCreated
    }

    companion object {
        private val TAG = logTag("AppCleaner", "List", "ViewModel")
    }
}
