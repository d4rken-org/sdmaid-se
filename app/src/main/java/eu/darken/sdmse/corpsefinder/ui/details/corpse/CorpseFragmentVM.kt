package eu.darken.sdmse.corpsefinder.ui.details.corpse

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
import eu.darken.sdmse.corpsefinder.ui.details.corpse.elements.CorpseElementFileVH
import eu.darken.sdmse.corpsefinder.ui.details.corpse.elements.CorpseElementHeaderVH
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class CorpseFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val corpseFinder: CorpseFinder,
    private val taskManager: TaskManager,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val args = CorpseFragmentArgs.fromSavedStateHandle(handle)

    val events = SingleLiveEvent<CorpseEvents>()

    val state = combine(
        corpseFinder.data
            .filterNotNull()
            .map { data ->
                data.corpses.singleOrNull { it.path == args.identifier }
            }
            .filterNotNull(),
        corpseFinder.progress
    ) { corpse, progress ->
        val elements = mutableListOf<CorpseElementsAdapter.Item>()

        CorpseElementHeaderVH.Item(
            corpse = corpse,
            onDeleteAllClicked = {
                events.postValue(CorpseEvents.ConfirmDeletion(it.corpse))
            },
            onExcludeClicked = {
                TODO()
            }
        ).run { elements.add(this) }

        corpse.content.map {
            CorpseElementFileVH.Item(
                corpse = corpse,
                lookup = it,
            )
        }.run { elements.addAll(this) }

        State(elements, progress)
    }.asLiveData2()

    data class State(
        val elements: List<CorpseElementsAdapter.Item>,
        val progress: Progress.Data? = null,
    )

    fun doDelete(corpse: Corpse) = launch {
        log(TAG, INFO) { "doDelete(corpse=$corpse)" }
        val task = CorpseFinderDeleteTask(toDelete = setOf(corpse.path))
        // Removnig the corpse, removes the fragment and also this viewmodel, so we can't post our own result
        events.postValue(CorpseEvents.TaskForParent(task))
    }

    companion object {
        private val TAG = logTag("CorpseFinder", "Details", "Fragment", "VM")
    }
}