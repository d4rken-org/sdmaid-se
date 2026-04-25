package eu.darken.sdmse.common.previews

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.lookup
import eu.darken.sdmse.common.uix.ViewModel4
import javax.inject.Inject

@HiltViewModel
class PreviewViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val gatewaySwitch: GatewaySwitch,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    suspend fun resolveLookup(path: APath): APathLookup<*>? = try {
        path.lookup(gatewaySwitch)
    } catch (e: Exception) {
        log(TAG, WARN) { "resolveLookup($path) failed: ${e.asLog()}" }
        errorEvents.tryEmit(e)
        null
    }

    companion object {
        private val TAG = logTag("Preview", "ViewModel")
    }
}
