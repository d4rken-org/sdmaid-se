package eu.darken.sdmse.corpsefinder.ui.list

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.hasData
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@HiltViewModel
class CorpseFinderListViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val corpseFinder: CorpseFinder,
    private val taskManager: TaskManager,
) : ViewModel3(dispatcherProvider) {

    init {
        corpseFinder.state
            .map { it.data }
            .filter { !it.hasData }
            .take(1)
            .onEach { popNavStack() }
            .launchInViewModel()
    }

    val events = SingleLiveEvent<CorpseFinderListEvents>()

    val state = combine(
        corpseFinder.state.map { it.data }.filterNotNull(),
        corpseFinder.progress
    ) { data, progress ->
        val rows = data.corpses.map { corpse ->
            CorpseFinderListRowVH.Item(
                corpse = corpse,
                onItemClicked = { delete(setOf(it)) },
                onDetailsClicked = { showDetails(it) }
            )
        }
        State(rows, progress)
    }.asLiveData2()

    data class State(
        val items: List<CorpseFinderListRowVH.Item>,
        val progress: Progress.Data? = null,
    )

    fun delete(items: Collection<CorpseFinderListAdapter.Item>, confirmed: Boolean = false) = launch {
        log(TAG, INFO) { "delete(): ${items.size} confirmed=$confirmed" }
        if (!confirmed) {
            events.postValue(CorpseFinderListEvents.ConfirmDeletion(items))
            return@launch
        }

        val targets = items.mapNotNull {
            when (it) {
                is CorpseFinderListRowVH.Item -> it.corpse.identifier
                else -> null
            }
        }.toSet()

        val task = CorpseFinderDeleteTask(targetCorpses = targets)
        val result = taskManager.submit(task) as CorpseFinderDeleteTask.Result

        log(TAG) { "delete(): Result was $result" }
        when (result) {
            is CorpseFinderDeleteTask.Success -> events.postValue(CorpseFinderListEvents.TaskResult(result))
        }
    }

    fun exclude(items: Collection<CorpseFinderListAdapter.Item>) = launch {
        log(TAG, INFO) { "exclude(): ${items.size}" }
        val targets = items.mapNotNull {
            when (it) {
                is CorpseFinderListRowVH.Item -> it.corpse.identifier
                else -> null
            }
        }.toSet()
        corpseFinder.exclude(targets)
        events.postValue(CorpseFinderListEvents.ExclusionsCreated(items.size))
    }

    fun showDetails(item: CorpseFinderListAdapter.Item) = launch {
        log(TAG, INFO) { "showDetails(item=$item)" }
        CorpseFinderListFragmentDirections.actionCorpseFinderListFragmentToCorpseFinderDetailsFragment(
            corpsePath = (item as CorpseFinderListRowVH.Item).corpse.identifier
        ).navigate()
    }

    companion object {
        private val TAG = logTag("CorpseFinder", "List", "ViewModel")
    }
}