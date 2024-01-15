package eu.darken.sdmse.common.previews

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.lookup
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class PreviewViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val gatewaySwitch: GatewaySwitch,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val args = PreviewFragmentArgs.fromSavedStateHandle(handle)

    val events = SingleLiveEvent<PreviewEvents>()

    val state = flowOf(args.path)
        .map { previewPath ->
            State(preview = previewPath.lookup(gatewaySwitch))
        }
        .onStart { emit(State(progress = Progress.Data())) }
        .asLiveData2()

    data class State(
        val preview: APathLookup<*>? = null,
        val progress: Progress.Data? = null,
    )

    companion object {
        private val TAG = logTag("CorpseFinder", "Details", "ViewModel")
    }
}