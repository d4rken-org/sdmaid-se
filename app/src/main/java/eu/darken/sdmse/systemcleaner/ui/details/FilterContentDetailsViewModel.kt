package eu.darken.sdmse.systemcleaner.ui.details

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
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.core.taskmanager.getLatestTask
import eu.darken.sdmse.systemcleaner.core.FilterContent
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import eu.darken.sdmse.systemcleaner.core.hasData
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerTask
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
class FilterContentDetailsViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    systemCleaner: SystemCleaner,
    private val taskManager: TaskManager,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {
    private val args by handle.navArgs<FilterContentDetailsFragmentArgs>()
    private var currentTarget: FilterIdentifier? by handle.mutableState("target")
    private var lastPosition: Int? by handle.mutableState("position")

    init {
        systemCleaner.state
            .map { it.data }
            .filter { !it.hasData }
            .take(1)
            .onEach {
                popNavStack()
            }
            .launchInViewModel()

        val start = Instant.now()
        val handledResults = mutableSetOf<String>()
        taskManager.state
            .map { it.getLatestTask(SDMTool.Type.SYSTEMCLEANER) }
            .filterNotNull()
            .filter { it.completedAt!! > start }
            .onEach { task ->
                val result = task.result as? SystemCleanerTask.Result ?: return@onEach
                if (!handledResults.contains(task.id)) {
                    handledResults.add(task.id)
                    events.postValue(FilterContentDetailsEvents.TaskResult(result))
                }
            }
            .launchInViewModel()
    }

    val events = SingleLiveEvent<FilterContentDetailsEvents>()

    val state = combine(
        systemCleaner.progress,
        systemCleaner.state
            .map { it.data }
            .filterNotNull()
            .distinctUntilChangedBy { data ->
                data.filterContents.map { it.identifier }.toSet()
            },
    ) { progress, data ->
        val sortedContents = data.filterContents.sortedByDescending { it.size }

        val availableTarget = resolveTarget(
            items = sortedContents,
            requestedTarget = currentTarget ?: args.filterIdentifier,
            lastPosition = lastPosition,
            identifierOf = { it.identifier },
            onPositionTracked = { lastPosition = it },
        )

        State(
            items = sortedContents,
            target = availableTarget,
            progress = progress,
        )
    }.asLiveData2()

    data class State(
        val items: List<FilterContent>,
        val target: FilterIdentifier?,
        val progress: Progress.Data?,
    )

    fun updatePage(identifier: FilterIdentifier) {
        log(TAG) { "updatePage($identifier)" }
        currentTarget = identifier
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "Details", "ViewModel")
    }
}