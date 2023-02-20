package eu.darken.sdmse.appcleaner.ui.details

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerDeleteTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerTask
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class AppJunkDetailsFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val appCleaner: AppCleaner,
    private val taskManager: TaskManager,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {
    private val args by handle.navArgs<AppJunkDetailsFragmentArgs>()

    val events = SingleLiveEvent<AppJunkDetailsEvents>()

    init {
        appCleaner.data
            .filter { it == null }
            .take(1)
            .onEach {
                popNavStack()
            }
            .launchInViewModel()
    }

    val state = appCleaner.data
        .filterNotNull()
        .distinctUntilChangedBy { junks ->
            junks.junks.map { it.identifier }.toSet()
        }
        .map {
            State(
                items = it.junks.toList(),
                target = args.pkgId,
            )
        }
        .asLiveData2()

    data class State(
        val items: List<AppJunk>,
        val target: Pkg.Id?
    )

    fun forwardTask(task: AppCleanerTask) = launch {
        log(TAG) { "forwardTask(): $task" }
        val result = taskManager.submit(task) as AppCleanerTask.Result
        log(TAG) { "forwardTask(): Result $result" }
        when (result) {
            is AppCleanerScanTask.Success -> TODO()
            is AppCleanerDeleteTask.Success -> events.postValue(AppJunkDetailsEvents.TaskResult(result))
        }
    }

    companion object {
        private val TAG = logTag("AppCleaner", "Details", "Fragment", "VM")
    }
}