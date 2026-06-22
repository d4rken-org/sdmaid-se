package eu.darken.sdmse.appcleaner.ui.list

import android.annotation.SuppressLint
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerProcessingTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerTask
import eu.darken.sdmse.appcleaner.ui.AppJunkDetailsRoute
import eu.darken.sdmse.common.compose.snackbar.ToolListEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.exclusion.ui.ExclusionsListRoute
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
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

    init {
        // navUp only when a non-null Data drains to empty junks — null is the loading state
        // (set during performScan before results land) and must not trigger navigation.
        appCleaner.state
            .map { it.data }
            .drop(1)
            .filter { it?.junks?.isEmpty() == true }
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
        appCleaner.state.map { it.data },
        searchQuery,
    ) { data, rawQuery ->
        val all = data?.junks?.sortedByDescending { it.size }
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
        if (!upgradeRepo.isPro()) {
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
        if (!upgradeRepo.isPro()) {
            navTo(UpgradeRoute())
            return@launch
        }
        events.tryEmit(Event.ConfirmDeletion(ids))
    }

    fun onDeleteConfirmed(ids: Set<InstallId>) = launch {
        log(TAG, INFO) { "onDeleteConfirmed(${ids.size})" }
        if (!upgradeRepo.isPro()) {
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
