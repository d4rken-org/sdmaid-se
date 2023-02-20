package eu.darken.sdmse.appcleaner.ui.list

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerDeleteTask
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class AppCleanerListFragmentVM @Inject constructor(
    private val handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val appCleaner: AppCleaner,
    private val taskManager: TaskManager,
) : ViewModel3(dispatcherProvider) {

    val events = SingleLiveEvent<AppCleanerListEvents>()

    val items = appCleaner.data
        .filterNotNull()
        .map { data ->
            data.junks.map { content ->
                AppCleanerListRowVH.Item(
                    junk = content,
                    onItemClicked = {
                        events.postValue(AppCleanerListEvents.ConfirmDeletion(it))
                    },
                    onDetailsClicked = { showDetails(it) }
                )
            }
        }
        .asLiveData2()

    init {
        appCleaner.data
            .filter { it == null }
            .take(1)
            .onEach { popNavStack() }
            .launchInViewModel()
    }

    fun doDelete(appJunk: AppJunk) = launch {
        log(TAG, INFO) { "doDelete(appJunk=$appJunk)" }
        val task = AppCleanerDeleteTask(targetPkgs = setOf(appJunk.pkg.id))
        val result = taskManager.submit(task) as AppCleanerDeleteTask.Result
        log(TAG) { "doDelete(): Result was $result" }
        when (result) {
            is AppCleanerDeleteTask.Success -> events.postValue(AppCleanerListEvents.TaskResult(result))
        }
    }

    fun showDetails(appJunk: AppJunk) = launch {
        log(TAG, INFO) { "showDetails(appJunk=$appJunk)" }
        AppCleanerListFragmentDirections.actionAppCleanerListFragmentToAppCleanerDetailsFragment2(
            pkgId = appJunk.identifier
        ).navigate()
    }

    companion object {
        private val TAG = logTag("AppCleaner", "List", "VM")
    }
}