package eu.darken.sdmse.corpsefinder.ui.details

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.hasData
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderScanTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderTask
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class CorpseDetailsFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val corpseFinder: CorpseFinder,
    private val taskManager: TaskManager,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {
    private val args by handle.navArgs<CorpseDetailsFragmentArgs>()

    init {
        corpseFinder.data
            .filter { !it.hasData }
            .take(1)
            .onEach { popNavStack() }
            .launchInViewModel()
    }

    val events = SingleLiveEvent<CorpseDetailsEvents>()

    val state = corpseFinder.data
        .filterNotNull()
        .distinctUntilChangedBy { data -> data.corpses.map { it.path }.toSet() }
        .map {
            State(
                items = it.corpses.toList(),
                target = args.corpsePath,
            )
        }
        .asLiveData2()

    data class State(
        val items: List<Corpse>,
        val target: APath?
    )

    fun forwardTask(task: CorpseFinderTask) = launch {
        log(TAG) { "forwardTask(): $task" }
        val result = taskManager.submit(task) as CorpseFinderTask.Result
        log(TAG) { "forwardTask(): Result $result" }
        when (result) {
            is CorpseFinderDeleteTask.Success -> events.postValue(CorpseDetailsEvents.TaskResult(result))
            is CorpseFinderScanTask.Success -> {}
        }
    }

    companion object {
        private val TAG = logTag("CorpseFinder", "Details", "Fragment", "VM")
    }
}