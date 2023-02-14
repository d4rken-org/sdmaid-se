package eu.darken.sdmse.systemcleaner.ui.details

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.systemcleaner.core.FilterContent
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class FilterContentDetailsFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val systemCleaner: SystemCleaner,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {
    private val args by handle.navArgs<FilterContentDetailsFragmentArgs>()

    init {
        systemCleaner.data
            .filter { it == null }
            .take(1)
            .onEach {
                popNavStack()
            }
            .launchInViewModel()
    }

    val state = systemCleaner.data
        .filterNotNull()
        .map {
            State(
                items = it.filterContents.toList(),
                target = args.filterIdentifier,
            )
        }
        .asLiveData2()

    data class State(
        val items: List<FilterContent>,
        val target: FilterIdentifier?
    )

    companion object {
        private val TAG = logTag("SystemCleaner", "Details", "Fragment", "VM")
    }
}