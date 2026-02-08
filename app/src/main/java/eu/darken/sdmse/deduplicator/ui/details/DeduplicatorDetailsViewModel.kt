package eu.darken.sdmse.deduplicator.ui.details

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
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.DeduplicatorSettings
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.hasData
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorTask
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
class DeduplicatorDetailsViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    deduplicator: Deduplicator,
    private val taskManager: TaskManager,
    private val settings: DeduplicatorSettings,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {
    private val args by handle.navArgs<DeduplicatorDetailsFragmentArgs>()
    private var currentTarget: Duplicate.Cluster.Id? by handle.mutableState("target")
    private var lastPosition: Int? by handle.mutableState("position")

    init {
        deduplicator.state
            .map { it.data }
            .filter { !it.hasData }
            .take(1)
            .onEach { popNavStack() }
            .launchInViewModel()

        val start = Instant.now()
        val handledResults = mutableSetOf<String>()
        taskManager.state
            .map { it.getLatestTask(SDMTool.Type.DEDUPLICATOR) }
            .filterNotNull()
            .filter { it.completedAt!! > start }
            .onEach { task ->
                val result = task.result as? DeduplicatorTask.Result ?: return@onEach
                if (!handledResults.contains(task.id)) {
                    handledResults.add(task.id)
                    events.postValue(DeduplicatorDetailsEvents.TaskResult(result))
                }
            }
            .launchInViewModel()
    }

    val events = SingleLiveEvent<DeduplicatorDetailsEvents>()

    val state = combine(
        deduplicator.progress,
        deduplicator.state
            .map { it.data }
            .filterNotNull()
            .distinctUntilChangedBy { data -> data.clusters.map { it.identifier }.toSet() },
        settings.isDirectoryViewEnabled.flow,
    ) { progress, data, isDirectoryViewEnabled ->
        val sortedClusters = data.clusters
            .sortedByDescending { it.averageSize }

        val availableTarget = resolveTarget(
            items = sortedClusters,
            requestedTarget = currentTarget ?: args.identifier,
            lastPosition = lastPosition,
            identifierOf = { it.identifier },
            onPositionTracked = { lastPosition = it },
        )

        State(
            items = sortedClusters,
            target = availableTarget,
            progress = progress,
            isDirectoryViewEnabled = isDirectoryViewEnabled,
        )
    }.asLiveData2()

    data class State(
        val items: List<Duplicate.Cluster>,
        val target: Duplicate.Cluster.Id?,
        val progress: Progress.Data?,
        val isDirectoryViewEnabled: Boolean = false,
    )

    fun updatePage(identifier: Duplicate.Cluster.Id) {
        log(TAG) { "updatePage($identifier)" }
        currentTarget = identifier
    }

    fun toggleDirectoryView() = launch {
        log(TAG) { "toggleDirectoryView()" }
        settings.isDirectoryViewEnabled.update { !it }
    }

    companion object {
        private val TAG = logTag("Deduplicator", "Details", "ViewModel")
    }
}