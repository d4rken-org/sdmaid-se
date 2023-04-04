package eu.darken.sdmse.corpsefinder.ui.details.corpse

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.corpsefinder.ui.details.corpse.elements.CorpseElementFileVH
import eu.darken.sdmse.corpsefinder.ui.details.corpse.elements.CorpseElementHeaderVH
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class CorpseFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val corpseFinder: CorpseFinder,
    private val taskManager: TaskManager,
    private val exclusionManager: ExclusionManager,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val args = CorpseFragmentArgs.fromSavedStateHandle(handle)

    val events = SingleLiveEvent<CorpseEvents>()

    val state = combine(
        corpseFinder.data
            .filterNotNull()
            .map { data -> data.corpses.singleOrNull { it.path == args.identifier } }
            .filterNotNull(),
        corpseFinder.progress
    ) { corpse, progress ->
        val elements = mutableListOf<CorpseElementsAdapter.Item>()

        CorpseElementHeaderVH.Item(
            corpse = corpse,
            onDeleteAllClicked = { events.postValue(CorpseEvents.ConfirmDeletion(it.corpse)) },
            onExcludeClicked = {
                launch { corpseFinder.exclude(corpse) }
            }
        ).run { elements.add(this) }

        corpse.content.map { lookup ->
            CorpseElementFileVH.Item(
                corpse = corpse,
                lookup = lookup,
                onItemClick = { events.postValue(CorpseEvents.ConfirmDeletion(it.corpse, it.lookup.lookedUp)) },
            )
        }.run { elements.addAll(this) }

        State(elements, progress)
    }.asLiveData2()

    data class State(
        val elements: List<CorpseElementsAdapter.Item>,
        val progress: Progress.Data? = null,
    )

    fun doDelete(corpse: Corpse, content: APath? = null) = launch {
        log(TAG, INFO) { "doDelete(corpse=$corpse)" }
        val task = CorpseFinderDeleteTask(
            targetCorpses = setOf(corpse.path),
            targetContent = content?.let { setOf(it) }
        )
        // Removnig the corpse, removes the fragment and also this viewmodel, so we can't post our own result
        events.postValue(CorpseEvents.TaskForParent(task))
    }

    fun doExclude(corpse: Corpse, path: APath) = launch {
        log(TAG, INFO) { "doExclude(): $path" }
        corpseFinder.exclude(corpse, path)
    }

    companion object {
        private val TAG = logTag("CorpseFinder", "Details", "Fragment", "VM")
    }
}