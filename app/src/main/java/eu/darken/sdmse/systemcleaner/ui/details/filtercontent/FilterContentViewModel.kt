package eu.darken.sdmse.systemcleaner.ui.details.filtercontent

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavDirections
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.R
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.previews.PreviewFragmentArgs
import eu.darken.sdmse.common.previews.PreviewOptions
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.filter.filterIdentifier
import eu.darken.sdmse.systemcleaner.core.filter.stock.EmptyDirectoryFilter
import eu.darken.sdmse.systemcleaner.core.filter.stock.ScreenshotsFilter
import eu.darken.sdmse.systemcleaner.core.filter.stock.TrashedFilter
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerProcessingTask
import eu.darken.sdmse.systemcleaner.ui.details.filtercontent.elements.FilterContentElementFileVH
import eu.darken.sdmse.systemcleaner.ui.details.filtercontent.elements.FilterContentElementHeaderVH
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class FilterContentViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val systemCleaner: SystemCleaner,
    private val taskManager: TaskManager,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val args = FilterContentFragmentArgs.fromSavedStateHandle(handle)

    val events = SingleLiveEvent<FilterContentEvents>()

    val state = combine(
        systemCleaner.state
            .map { it.data }
            .filterNotNull()
            .map { data ->
                data.filterContents.singleOrNull { it.identifier == args.identifier }
            }
            .filterNotNull(),
        systemCleaner.progress,
    ) { filterContent, progress ->
        val elements = mutableListOf<FilterContentElementsAdapter.Item>()

        FilterContentElementHeaderVH.Item(
            filterContent = filterContent,
            onDeleteAllClicked = { delete(setOf(it)) },
            onExcludeClicked = { exclude(setOf(it)) }
        ).run { elements.add(this) }

        val sorted = when (filterContent.identifier) {
            EmptyDirectoryFilter::class.filterIdentifier -> filterContent.items.sortedBy { it.path.path }
            TrashedFilter::class.filterIdentifier -> filterContent.items.sortedByDescending { it.lookup.modifiedAt }
            ScreenshotsFilter::class.filterIdentifier -> filterContent.items.sortedBy { it.lookup.modifiedAt }
            else -> filterContent.items.sortedByDescending { it.expectedGain }
        }

        sorted.map { item ->
            FilterContentElementFileVH.Item(
                filterContent = filterContent,
                match = item,
                onItemClick = { delete(setOf(it)) },
                onThumbnailClick = when (filterContent.identifier) {
                    TrashedFilter::class.filterIdentifier, ScreenshotsFilter::class.filterIdentifier -> {
                        {
                            object : NavDirections {
                                override val actionId: Int = R.id.goToPreview
                                override val arguments: Bundle = PreviewFragmentArgs(
                                    PreviewOptions(item.path)
                                ).toBundle()

                            }.navigate()
                        }
                    }

                    else -> null
                },
                showDate = when (filterContent.identifier) {
                    TrashedFilter::class.filterIdentifier -> true
                    ScreenshotsFilter::class.filterIdentifier -> true
                    else -> false
                }
            )
        }.run { elements.addAll(this) }

        State(
            items = elements,
            progress = progress
        )
    }.asLiveData2()

    fun delete(items: Collection<FilterContentElementsAdapter.Item>, confirmed: Boolean = false) = launch {
        log(TAG, INFO) { "delete(): ${items.size} confirmed=$confirmed" }
        if (!confirmed) {
            events.postValue(FilterContentEvents.ConfirmDeletion(items))
            return@launch
        }

        val task = when {
            items.singleOrNull() is FilterContentElementHeaderVH.Item -> SystemCleanerProcessingTask(
                targetFilters = setOf(args.identifier)
            )

            else -> SystemCleanerProcessingTask(
                setOf(args.identifier),
                items.mapNotNull {
                    when (it) {
                        is FilterContentElementFileVH.Item -> it.match.path
                        else -> null
                    }
                }.toSet()
            )
        }
        taskManager.submit(task)
    }

    fun exclude(items: Collection<FilterContentElementsAdapter.Item>) = launch {
        log(TAG) { "exclude(): ${items.size}" }
        val toExclude = items.mapNotNull { item ->
            when (item) {
                is FilterContentElementFileVH.Item -> setOf(item.match.path)
                is FilterContentElementHeaderVH.Item -> item.filterContent.items.map { it.path }
                else -> null
            }
        }.flatten().toSet()
        systemCleaner.exclude(args.identifier, toExclude)
    }

    data class State(
        val items: List<FilterContentElementsAdapter.Item>,
        val progress: Progress.Data?,
    )

    companion object {
        private val TAG = logTag("SystemCleaner", "Details", "ViewModel")
    }
}