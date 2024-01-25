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
    private val options = args.options
    private val currentPosition = MutableStateFlow(options.position)

    val events = SingleLiveEvent<PreviewEvents>()

    val state = combine(
        currentPosition,
        flowOf(options.paths)
    ) { pos, paths ->
        State(
            position = pos,
            previews = paths.map { it.lookup(gatewaySwitch) }
        )
    }
        .onStart { emit(State(progress = Progress.Data())) }
        .asLiveData2()

    data class State(
        val position: Int = 0,
        val previews: List<APathLookup<*>>? = null,
        val progress: Progress.Data? = null,
    ) {
        val preview: APathLookup<*>?
            get() = previews
                ?.takeIf { it.isNotEmpty() }
                ?.let { it[position] }
    }

    fun next() {
        currentPosition.value = (currentPosition.value + 1) % options.paths.size
    }

    fun previous() {
        currentPosition.value = (currentPosition.value - 1 + options.paths.size) % options.paths.size
    }

    companion object {
        private val TAG = logTag("Preview", "ViewModel")
    }
}