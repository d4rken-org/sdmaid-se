package eu.darken.sdmse.deduplicator.ui.list

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.DeduplicatorSettings
import eu.darken.sdmse.deduplicator.core.hasData
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorDeleteTask
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@HiltViewModel
class DeduplicatorListViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val deduplicator: Deduplicator,
    private val settings: DeduplicatorSettings,
    private val taskManager: TaskManager,
) : ViewModel3(dispatcherProvider) {

    init {
        deduplicator.state
            .map { it.data }
            .filter { !it.hasData }
            .take(1)
            .onEach { popNavStack() }
            .launchInViewModel()
    }

    val events = SingleLiveEvent<DeduplicatorListEvents>()

    val state = combine(
        deduplicator.state.map { it.data }.filterNotNull(),
        deduplicator.progress
    ) { data, progress ->
        val rows = data.clusters
            .sortedByDescending { it.averageSize }
            .map { cluster ->
                DeduplicatorListGridVH.Item(
                    cluster = cluster,
                    onItemClicked = { delete(setOf(it)) },
                )
            }
        State(rows, progress)
    }.asLiveData2()

    data class State(
        val items: List<DeduplicatorListAdapter.Item>,
        val progress: Progress.Data? = null,
    )

    fun delete(
        items: Collection<DeduplicatorListAdapter.Item>,
        confirmed: Boolean = false,
        deleteAll: Boolean = false,
    ) = launch {
        log(TAG, INFO) { "delete(): ${items.size} confirmed=$confirmed" }
        if (!confirmed) {
            val event = DeduplicatorListEvents.ConfirmDeletion(items, settings.allowDeleteAll.value())
            events.postValue(event)
            return@launch
        }

        val mode: DeduplicatorDeleteTask.TargetMode = DeduplicatorDeleteTask.TargetMode.Clusters(
            targets = items.map { it.cluster.identifier }.toSet(),
            deleteAll = deleteAll,
        )

        val task = DeduplicatorDeleteTask(mode = mode)
        taskManager.submit(task)
    }

    fun exclude(items: Collection<DeduplicatorListAdapter.Item>) = launch {
        log(TAG, INFO) { "exclude(): ${items.size}" }
        val targets = items.map { it.cluster.identifier }.toSet()
        deduplicator.exclude(targets)
        events.postValue(DeduplicatorListEvents.ExclusionsCreated(items.sumOf { it.cluster.count }))
    }

    fun showDetails(item: DeduplicatorListAdapter.Item) = launch {
        log(TAG, INFO) { "showDetails(item=$item)" }
        DeduplicatorListFragmentDirections.actionDeduplicatorListFragmentToDeduplicatorDetailsFragment(
            identifier = item.cluster.identifier
        ).navigate()
    }

    companion object {
        private val TAG = logTag("Deduplicator", "DuplicateGroupList", "ViewModel")
    }
}