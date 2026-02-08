package eu.darken.sdmse.corpsefinder.ui.details

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.mutableState
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.uix.resolveTarget
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.CorpseIdentifier
import eu.darken.sdmse.corpsefinder.core.hasData
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderTask
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.core.taskmanager.getLatestTask
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class CorpseDetailsViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    corpseFinder: CorpseFinder,
    private val taskManager: TaskManager,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {
    private val args by handle.navArgs<CorpseDetailsFragmentArgs>()
    private var currentTarget: CorpseIdentifier? by handle.mutableState("target")
    private var lastPosition: Int? by handle.mutableState("position")

    init {
        corpseFinder.state
            .map { it.data }
            .filter { !it.hasData }
            .take(1)
            .onEach { popNavStack() }
            .launchInViewModel()

        val start = Instant.now()
        val handledResults = mutableSetOf<String>()
        taskManager.state
            .map { it.getLatestTask(SDMTool.Type.CORPSEFINDER) }
            .filterNotNull()
            .filter { it.completedAt!! > start }
            .onEach { task ->
                val result = task.result as? CorpseFinderTask.Result ?: return@onEach
                if (!handledResults.contains(task.id)) {
                    handledResults.add(task.id)
                    events.postValue(CorpseDetailsEvents.TaskResult(result))
                }
            }
            .launchInViewModel()
    }

    val events = SingleLiveEvent<CorpseDetailsEvents>()

    val state = combine(
        corpseFinder.progress,
        corpseFinder.state
            .map { it.data }
            .filterNotNull()
            .distinctUntilChangedBy { data -> data.corpses.map { it.identifier }.toSet() },
    ) { progress, data ->
        val sortedCorpses = data.corpses
            .sortedByDescending { it.size }
            .toList()

        val availableTarget = resolveTarget(
            items = sortedCorpses,
            requestedTarget = currentTarget ?: args.corpsePath,
            lastPosition = lastPosition,
            identifierOf = { it.identifier },
            onPositionTracked = { lastPosition = it },
        )

        State(
            items = sortedCorpses,
            target = availableTarget,
            progress = progress,
        )
    }.asLiveData2()

    data class State(
        val items: List<Corpse>,
        val target: CorpseIdentifier?,
        val progress: Progress.Data?,
    )

    fun updatePage(identifier: CorpseIdentifier) {
        log(TAG) { "updatePage($identifier)" }
        currentTarget = identifier
    }

    companion object {
        private val TAG = logTag("CorpseFinder", "Details", "ViewModel")
    }
}