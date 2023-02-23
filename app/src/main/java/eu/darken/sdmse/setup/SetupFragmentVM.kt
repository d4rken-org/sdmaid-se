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
import eu.darken.sdmse.setup.accessibility.AccessibilitySetupCardVH
import eu.darken.sdmse.setup.accessibility.AccessibilitySetupModule
import eu.darken.sdmse.setup.root.RootSetupCardVH
import eu.darken.sdmse.setup.root.RootSetupModule
import eu.darken.sdmse.setup.saf.SAFSetupCardVH
import eu.darken.sdmse.setup.saf.SAFSetupModule
import eu.darken.sdmse.setup.storage.StorageSetupCardVH
import eu.darken.sdmse.setup.storage.StorageSetupModule
import eu.darken.sdmse.setup.usagestats.UsageStatsSetupCardVH
import eu.darken.sdmse.setup.usagestats.UsageStatsSetupModule
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class SetupFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val setupManager: SetupManager,
    private val safSetupModule: SAFSetupModule,
    private val accessibilitySetupModule: AccessibilitySetupModule,
    private val webpageTool: WebpageTool,
    private val rootSetupModule: RootSetupModule,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val navArgs by handle.navArgs<SetupFragmentArgs>()

    init {
        setupManager.setDismissed(false)
    }

    val isOnboarding = navArgs.isOnboarding

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
                        is RootSetupModule.State -> RootSetupCardVH.Item(
                            setupState = state,
                            onToggleUseRoot = {
                                launch {
                                    rootSetupModule.toggleUseRoot(it)
                                    rootSetupModule.refresh()
                                }
                            },
                            onHelp = {
                                webpageTool.open("https://github.com/d4rken/sdmaid-se/wiki/Setup#root-access")
                            },
                        )
                        is UsageStatsSetupModule.State -> UsageStatsSetupCardVH.Item(
                            setupState = state,
                            onGrantAction = {
                                state.missingPermission.firstOrNull()?.let {
                                    events.postValue(SetupEvents.RuntimePermissionRequests(it))
                                }
                            },
                            onHelp = {
                                webpageTool.open("https://github.com/d4rken/sdmaid-se/wiki/Setup#usage-statistics")
                            }
                        )
                        is AccessibilitySetupModule.State -> AccessibilitySetupCardVH.Item(
                            setupState = state,
                            onGrantAction = {
                                events.postValue(SetupEvents.ConfigureAccessibilityService(state))
                            },
                            onHelp = {
                                webpageTool.open("https://github.com/d4rken/sdmaid-se/wiki/Setup#accessibility-service")
                            }
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
        if (granted) setupManager.refresh()
    }

    fun onAccessibilityReturn() = launch {
        log(TAG) { "onAccessibilityReturn" }
        accessibilitySetupModule.refresh()
    }

    companion object {
        private val DISPLAY_ORDER = listOf(
            StorageSetupCardVH.Item::class,
            SAFSetupCardVH.Item::class,
            UsageStatsSetupCardVH.Item::class,
            AccessibilitySetupCardVH.Item::class,
            RootSetupCardVH.Item::class,
        )
        private val TAG = logTag("Setup", "Fragment", "VM")
    }
}