package eu.darken.sdmse.appcleaner.ui.list

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.hasData
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerDeleteTask
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@HiltViewModel
class AppCleanerListFragmentVM @Inject constructor(
    private val handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val appCleaner: AppCleaner,
    private val taskManager: TaskManager,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel3(dispatcherProvider) {

    init {
        appCleaner.data
            .filter { !it.hasData }
            .take(1)
            .onEach { popNavStack() }
            .launchInViewModel()
    }

    val events = SingleLiveEvent<AppCleanerListEvents>()

    val state = combine(
        appCleaner.data.filterNotNull(),
        appCleaner.progress,
    ) { data, progress ->
        val items = data.junks
            .sortedByDescending { it.size }
            .map { content ->
                AppCleanerListRowVH.Item(
                    junk = content,
                    onItemClicked = {
                        events.postValue(AppCleanerListEvents.ConfirmDeletion(it))
                    },
                    onDetailsClicked = { showDetails(it) }
                )
            }
        State(
            items = items,
            progress = progress,
        )
    }.asLiveData2()

    fun doDelete(appJunk: AppJunk) = launch {
        log(TAG, INFO) { "doDelete(appJunk=$appJunk)" }
        if (!upgradeRepo.isPro()) {
            MainDirections.goToUpgradeFragment().navigate()
            return@launch
        }
        val task = AppCleanerDeleteTask(targetPkgs = setOf(appJunk.identifier))
        val result = taskManager.submit(task) as AppCleanerDeleteTask.Result
        log(TAG) { "doDelete(): Result was $result" }
        when (result) {
            is AppCleanerDeleteTask.Success -> events.postValue(AppCleanerListEvents.TaskResult(result))
        }
    }

    fun showDetails(appJunk: AppJunk) = launch {
        log(TAG, INFO) { "showDetails(appJunk=$appJunk)" }
        AppCleanerListFragmentDirections.actionAppCleanerListFragmentToAppCleanerDetailsFragment2(
            identifier = appJunk.identifier
        ).navigate()
    }

    data class State(
        val items: List<AppCleanerListAdapter.Item>,
        val progress: Progress.Data?,
    )

    companion object {
        private val TAG = logTag("AppCleaner", "List", "VM")
    }
}