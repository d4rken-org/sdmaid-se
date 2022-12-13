package eu.darken.sdmse.setup

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.setup.saf.SAFSetupCardVH
import eu.darken.sdmse.setup.saf.SAFSetupModule
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class SetupFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val setupManager: SetupManager,
    private val safSetupModule: SAFSetupModule,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    val events = SingleLiveEvent<SetupEvents>()

    val listItems: LiveData<List<SetupAdapter.Item>> = setupManager.state
        .map { setupState ->
            val items = mutableListOf<SetupAdapter.Item>()

            setupState.moduleStates
                .filter { !it.isComplete }
                .map { state ->
                    when (state) {
                        is SAFSetupModule.State -> SAFSetupCardVH.Item(
                            setupState = state,
                            onPathClicked = { events.postValue(SetupEvents.SafRequestAccess(it)) },
                            onHelp = {
                                TODO()
                            },
                        )
                        else -> throw IllegalArgumentException("Unknown state: $state")
                    }
                }
                .run { items.addAll(this) }


            items
        }
        .onEach { if (it.isEmpty()) popNavStack() }
        .setupCommonEventHandlers(TAG) { "listItems" }
        .asLiveData2()

    fun onSafAccessGranted(uri: Uri?) = launch {
        log(TAG) { "onSafAccessGranted(uri=$uri)" }
        if (uri == null) return@launch
        try {
            safSetupModule.takePermission(uri)
            setupManager.refresh()
        } catch (e: IllegalArgumentException) {
            events.postValue(SetupEvents.SafWrongPathError(e))
        }
    }

    companion object {
        private val TAG = logTag("Setup", "Fragment", "VM")
    }
}