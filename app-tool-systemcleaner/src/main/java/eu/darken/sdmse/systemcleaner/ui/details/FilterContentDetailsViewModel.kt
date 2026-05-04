package eu.darken.sdmse.systemcleaner.ui.details

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.navigation.mutableState
import eu.darken.sdmse.common.previews.PreviewOptions
import eu.darken.sdmse.common.previews.PreviewRoute
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.uix.resolveTarget
import eu.darken.sdmse.exclusion.ui.ExclusionsListRoute
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.main.core.taskmanager.getLatestTask
import eu.darken.sdmse.systemcleaner.core.FilterContent
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import eu.darken.sdmse.systemcleaner.core.filter.filterIdentifier
import eu.darken.sdmse.systemcleaner.core.filter.stock.ScreenshotsFilter
import eu.darken.sdmse.systemcleaner.core.filter.stock.TrashedFilter
import eu.darken.sdmse.systemcleaner.core.hasData
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerProcessingTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerTask
import eu.darken.sdmse.systemcleaner.ui.FilterContentDetailsRoute
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class FilterContentDetailsViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val systemCleaner: SystemCleaner,
    private val taskSubmitter: TaskSubmitter,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    private val initialFilterIdentifier: FilterIdentifier? =
        FilterContentDetailsRoute.from(handle).filterIdentifier
    private var currentTarget: FilterIdentifier? by handle.mutableState("target")
    private var lastPosition: Int? by handle.mutableState("position")

    val events = SingleEventFlow<Event>()

    init {
        systemCleaner.state
            .map { it.data }
            .filter { !it.hasData }
            .take(1)
            .onEach { navUp() }
            .launchIn(vmScope)

        val start = Instant.now()
        val handledResults = mutableSetOf<String>()
        taskSubmitter.state
            .map { it.getLatestTask(SDMTool.Type.SYSTEMCLEANER) }
            .filterNotNull()
            .filter { it.completedAt!! > start }
            .onEach { task ->
                val result = task.result as? SystemCleanerTask.Result ?: return@onEach
                if (!handledResults.contains(task.id)) {
                    handledResults.add(task.id)
                    events.tryEmit(Event.TaskResult(result))
                }
            }
            .launchIn(vmScope)
    }

    val state: StateFlow<State> = combine(
        systemCleaner.progress,
        systemCleaner.state.map { it.data }.filterNotNull(),
    ) { progress, data ->
        val sortedContents = data.filterContents.sortedByDescending { it.size }

        val availableTarget = resolveTarget(
            items = sortedContents,
            requestedTarget = currentTarget ?: initialFilterIdentifier,
            lastPosition = lastPosition,
            identifierOf = { it.identifier },
            onPositionTracked = { lastPosition = it },
        )

        State(
            items = sortedContents,
            target = availableTarget,
            progress = progress,
        )
    }.safeStateIn(
        initialValue = State(),
        onError = { State() },
    )

    fun onPageChanged(identifier: FilterIdentifier) {
        log(TAG) { "onPageChanged($identifier)" }
        currentTarget = identifier
    }

    fun onConfirmDeleteFilter(id: FilterIdentifier) = launch {
        log(TAG, INFO) { "onConfirmDeleteFilter($id)" }
        val data = systemCleaner.state.first().data ?: return@launch
        if (data.filterContents.none { it.identifier == id }) return@launch
        taskSubmitter.submit(SystemCleanerProcessingTask(targetFilters = setOf(id)))
    }

    fun onConfirmDeleteFiles(id: FilterIdentifier, paths: Set<APath>) = launch {
        log(TAG, INFO) { "onConfirmDeleteFiles($id, ${paths.size})" }
        if (paths.isEmpty()) return@launch
        val data = systemCleaner.state.first().data ?: return@launch
        val fc = data.filterContents.firstOrNull { it.identifier == id } ?: return@launch
        val livePaths = fc.items.map { it.path }.toSet()
        val validPaths = paths intersect livePaths
        if (validPaths.isEmpty()) return@launch
        taskSubmitter.submit(
            SystemCleanerProcessingTask(
                targetFilters = setOf(id),
                targetContent = validPaths,
            ),
        )
    }

    fun onExcludeFilter(id: FilterIdentifier) = launch {
        log(TAG, INFO) { "onExcludeFilter($id)" }
        val data = systemCleaner.state.first().data ?: return@launch
        val fc = data.filterContents.firstOrNull { it.identifier == id } ?: return@launch
        val paths = fc.items.map { it.path }.toSet()
        if (paths.isEmpty()) return@launch
        val undo = systemCleaner.exclude(id, paths)
        events.tryEmit(
            Event.ExclusionsCreated(
                count = undo.exclusionIds.size,
                undo = undo,
                restoreTarget = id,
            ),
        )
    }

    fun onExcludeFiles(id: FilterIdentifier, paths: Set<APath>) = launch {
        log(TAG, INFO) { "onExcludeFiles($id, ${paths.size})" }
        if (paths.isEmpty()) return@launch
        val data = systemCleaner.state.first().data ?: return@launch
        val fc = data.filterContents.firstOrNull { it.identifier == id } ?: return@launch
        val livePaths = fc.items.map { it.path }.toSet()
        val validPaths = paths intersect livePaths
        if (validPaths.isEmpty()) return@launch
        systemCleaner.exclude(id, validPaths)
        events.tryEmit(Event.SelectionExclusionsCreated(validPaths.size))
    }

    fun onShowExclusions() {
        navTo(ExclusionsListRoute)
    }

    fun onUndoExclude(undo: SystemCleaner.ExclusionUndo, restoreTarget: FilterIdentifier) = launch {
        log(TAG, INFO) { "onUndoExclude(${undo.exclusionIds.size}, restore=$restoreTarget)" }
        currentTarget = restoreTarget
        systemCleaner.undoExclude(undo)
    }

    fun onPreviewFile(filterId: FilterIdentifier, path: APath) {
        log(TAG, INFO) { "onPreviewFile($filterId, $path)" }
        val supportsPreview = filterId == TrashedFilter::class.filterIdentifier ||
            filterId == ScreenshotsFilter::class.filterIdentifier
        if (!supportsPreview) return
        navTo(PreviewRoute(options = PreviewOptions(path)))
    }

    data class State(
        val items: List<FilterContent> = emptyList(),
        val target: FilterIdentifier? = null,
        val progress: Progress.Data? = null,
    )

    sealed interface Event {
        data class TaskResult(val result: SystemCleanerTask.Result) : Event
        data class ExclusionsCreated(
            val count: Int,
            val undo: SystemCleaner.ExclusionUndo,
            val restoreTarget: FilterIdentifier,
        ) : Event

        data class SelectionExclusionsCreated(val count: Int) : Event
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "Details", "ViewModel")
    }
}
