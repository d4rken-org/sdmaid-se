package eu.darken.sdmse.corpsefinder.ui.details

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.PagedDetailsViewModel
import eu.darken.sdmse.common.uix.resolveTarget
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.CorpseIdentifier
import eu.darken.sdmse.corpsefinder.core.hasData
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderTask
import eu.darken.sdmse.corpsefinder.ui.CorpseDetailsRoute
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.main.core.taskmanager.uniqueTaskResults
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class CorpseDetailsViewModel @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val corpseFinder: CorpseFinder,
    private val taskSubmitter: TaskSubmitter,
) : PagedDetailsViewModel<CorpseDetailsRoute, CorpseIdentifier>(handle, dispatcherProvider, tag = TAG) {

    val events = SingleEventFlow<Event>()

    override fun bindRouteLogValue(route: CorpseDetailsRoute): Any? = route.corpsePath

    init {
        // `mapNotNull { it.data }` skips the null transitions that performScan publishes at
        // the start of a refresh, so navUp fires only on real "drain to empty", not loading.
        autoNavUpOnEmpty(corpseFinder.state.mapNotNull { it.data }.map { it.hasData })

        taskSubmitter.uniqueTaskResults<CorpseFinderTask.Result>(SDMTool.Type.CORPSEFINDER)
            .onEach { events.tryEmit(Event.TaskResult(it)) }
            .launchInViewModel()
    }

    val state: StateFlow<State> = routeFlow.filterNotNull().flatMapLatest { route ->
        // Item production excludes progress so high-frequency progress ticks during a scan don't
        // re-sort the corpse list and re-run resolveTarget. Progress is merged in last (below) as a
        // cheap field swap that preserves the items List instance, letting keyed pager pages skip.
        val itemsState = corpseFinder.state
            .map { it.data }
            .filterNotNull()
            .distinctUntilChanged()
            .map { data ->
                val sortedCorpses = data.corpses
                    .sortedByDescending { it.size }
                    .toList()

                val availableTarget = resolveTarget(
                    items = sortedCorpses,
                    requestedTarget = currentTarget ?: route.corpsePath,
                    lastPosition = lastPosition,
                    identifierOf = { it.identifier },
                    onPositionTracked = { lastPosition = it },
                )

                State(
                    items = sortedCorpses,
                    target = availableTarget,
                )
            }

        combine(itemsState, corpseFinder.progress) { base, progress ->
            base.copy(progress = progress)
        }
    }.safeStateIn(
        initialValue = State(),
        onError = { State() },
    )

    fun onPageChanged(identifier: CorpseIdentifier) {
        log(TAG) { "onPageChanged($identifier)" }
        currentTarget = identifier
    }

    fun onConfirmDeleteCorpse(corpseId: CorpseIdentifier) = launch {
        log(TAG, INFO) { "onConfirmDeleteCorpse($corpseId)" }
        val snapshot = corpseFinder.state.first().data ?: return@launch
        if (snapshot.corpses.none { it.identifier == corpseId }) return@launch
        taskSubmitter.submit(CorpseFinderDeleteTask(targetCorpses = setOf(corpseId)))
    }

    fun onConfirmDeleteContent(corpseId: CorpseIdentifier, paths: Set<APath>) = launch {
        log(TAG, INFO) { "onConfirmDeleteContent($corpseId, ${paths.size})" }
        if (paths.isEmpty()) return@launch
        val snapshot = corpseFinder.state.first().data ?: return@launch
        val corpse = snapshot.corpses.firstOrNull { it.identifier == corpseId } ?: return@launch
        val validPaths = paths.filter { p -> corpse.content.any { it.lookedUp == p } }.toSet()
        if (validPaths.isEmpty()) return@launch
        taskSubmitter.submit(
            CorpseFinderDeleteTask(
                targetCorpses = setOf(corpseId),
                targetContent = validPaths,
            ),
        )
    }

    fun onExcludeCorpse(corpseId: CorpseIdentifier) = launch {
        log(TAG, INFO) { "onExcludeCorpse($corpseId)" }
        val snapshot = corpseFinder.state.first().data ?: return@launch
        if (snapshot.corpses.none { it.identifier == corpseId }) return@launch
        val undo = corpseFinder.exclude(setOf(corpseId))
        events.tryEmit(
            Event.ExclusionsCreated(
                count = undo.exclusionIds.size,
                undo = undo,
                restoreTarget = corpseId,
            ),
        )
    }

    fun onUndoExclude(undo: CorpseFinder.ExclusionUndo, restoreTarget: CorpseIdentifier) = launch {
        log(TAG, INFO) { "onUndoExclude(${undo.exclusionIds.size}, restore=$restoreTarget)" }
        currentTarget = restoreTarget
        corpseFinder.undoExclude(undo)
    }

    data class State(
        val items: List<Corpse> = emptyList(),
        val target: CorpseIdentifier? = null,
        val progress: Progress.Data? = null,
    )

    sealed interface Event {
        data class TaskResult(val result: CorpseFinderTask.Result) : Event
        data class ExclusionsCreated(
            val count: Int,
            val undo: CorpseFinder.ExclusionUndo,
            val restoreTarget: CorpseIdentifier,
        ) : Event
    }

    companion object {
        private val TAG = logTag("CorpseFinder", "Details", "ViewModel")
    }
}
