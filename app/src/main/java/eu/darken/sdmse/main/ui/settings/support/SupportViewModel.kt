package eu.darken.sdmse.main.ui.settings.support

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SDMId
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.recorder.core.RecorderModule
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class SupportViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val sdmId: SDMId,
    private val recorderModule: RecorderModule,
) : ViewModel3(dispatcherProvider) {

    val clipboardEvent = SingleLiveEvent<String>()

    val isRecording = recorderModule.state.map { it.isRecording }.asLiveData2()

    fun copyInstallID() = launch {
        clipboardEvent.postValue(sdmId.id)
    }

    fun startDebugLog() = launch {
        log { "startDebugLog()" }
        recorderModule.startRecorder()
    }

    fun stopDebugLog() = launch {
        log { "stopDebugLog()" }
        recorderModule.stopRecorder()
    }
}