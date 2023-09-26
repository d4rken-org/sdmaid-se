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
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.corpsefinder.ui.details.corpse.elements.CorpseElementFileVH
import eu.darken.sdmse.corpsefinder.ui.details.corpse.elements.CorpseElementHeaderVH
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class CorpseViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val corpseFinder: CorpseFinder,
    private val taskManager: TaskManager,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val args = CorpseFragmentArgs.fromSavedStateHandle(handle)

    val events = SingleLiveEvent<CorpseEvents>()

    private val corpseData = corpseFinder.state
        .map { it.data }
        .filterNotNull()
        .map { data -> data.corpses.singleOrNull { it.identifier == args.identifier } }
        .filterNotNull()

    val state = combine(
        corpseData,
        corpseFinder.progress
    ) { corpse, progress ->
        val elements = mutableListOf<CorpseElementsAdapter.Item>()

        CorpseElementHeaderVH.Item(
            corpse = corpse,
            onDeleteAllClicked = { delete(setOf(it)) },
            onExcludeClicked = { exclude(setOf(it)) },
        ).run { elements.add(this) }

        corpse.content
            .sortedByDescending { it.size }
            .map { lookup ->
                CorpseElementFileVH.Item(
                    corpse = corpse,
                    lookup = lookup,
                    onItemClick = { delete(setOf(it)) },
                )
            }
            .run { elements.addAll(this) }

        State(elements, progress)
    }.asLiveData2()

    data class State(
        val elements: List<CorpseElementsAdapter.Item>,
        val progress: Progress.Data? = null,
    )

    fun delete(items: Collection<CorpseElementsAdapter.Item>, confirmed: Boolean = false) = launch {
        log(TAG, INFO) { "delete(items=$items)" }
        if (!confirmed) {
            events.postValue(CorpseEvents.ConfirmDeletion(items))
            return@launch
        }

        val targets = items.mapNotNull {
            when (it) {
                is CorpseElementFileVH.Item -> it.lookup.lookedUp
                else -> null
            }
        }.toSet()

        val task = CorpseFinderDeleteTask(
            targetCorpses = setOf(corpseData.first().identifier),
            targetContent = targets.takeIf { it.isNotEmpty() }
        )

        taskManager.submit(task)
    }

    fun exclude(items: Collection<CorpseElementsAdapter.Item>) = launch {
        log(TAG, INFO) { "exclude(): $items" }
        val corpse = corpseData.first()

        if (items.singleOrNull() is CorpseElementHeaderVH.Item) {
            corpseFinder.exclude(setOf(corpse.identifier))
        }
    }

    companion object {
        private val TAG = logTag("CorpseFinder", "Details", "ViewModel")
    }
}