package eu.darken.sdmse.systemcleaner.ui.details.filtercontent

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerDeleteTask
import eu.darken.sdmse.systemcleaner.ui.details.filtercontent.elements.FilterContentElementFileVH
import eu.darken.sdmse.systemcleaner.ui.details.filtercontent.elements.FilterContentElementHeaderVH
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class FilterContentFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val systemCleaner: SystemCleaner,
    private val taskManager: TaskManager,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val args = FilterContentFragmentArgs.fromSavedStateHandle(handle)

    val events = SingleLiveEvent<FilterContentEvents>()

    val state = combine(
        systemCleaner.data
            .filterNotNull()
            .map { data ->
                data.filterContents.singleOrNull { it.filterIdentifier == args.identifier }
            }
            .filterNotNull(),
        systemCleaner.progress,
    ) { filterContent, progress ->
        val elements = mutableListOf<FilterContentElementsAdapter.Item>()

        FilterContentElementHeaderVH.Item(
            filterContent = filterContent,
            onDeleteAllClicked = {
                events.postValue(FilterContentEvents.ConfirmDeletion(it.filterContent.filterIdentifier))
            },
            onExcludeClicked = {
                launch {
                    filterContent.items.forEach { systemCleaner.exclude(filterContent.filterIdentifier, it) }
                }
            }
        ).run { elements.add(this) }

        filterContent.items.map { item ->
            FilterContentElementFileVH.Item(
                filterContent = filterContent,
                lookup = item,
                onItemClick = {
                    events.postValue(
                        FilterContentEvents.ConfirmFileDeletion(it.filterContent.filterIdentifier, it.lookup)
                    )
                },
            )
        }.run { elements.addAll(this) }

        State(
            items = elements,
            progress = progress
        )
    }.asLiveData2()

    fun doDelete(identifier: FilterIdentifier) = launch {
        log(TAG, INFO) { "doDelete(): $identifier" }
        val task = SystemCleanerDeleteTask(targetFilters = setOf(identifier))
        // Removing the filtercontent, removes the fragment and also this viewmodel, so we can't post our own result
        events.postValue(FilterContentEvents.TaskForParent(task))
    }

    fun doDelete(identifier: FilterIdentifier, path: APath) = launch {
        log(TAG, INFO) { "doDelete(): $path" }
        val task = SystemCleanerDeleteTask(setOf(identifier), setOf(path))
        events.postValue(FilterContentEvents.TaskForParent(task))
    }

    fun doExclude(identifier: FilterIdentifier, path: APath) = launch {
        log(TAG) { "doExclude(): $identifier, $path" }
        systemCleaner.exclude(identifier, path)
    }

    data class State(
        val items: List<FilterContentElementsAdapter.Item>,
        val progress: Progress.Data?,
    )

    companion object {
        private val TAG = logTag("SystemCleaner", "Details", "Fragment", "VM")
    }
}