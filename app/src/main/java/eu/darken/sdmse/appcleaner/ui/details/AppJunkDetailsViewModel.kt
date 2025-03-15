package eu.darken.sdmse.appcleaner.ui.details

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.hasData
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerTask
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
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
import java.lang.Integer.min
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class AppJunkDetailsViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    appCleaner: AppCleaner,
    private val taskManager: TaskManager,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val args by handle.navArgs<AppJunkDetailsFragmentArgs>()

    private var currentTarget: InstallId? = null
        get() = field ?: handle["target"]
        set(value) {
            field = value.also { handle["target"] = it }
        }
    private var lastPosition: Int? = null
        get() = field ?: handle["position"]
        set(value) {
            field = value.also { handle["position"] = it }
        }

    init {
        appCleaner.state
            .map { it.data }
            .filter { !it.hasData }
            .take(1)
            .onEach { popNavStack() }
            .launchInViewModel()

        val start = Instant.now()
        val handledResults = mutableSetOf<String>()
        taskManager.state
            .map { it.getLatestTask(SDMTool.Type.APPCLEANER) }
            .filterNotNull()
            .filter { it.completedAt!! > start }
            .onEach { task ->
                val result = task.result as? AppCleanerTask.Result ?: return@onEach
                if (!handledResults.contains(task.id)) {
                    handledResults.add(task.id)
                    events.postValue(AppJunkDetailsEvents.TaskResult(result))
                }
            }
            .launchInViewModel()
    }

    val events = SingleLiveEvent<AppJunkDetailsEvents>()

    val state = combine(
        appCleaner.progress,
        appCleaner.state
            .map { it.data }
            .filterNotNull()
            .distinctUntilChangedBy { junks -> junks.junks.map { it.identifier }.toSet() },
    ) { progress, newData ->
        val newJunks = newData.junks.sortedByDescending { it.size }

        val requestedTarget = currentTarget ?: args.identifier

        // Target still within the data set?
        val currentIndex = newJunks.indexOfFirst { it.identifier == requestedTarget }
        if (currentIndex != -1) lastPosition = currentIndex

        // If the target is no longer with us, use the new item that is now at the same position
        val availableTarget = when {
            newJunks.isEmpty() -> null
            currentIndex != -1 -> requestedTarget
            lastPosition != null -> newJunks[min(lastPosition!!, newJunks.size - 1)].identifier
            else -> requestedTarget
        }

        State(
            items = newJunks,
            target = availableTarget,
            progress = progress,
        )
    }.asLiveData2()

    data class State(
        val items: List<AppJunk>,
        val target: InstallId?,
        val progress: Progress.Data?,
    )

    fun updatePage(identifier: InstallId) {
        log(TAG) { "updatePage($identifier)" }
        currentTarget = identifier
    }

    companion object {
        private val TAG = logTag("AppCleaner", "Details", "ViewModel")
    }
}