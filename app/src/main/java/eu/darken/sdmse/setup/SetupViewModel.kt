package eu.darken.sdmse.setup

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.pkgs.getLaunchIntent
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.setup.automation.AutomationSetupCardVH
import eu.darken.sdmse.setup.automation.AutomationSetupModule
import eu.darken.sdmse.setup.inventory.InventorySetupCardVH
import eu.darken.sdmse.setup.inventory.InventorySetupModule
import eu.darken.sdmse.setup.notification.NotificationSetupCardVH
import eu.darken.sdmse.setup.notification.NotificationSetupModule
import eu.darken.sdmse.setup.root.RootSetupCardVH
import eu.darken.sdmse.setup.root.RootSetupModule
import eu.darken.sdmse.setup.saf.SAFSetupCardVH
import eu.darken.sdmse.setup.saf.SAFSetupModule
import eu.darken.sdmse.setup.shizuku.ShizukuSetupCardVH
import eu.darken.sdmse.setup.shizuku.ShizukuSetupModule
import eu.darken.sdmse.setup.storage.StorageSetupCardVH
import eu.darken.sdmse.setup.storage.StorageSetupModule
import eu.darken.sdmse.setup.usagestats.UsageStatsSetupCardVH
import eu.darken.sdmse.setup.usagestats.UsageStatsSetupModule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @Suppress("StaticFieldLeak") @ApplicationContext private val context: Context,
    private val setupManager: SetupManager,
    private val storageSetupModule: StorageSetupModule,
    private val safSetupModule: SAFSetupModule,
    private val automationSetupModule: AutomationSetupModule,
    private val webpageTool: WebpageTool,
    private val rootSetupModule: RootSetupModule,
    private val shizukuSetupModule: ShizukuSetupModule,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val navArgs by handle.navArgs<SetupFragmentArgs>()

    init {
        setupManager.setDismissed(false)
    }

    // TODO support filtering based on screenOptions.filterTypes
    val screenOptions = navArgs.options ?: SetupScreenOptions()

    val events = SingleLiveEvent<SetupEvents>()

    private val itemsStateFlow: StateFlow<List<SetupAdapter.Item>?> = setupManager.state
        .map { setupState ->
            val items = mutableListOf<SetupAdapter.Item>()

            setupState.moduleStates
                .filter { screenOptions.typeFilter == null || screenOptions.typeFilter.contains(it.type) }
                .filter { it !is SetupModule.State.Current || !it.isComplete || screenOptions.showCompleted }
                .mapNotNull { state ->
                    when (state.type) {
                        SetupModule.Type.SAF -> when (state) {
                            is SetupModule.State.Current -> SAFSetupCardVH.Item(
                                state = state as SAFSetupModule.Result,
                                onPathClicked = {
                                    if (!it.hasAccess) {
                                        events.postValue(SetupEvents.SafRequestAccess(it))
                                    }
                                },
                                onHelp = {
                                    webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#storage-access-framework")
                                },
                            ).takeIf { it.state.paths.isNotEmpty() }

                            is SetupModule.State.Loading -> SetupModuleLoadingCardVH.Item(state)
                        }

                        SetupModule.Type.STORAGE -> when (state) {
                            is SetupModule.State.Current -> StorageSetupCardVH.Item(
                                state = state as StorageSetupModule.Result,
                                onPathClicked = {
                                    state.missingPermission.firstOrNull()?.let {
                                        events.postValue(SetupEvents.RuntimePermissionRequests(it))
                                    }
                                },
                                onHelp = {
                                    webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#manage-storage")
                                },
                            )

                            is SetupModule.State.Loading -> SetupModuleLoadingCardVH.Item(state)
                        }

                        SetupModule.Type.ROOT -> when (state) {
                            is SetupModule.State.Current -> RootSetupCardVH.Item(
                                state = state as RootSetupModule.Result,
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

                            is SetupModule.State.Loading -> SetupModuleLoadingCardVH.Item(state)
                        }

                        SetupModule.Type.USAGE_STATS -> when (state) {
                            is SetupModule.State.Current -> UsageStatsSetupCardVH.Item(
                                state = state as UsageStatsSetupModule.Result,
                                onGrantAction = {
                                    state.missingPermission.firstOrNull()?.let {
                                        events.postValue(SetupEvents.RuntimePermissionRequests(it))
                                    }
                                },
                                onHelp = {
                                    webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#usage-statistics")
                                }
                            )

                            is SetupModule.State.Loading -> SetupModuleLoadingCardVH.Item(state)
                        }

                        SetupModule.Type.AUTOMATION -> when (state) {
                            is SetupModule.State.Current -> AutomationSetupCardVH.Item(
                                state = state as AutomationSetupModule.Result,
                                onGrantAction = {
                                    launch {
                                        automationSetupModule.setAllow(true)
                                        automationSetupModule.refresh()
                                        if (!state.canSelfEnable) {
                                            events.postValue(SetupEvents.ConfigureAccessibilityService(state))
                                        }
                                    }
                                },
                                onDismiss = {
                                    launch {
                                        automationSetupModule.setAllow(false)
                                        automationSetupModule.refresh()
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

                            is SetupModule.State.Loading -> SetupModuleLoadingCardVH.Item(state)
                        }

                        SetupModule.Type.NOTIFICATION -> when (state) {
                            is SetupModule.State.Current -> NotificationSetupCardVH.Item(
                                state = state as NotificationSetupModule.Result,
                                onGrantAction = {
                                    state.missingPermission.firstOrNull()?.let {
                                        events.postValue(SetupEvents.RuntimePermissionRequests(it))
                                    }
                                },
                                onHelp = {
                                    webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#notifications")
                                }
                            )

                            is SetupModule.State.Loading -> SetupModuleLoadingCardVH.Item(state)
                        }

                        SetupModule.Type.SHIZUKU -> when (state) {
                            is SetupModule.State.Current -> ShizukuSetupCardVH.Item(
                                state = state as ShizukuSetupModule.Result,
                                onToggleUseShizuku = {
                                    launch { shizukuSetupModule.toggleUseShizuku(it) }
                                },
                                onHelp = {
                                    webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#shizuku")
                                },
                                onOpen = {
                                    state.pkg.getLaunchIntent(context)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let {
                                        try {
                                            context.startActivity(it)
                                        } catch (e: ActivityNotFoundException) {
                                            errorEvents.postValue(e)
                                        }
                                    }
                                }
                            )

                            is SetupModule.State.Loading -> SetupModuleLoadingCardVH.Item(state)
                        }

                        SetupModule.Type.INVENTORY -> when (state) {
                            is SetupModule.State.Current -> InventorySetupCardVH.Item(
                                state = state as InventorySetupModule.Result,
                                onGrantAction = {
                                    events.postValue(SetupEvents.ShowOurDetailsPage(state.settingsIntent))
                                },
                                onHelp = {
                                    webpageTool.open(InventorySetupModule.INFO_URL)
                                }
                            )

                            is SetupModule.State.Loading -> SetupModuleLoadingCardVH.Item(state)
                        }
                    }
                }
                .sortedBy { item ->
                    if (screenOptions.showCompleted && !item.state.isComplete) {
                        Int.MIN_VALUE
                    } else if (item is RootSetupCardVH.Item && item.state.isInstalled && item.state.useRoot == null) {
                        Int.MIN_VALUE
                    } else {
                        DISPLAY_ORDER.indexOfFirst { it == item.state.type }
                    }
                }
                .run { items.addAll(this) }

            items
        }
        .setupCommonEventHandlers(TAG) { "listItems" }
        .stateIn(vmScope, SharingStarted.Eagerly, null)

    val listItems: LiveData<List<SetupAdapter.Item>> = itemsStateFlow
        .filterNotNull()
        .asLiveData2()

    val isSetupComplete: LiveData<Boolean> = itemsStateFlow
        .map { items -> items != null && items.isEmpty() && !screenOptions.showCompleted }
        .distinctUntilChanged()
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

                Permission.PACKAGE_USAGE_STATS -> {}
                Permission.POST_NOTIFICATIONS -> {}
                Permission.WRITE_SECURE_SETTINGS -> {}
                Permission.QUERY_ALL_PACKAGES -> {}
                null -> {}
            }
            setupManager.refresh()
        }
    }

    fun onAccessibilityReturn() = launch {
        log(TAG) { "onAccessibilityReturn" }
        automationSetupModule.refresh()
    }

    fun navback() {
        if (screenOptions.isOnboarding) {
            SetupFragmentDirections.actionSetupFragmentToDashboardFragment().navigate()
        } else {
            popNavStack()
        }
    }

    companion object {
        private val DISPLAY_ORDER = listOf(
            SetupModule.Type.INVENTORY,
            SetupModule.Type.NOTIFICATION,
            SetupModule.Type.STORAGE,
            SetupModule.Type.SAF,
            SetupModule.Type.SHIZUKU,
            SetupModule.Type.ROOT,
            SetupModule.Type.USAGE_STATS,
            SetupModule.Type.AUTOMATION,
        )
        private val TAG = logTag("Setup", "ViewModel")
    }
}