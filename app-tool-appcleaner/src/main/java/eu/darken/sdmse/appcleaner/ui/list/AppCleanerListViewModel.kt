package eu.darken.sdmse.appcleaner.ui.list

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.hasData
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerProcessingTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerTask
import eu.darken.sdmse.appcleaner.ui.AppJunkDetailsRoute
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
class AppCleanerListViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val appCleaner: AppCleaner,
    private val taskSubmitter: TaskSubmitter,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    init {
        appCleaner.state
            .map { it.data }
            .filter { !it.hasData }
            .take(1)
            .onEach { navUp() }
            .launchIn(vmScope)
    }

    val events = SingleEventFlow<Event>()

    val state: StateFlow<State> = combine(
        appCleaner.state.map { it.data },
        appCleaner.progress,
    ) { data, progress ->
        val rows = data?.junks
            ?.sortedByDescending { it.size }
            ?.map { Row(junk = it) }
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
        navTo(AppJunkDetailsRoute(identifier = row.identifier))
    }

    fun onDeleteSelected(ids: Set<InstallId>) {
        log(TAG, INFO) { "onDeleteSelected(${ids.size})" }
        if (ids.isEmpty()) return
        events.tryEmit(Event.ConfirmDeletion(ids, isSingle = ids.size == 1))
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
        appCleaner.exclude(validIds)
        events.tryEmit(Event.ExclusionsCreated(validIds.size))
    }

    fun onShowDetailsFromDialog(ids: Set<InstallId>) {
        val only = ids.singleOrNull() ?: return
        navTo(AppJunkDetailsRoute(identifier = only))
    }

    fun onShowExclusions() {
        navTo(ExclusionsListRoute)
    }

    data class State(
        val rows: List<Row>? = null,
        val progress: Progress.Data? = null,
    )

    data class Row(val junk: AppJunk) {
        val identifier: InstallId get() = junk.identifier
    }

    sealed interface Event {
        data class ConfirmDeletion(
            val ids: Set<InstallId>,
            val isSingle: Boolean,
        ) : Event

        data class TaskResult(val result: AppCleanerTask.Result) : Event

        data class ExclusionsCreated(val count: Int) : Event
    }

    companion object {
        private val TAG = logTag("AppCleaner", "List", "ViewModel")
    }
}
