package eu.darken.sdmse.deduplicator.ui.list

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.hasData
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorDeleteTask
import eu.darken.sdmse.deduplicator.ui.list.types.HashGroupRowVH
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@HiltViewModel
class DuplicateGroupViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val deduplicator: Deduplicator,
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

    val events = SingleLiveEvent<DuplicateGroupListEvents>()

    val state = combine(
        deduplicator.state.map { it.data }.filterNotNull(),
        deduplicator.progress
    ) { data, progress ->
        val rows = data.clusters
            .sortedByDescending { it.averageSize }
            .map { cluster ->
                HashGroupRowVH.Item(
                    cluster = cluster,
                    onItemClicked = { delete(setOf(it)) },
                )
            }
        State(rows, progress)
    }.asLiveData2()

    data class State(
        val items: List<DuplicateGroupListAdapter.Item>,
        val progress: Progress.Data? = null,
    )

    fun delete(items: Collection<DuplicateGroupListAdapter.Item>, confirmed: Boolean = false) = launch {
        log(TAG, INFO) { "delete(): ${items.size} confirmed=$confirmed" }
        if (!confirmed) {
            events.postValue(DuplicateGroupListEvents.ConfirmDeletion(items))
            return@launch
        }

        val targets = items.mapNotNull {
            when (it) {
                is DuplicateGroupListAdapter.Item -> it.cluster.identifier
                else -> null
            }
        }.toSet()

        val task = DeduplicatorDeleteTask(targetGroups = targets)
        val result = taskManager.submit(task) as DeduplicatorDeleteTask.Result

        log(TAG) { "delete(): Result was $result" }
        when (result) {
            is DeduplicatorDeleteTask.Success -> events.postValue(DuplicateGroupListEvents.TaskResult(result))
        }
    }

    fun exclude(items: Collection<DuplicateGroupListAdapter.Item>) = launch {
        log(TAG, INFO) { "exclude(): ${items.size}" }
        val targets = items.map { it.cluster.identifier }.toSet()
        deduplicator.exclude(targets)
        events.postValue(DuplicateGroupListEvents.ExclusionsCreated(items.size))
    }

    fun showDetails(item: DuplicateGroupListAdapter.Item) = launch {
        log(TAG, INFO) { "showDetails(item=$item)" }
//        CorpseListFragmentDirections.actionCorpseFinderListFragmentToCorpseFinderDetailsFragment(
//            corpsePath = (item as CorpseRowVH.Item).corpse.identifier
//        ).navigate()
    }

    companion object {
        private val TAG = logTag("Deduplicator", "DuplicateGroupList", "ViewModel")
    }
}