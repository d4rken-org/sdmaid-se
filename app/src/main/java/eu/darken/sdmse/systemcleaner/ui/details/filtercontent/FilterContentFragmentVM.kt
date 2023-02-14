package eu.darken.sdmse.systemcleaner.ui.details.filtercontent

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.ui.details.filtercontent.elements.FilterContentElementFileVH
import eu.darken.sdmse.systemcleaner.ui.details.filtercontent.elements.FilterContentElementHeaderVH
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class FilterContentFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val systemCleaner: SystemCleaner,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val args = FilterContentFragmentArgs.fromSavedStateHandle(handle)

    val info = systemCleaner.data
        .filterNotNull()
        .map { data ->
            data.filterContents.singleOrNull { it.filterIdentifier == args.identifier }
        }
        .filterNotNull()
        .map { filterContent ->
            val elements = mutableListOf<FilterContentElementsAdapter.Item>()

            FilterContentElementHeaderVH.Item(
                filterContent = filterContent,
                onDeleteAllClicked = {
//                    TODO()
                },
                onExcludeClicked = {
//                    TODO()
                }
            ).run { elements.add(this) }

            filterContent.items.map {
                FilterContentElementFileVH.Item(
                    filterContent = filterContent,
                    lookup = it,
                    onItemClick = {
//                        TODO()
                    }
                )
            }.run { elements.addAll(this) }

            Info(elements = elements)
        }
        .asLiveData2()

    data class Info(
        val elements: List<FilterContentElementsAdapter.Item>,
    )

    companion object {
        private val TAG = logTag("SystemCleaner", "Details", "Fragment", "VM")
    }
}