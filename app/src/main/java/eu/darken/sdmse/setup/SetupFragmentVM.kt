package eu.darken.sdmse.setup

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.setup.saf.SAFSetupCardVH
import eu.darken.sdmse.setup.saf.SAFSetupModule
import eu.darken.sdmse.setup.storage.StorageSetupCardVH
import eu.darken.sdmse.setup.storage.StorageSetupModule
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class SetupFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val setupManager: SetupManager,
    private val safSetupModule: SAFSetupModule,
    private val storageSetupModule: StorageSetupModule,
    private val webpageTool: WebpageTool,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val navArgs by handle.navArgs<SetupFragmentArgs>()

    val events = SingleLiveEvent<SetupEvents>()

    val listItems: LiveData<List<SetupAdapter.Item>> = setupManager.state
        .map { setupState ->
            val items = mutableListOf<SetupAdapter.Item>()

            setupState.moduleStates
                .filter { !it.isComplete || navArgs.showCompleted }
                .map { state ->
                    when (state) {
                        is SAFSetupModule.State -> SAFSetupCardVH.Item(
                            setupState = state,
                            onPathClicked = {
                                if (!it.hasAccess) {
                                    events.postValue(SetupEvents.SafRequestAccess(it))
                                }
                            },
                            onHelp = {
                                webpageTool.open("https://github.com/d4rken/sdmaid-se/wiki/Setup#storage-access-framework")
                            },
                        )
                        is StorageSetupModule.State -> StorageSetupCardVH.Item(
                            setupState = state,
                            onPathClicked = {
                                state.missingPermission.firstOrNull()?.let {
                                    events.postValue(SetupEvents.RuntimePermissionRequests(it))
                                }
                            },
                            onHelp = {
                                webpageTool.open("https://github.com/d4rken/sdmaid-se/wiki/Setup#manage-storage")
                            },
                        )
                        else -> throw IllegalArgumentException("Unknown state: $state")
                    }
                }
                .sortedBy { item -> DISPLAY_ORDER.indexOfFirst { it.isInstance(item) } }
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
        } catch (e: IllegalArgumentException) {
            events.postValue(SetupEvents.SafWrongPathError(e))
        }
    }

    fun onRuntimePermissionsGranted(result: Permission?, granted: Boolean) = launch {
        log(TAG) { "onRuntimePermissionGranted(result=$result,granted=$granted)" }
        if (granted) storageSetupModule.refresh()
    }

    companion object {
        private val DISPLAY_ORDER = listOf(
            StorageSetupCardVH.Item::class,
            SAFSetupCardVH.Item::class,
        )
        private val TAG = logTag("Setup", "Fragment", "VM")
    }
}