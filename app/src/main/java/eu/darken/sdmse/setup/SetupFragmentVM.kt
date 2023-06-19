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
import eu.darken.sdmse.setup.notification.NotificationSetupCardVH
import eu.darken.sdmse.setup.notification.NotificationSetupModule
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
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val setupManager: SetupManager,
    private val storageSetupModule: StorageSetupModule,
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
                .mapNotNull { state ->
                    when (state) {
                        is SAFSetupModule.State -> SAFSetupCardVH.Item(
                            state = state,
                            onPathClicked = {
                                if (!it.hasAccess) {
                                    events.postValue(SetupEvents.SafRequestAccess(it))
                                }
                            },
                            onHelp = {
                                webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#storage-access-framework")
                            },
                        ).takeIf { it.state.paths.isNotEmpty() }
                        is StorageSetupModule.State -> StorageSetupCardVH.Item(
                            state = state,
                            onPathClicked = {
                                state.missingPermission.firstOrNull()?.let {
                                    events.postValue(SetupEvents.RuntimePermissionRequests(it))
                                }
                            },
                            onHelp = {
                                webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#manage-storage")
                            },
                        )
                        is RootSetupModule.State -> RootSetupCardVH.Item(
                            state = state,
                            onToggleUseRoot = {
                                launch {
                                    rootSetupModule.toggleUseRoot(it)
                                    rootSetupModule.refresh()
                                }
                            },
                            onHelp = {
                                webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#root-access")
                            },
                        )
                        is UsageStatsSetupModule.State -> UsageStatsSetupCardVH.Item(
                            state = state,
                            onGrantAction = {
                                state.missingPermission.firstOrNull()?.let {
                                    events.postValue(SetupEvents.RuntimePermissionRequests(it))
                                }
                            },
                            onHelp = {
                                webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#usage-statistics")
                            }
                        )
                        is AccessibilitySetupModule.State -> AccessibilitySetupCardVH.Item(
                            state = state,
                            onGrantAction = {
                                launch {
                                    accessibilitySetupModule.setAllow(true)
                                    accessibilitySetupModule.refresh()
                                    events.postValue(SetupEvents.ConfigureAccessibilityService(state))
                                }
                            },
                            onDismiss = {
                                launch {
                                    accessibilitySetupModule.setAllow(false)
                                    accessibilitySetupModule.refresh()
                                }
                            },
                            onHelp = {
                                webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#accessibility-service")
                            },
                            onRestrictionsShow = {
                                events.postValue(SetupEvents.ShowOurDetailsPage(state.liftRestrictionsIntent))
                            },
                            onRestrictionsHelp = {
                                webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#acs-appops-restrictions")
                            },
                        )
                        is NotificationSetupModule.State -> NotificationSetupCardVH.Item(
                            state = state,
                            onGrantAction = {
                                state.missingPermission.firstOrNull()?.let {
                                    events.postValue(SetupEvents.RuntimePermissionRequests(it))
                                }
                            },
                            onHelp = {
                                webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#notifications")
                            }
                        )
                        else -> throw IllegalArgumentException("Unknown state: $state")
                    }
                }
                .sortedBy { item ->
                    if (navArgs.showCompleted && !item.state.isComplete) {
                        Int.MIN_VALUE
                    } else {
                        DISPLAY_ORDER.indexOfFirst { it.isInstance(item) }
                    }
                }
                .run { items.addAll(this) }

            items
        }
        .onEach {
            if (it.isEmpty() && !navArgs.showCompleted) {
                navback()
            }
        }
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

    fun onRuntimePermissionsGranted(permission: Permission?, granted: Boolean) = launch {
        log(TAG) { "onRuntimePermissionGranted(result=$permission,granted=$granted)" }
        if (granted) {
            when (permission) {
                Permission.MANAGE_EXTERNAL_STORAGE,
                Permission.READ_EXTERNAL_STORAGE,
                Permission.WRITE_EXTERNAL_STORAGE -> {
                    storageSetupModule.onPermissionChanged(permission, granted)
                }
                Permission.IGNORE_BATTERY_OPTIMIZATION -> {}
                Permission.PACKAGE_USAGE_STATS -> {}
                Permission.POST_NOTIFICATIONS -> {}
                Permission.WRITE_SECURE_SETTINGS -> {}
                null -> {}
            }
            setupManager.refresh()
        }
    }

    fun onAccessibilityReturn() = launch {
        log(TAG) { "onAccessibilityReturn" }
        accessibilitySetupModule.refresh()
    }

    fun navback() {
        if (navArgs.isOnboarding) {
            SetupFragmentDirections.actionSetupFragmentToDashboardFragment().navigate()
        } else {
            popNavStack()
        }
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