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
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class CorpseListFragmentVM @Inject constructor(
    private val handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val corpseFinder: CorpseFinder,
    private val taskManager: TaskManager,
) : ViewModel3(dispatcherProvider) {

    val events = SingleLiveEvent<CorpseListEvents>()

    val state = combine(
        corpseFinder.data.filterNotNull(),
        corpseFinder.progress
    ) { data, progress ->
        val rows = data.corpses.map { corpse ->
            CorpseRowVH.Item(
                corpse = corpse,
                onItemClicked = {
                    events.postValue(CorpseListEvents.ConfirmDeletion(it))
                },
                onDetailsClicked = { showDetails(it) }
            )
        }
        State(rows, progress)
    }.asLiveData2()

    data class State(
        val items: List<CorpseRowVH.Item>,
        val progress: Progress.Data? = null,
    )

    init {
        corpseFinder.data
            .filter { it == null }
            .take(1)
            .onEach { popNavStack() }
            .launchInViewModel()
    }

    fun doDelete(corpse: Corpse) = launch {
        log(TAG, INFO) { "doDelete(corpse=$corpse)" }
        val task = CorpseFinderDeleteTask(toDelete = setOf(corpse.path))
        taskManager.submit(task)
    }

    fun showDetails(corpse: Corpse) = launch {
        log(TAG, INFO) { "showDetails(corpse=$corpse)" }
        CorpseListFragmentDirections.actionCorpseFinderListFragmentToCorpseFinderDetailsFragment(
            corpsePath = corpse.path
        ).navigate()
    }

    companion object {
        private val TAG = logTag("CorpseFinder", "List", "VM")
    }
}